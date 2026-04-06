mod logger; mod config; mod storage; mod network; mod logic;

use tokio::net::TcpListener;
use tokio_tungstenite::accept_async;
use futures_util::{StreamExt, SinkExt};
use tokio_tungstenite::tungstenite::Message;
use std::sync::{Arc, Mutex};
use std::collections::HashMap;
use tokio::sync::mpsc;
use crate::network::{rate_limit::RateLimiter, crypto::CryptoProvider};
use crate::logger::Logger;

#[tokio::main]
async fn main() {
    storage::DataStore::init_files();
    let limiter = Arc::new(RateLimiter::new());
    let online_users: logic::commands::OnlineMap = Arc::new(Mutex::new(HashMap::new()));
    let shell_map = online_users.clone();
    tokio::spawn(logic::commands::handle_shell(shell_map));

    let listener = TcpListener::bind(config::SERVER_PORT).await.unwrap();
    Logger::info(&format!("服务端已启动: {}", config::SERVER_PORT));

    while let Ok((stream, addr)) = listener.accept().await {
        let lim = limiter.clone();
        let map_clone = online_users.clone();
        tokio::spawn(async move {
            if let Ok(ws) = accept_async(stream).await {
                let (mut ws_write, mut ws_read) = ws.split();
                let crypto = CryptoProvider::new();
                let server_pub_b64 = crypto.public_key_base64.clone();
                let (tx, mut rx) = mpsc::unbounded_channel::<Message>();

                if let Some(Ok(Message::Text(txt))) = ws_read.next().await {
                    let json: serde_json::Value = serde_json::from_str(&txt).unwrap_or_default();
                    if json["type"] == "HANDSHAKE_INIT" {
                        if let Ok(cipher) = crypto.derive_aes_key(json["key"].as_str().unwrap_or("")) {
                            ws_write.send(Message::Text(serde_json::json!({"type":"HANDSHAKE_RESP","key":server_pub_b64}).to_string())).await.ok();
                            let cipher_r = cipher.clone();
                            let cipher_w = cipher.clone();
                            let current_user = Arc::new(Mutex::new(String::new()));
                            let user_ref = current_user.clone();

                            tokio::spawn(async move {
                                while let Some(msg) = rx.recv().await { if ws_write.send(msg).await.is_err() { break; } }
                            });

                            while let Some(Ok(Message::Text(m))) = ws_read.next().await {
                                if !lim.check(addr.ip()) {
                                    let e = serde_json::json!({"status":"error","message":"请求频率过快"}).to_string();
                                    tx.send(Message::Text(CryptoProvider::encrypt(&cipher_w, &e))).ok();
                                    continue;
                                }
                                if let Ok(dec) = CryptoProvider::decrypt(&cipher_r, &m) {
                                    let req_v: serde_json::Value = serde_json::from_str(&dec).unwrap_or_default();
                                    if req_v["r_type"] == "HEARTBEAT" { continue; }

                                    if req_v["r_type"] == "SYNC_MC_NAME" {
                                        let mc_name = req_v["mc_name"].as_str().unwrap_or("Unknown").to_string();
                                        let irc_name = user_ref.lock().unwrap().clone();
                                        if !irc_name.is_empty() {
                                            let map = map_clone.lock().unwrap();
                                            if let Some(s) = map.get(&irc_name) {
                                                *s.mc_name.lock().unwrap() = mc_name;
                                                let mut mapping = HashMap::new();
                                                for (u, ses) in map.iter() { mapping.insert(u.clone(), ses.mc_name.lock().unwrap().clone()); }
                                                let list_pkt = serde_json::json!({"r_type":"MC_NAME_LIST","mapping":mapping}).to_string();
                                                for ses in map.values() { ses.tx.send(Message::Text(CryptoProvider::encrypt(&ses.cipher, &list_pkt))).ok(); }
                                            }
                                        }
                                        continue;
                                    }

                                    if req_v["r_type"] == "CHAT" {
                                        let uname = user_ref.lock().unwrap().clone();
                                        if !uname.is_empty() {
                                            let users = storage::database::DataStore::load_users();
                                            if let Some(u) = users.iter().find(|u| u.username == uname) {
                                                if u.mute {
                                                    let e = serde_json::json!({"status":"error","message":"§c您已被禁言"}).to_string();
                                                    tx.send(Message::Text(CryptoProvider::encrypt(&cipher_w, &e))).ok();
                                                    continue;
                                                }
                                                let content = req_v["content"].as_str().unwrap_or("");
                                                let b = serde_json::json!({"r_type":"IRC_SAY","sender":uname,"content":content,"group":u.group,"tag":u.tag}).to_string();
                                                let map = map_clone.lock().unwrap();
                                                for s in map.values() { s.tx.send(Message::Text(CryptoProvider::encrypt(&s.cipher, &b))).ok(); }
                                                Logger::info(&format!("[IRC聊天] {}: {}", uname, content));
                                            }
                                        }
                                        continue;
                                    }

                                    if let Ok(req) = serde_json::from_str::<network::packets::AuthRequest>(&dec) {
                                        let resp = match req.r_type.as_str() {
                                            "REGISTER" => logic::auth::handle_register(req.clone()),
                                            "RENEW" => logic::auth::handle_renew(req.clone()),
                                            _ => logic::auth::handle_login(req.clone()),
                                        };
                                        // 核心修复点：允许 REGISTER 成功后也绑定 Session
                                        if resp.status == "success" && (req.r_type == "LOGIN" || req.r_type == "REGISTER") {
                                            let n = req.username.unwrap();
                                            *user_ref.lock().unwrap() = n.clone();
                                            map_clone.lock().unwrap().insert(n, Arc::new(logic::commands::Session {
                                                tx: tx.clone(),
                                                cipher: cipher_w.clone(),
                                                mc_name: Arc::new(Mutex::new("Unknown".into()))
                                            }));
                                        }
                                        tx.send(Message::Text(CryptoProvider::encrypt(&cipher_w, &serde_json::to_string(&resp).unwrap()))).ok();
                                    }
                                }
                            }
                            let n = user_ref.lock().unwrap();
                            if !n.is_empty() { map_clone.lock().unwrap().remove(&*n); Logger::info(&format!("用户 {} 已断开", n)); }
                        }
                    }
                }
            }
        });
    }
}