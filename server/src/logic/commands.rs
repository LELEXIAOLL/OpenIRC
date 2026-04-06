use tokio::io::{self, AsyncBufReadExt, BufReader};
use crate::logger::Logger;
use crate::storage::database::{DataStore, Key};
use crate::network::crypto::CryptoProvider;
use crate::network::packets::{CriticalAction, IrcMessage};
use tokio_tungstenite::tungstenite::Message;
use tokio::sync::mpsc::UnboundedSender;
use std::collections::HashMap;
use std::sync::{Arc, Mutex};
use rand::{distributions::Alphanumeric, Rng};
use aes_gcm::Aes256Gcm;

pub struct Session {
    pub tx: UnboundedSender<Message>,
    pub cipher: Aes256Gcm,
    pub mc_name: Arc<Mutex<String>>,
}

pub type OnlineMap = Arc<Mutex<HashMap<String, Arc<Session>>>>;

pub async fn handle_shell(online_users: OnlineMap) {
    let mut reader = BufReader::new(io::stdin()).lines();
    while let Ok(Some(line)) = reader.next_line().await {
        let args: Vec<&str> = line.split_whitespace().collect();
        if args.is_empty() { continue; }
        match args[0] {
            "/help" => println!("--- OpenIRC Server 命令列表 ---
/help - 显示此帮助
/mute <用户名> <true/false> - 禁言用户
/ban <用户名> - 封禁用户账号
/unban <用户名> - 解封用户账号
/hwidban <用户名> - 封禁账号及对应机器码
/unbanhwid <用户名> - 解封账号及对应机器码
/list - 查看当前在线用户
/keys create <数量> <天数> - 批量生成卡密
/keys delete <卡密> - 删除指定卡密
/keys used <卡密> <true/false> - 修改卡密状态
/keys list - 查看所有卡密信息
/kick <用户名> - 断开用户连接
/crash <用户名> - 强制客户端自杀并断连
/say <消息> - 全服广播消息
/change password <用户名> <新密码> - 修改用户密码
/change hwid <用户名> <新机器码> - 修改用户机器码
/change group <用户名> <组名> - 修改用户组
/change tag <用户名> \"<称号>\" - 修改用户称号(支持空格)
/change delete <用户名> - 删除指定用户
/stop - 安全停止服务端"),

            "/mute" if args.len() == 3 => {
                let mut users = DataStore::load_users();
                let status = args[2].parse().unwrap_or(false);
                if let Some(u) = users.iter_mut().find(|u| u.username == args[1]) { u.mute = status; }
                DataStore::save_users(&users);
                Logger::info(&format!("已更改用户 {} 禁言状态", args[1]));
            },

            "/ban" if args.len() == 2 => {
                let mut users = DataStore::load_users();
                if let Some(u) = users.iter_mut().find(|u| u.username == args[1]) { u.ban = true; }
                DataStore::save_users(&users);
                Logger::info(&format!("已封禁账号: {}", args[1]));
            },

            "/unban" if args.len() == 2 => {
                let mut users = DataStore::load_users();
                if let Some(u) = users.iter_mut().find(|u| u.username == args[1]) { u.ban = false; }
                DataStore::save_users(&users);
                Logger::info(&format!("已解封账号: {}", args[1]));
            },

            "/hwidban" if args.len() == 2 => {
                let mut users = DataStore::load_users();
                let mut target_hwid = None;
                if let Some(u) = users.iter_mut().find(|u| u.username == args[1]) {
                    u.ban = true;
                    target_hwid = Some(u.hwid.clone());
                }
                if let Some(hwid) = target_hwid {
                    let mut bans = DataStore::load_hwid_bans();
                    if !bans.banned_hwids.contains(&hwid) {
                        bans.banned_hwids.push(hwid.clone());
                        std::fs::write("hwidbans.json", serde_json::to_string_pretty(&bans).unwrap()).ok();
                    }
                    DataStore::save_users(&users);
                    Logger::info(&format!("已封禁账号及机器码: {} | {}", args[1], hwid));
                }
            },

            "/unbanhwid" if args.len() == 2 => {
                let mut users = DataStore::load_users();
                let mut target_hwid = None;
                if let Some(u) = users.iter_mut().find(|u| u.username == args[1]) {
                    u.ban = false;
                    target_hwid = Some(u.hwid.clone());
                }
                if let Some(hwid) = target_hwid {
                    let mut bans = DataStore::load_hwid_bans();
                    bans.banned_hwids.retain(|h| h != &hwid);
                    std::fs::write("hwidbans.json", serde_json::to_string_pretty(&bans).unwrap()).ok();
                    DataStore::save_users(&users);
                    Logger::info(&format!("已解封账号及机器码: {} | {}", args[1], hwid));
                }
            },

            "/list" => {
                let map = online_users.lock().unwrap();
                println!("--- 在线用户 ({}) ---", map.len());
                for (u, s) in map.iter() { println!("- {} (MC: {})", u, s.mc_name.lock().unwrap()); }
            },

            "/keys" if args.len() >= 2 => match args[1] {
                "create" if args.len() == 4 => {
                    let count: usize = args[2].parse().unwrap_or(0);
                    let days = args[3];
                    let mut keys = DataStore::load_keys();
                    for _ in 0..count {
                        let k: String = rand::thread_rng().sample_iter(&Alphanumeric).take(16).map(char::from).collect();
                        keys.push(Key { key: k, time: days.into(), used: "false".into() });
                    }
                    DataStore::save_keys(&keys);
                    Logger::info(&format!("成功生成 {} 张 {} 天卡密", count, days));
                },
                "delete" if args.len() == 3 => {
                    let mut keys = DataStore::load_keys();
                    keys.retain(|k| k.key != args[2]);
                    DataStore::save_keys(&keys);
                    Logger::info("卡密已删除");
                },
                "used" if args.len() == 4 => {
                    let mut keys = DataStore::load_keys();
                    let status = args[3].to_string();
                    if let Some(k) = keys.iter_mut().find(|k| k.key == args[2]) { k.used = status; }
                    DataStore::save_keys(&keys);
                    Logger::info("卡密状态已更新");
                },
                "list" => {
                    let keys = DataStore::load_keys();
                    for k in keys { println!("卡密: {} | 时长: {}天 | 已使用: {}", k.key, k.time, k.used); }
                },
                _ => {}
            },

            "/kick" if args.len() == 2 => {
                let mut map = online_users.lock().unwrap();
                if let Some(session) = map.remove(args[1]) {
                    session.tx.send(Message::Close(None)).ok();
                    Logger::info(&format!("用户 {} 已踢出", args[1]));
                }
            },

            "/crash" if args.len() == 2 => {
                let mut map = online_users.lock().unwrap();
                if let Some(session) = map.remove(args[1]) {
                    let p = serde_json::to_string(&CriticalAction { r_type: "CRITICAL_ACTION".into(), action: "FORCE_EXIT".into() }).unwrap();
                    session.tx.send(Message::Text(CryptoProvider::encrypt(&session.cipher, &p))).ok();
                    session.tx.send(Message::Close(None)).ok();
                    Logger::info(&format!("已发送强退指令: {}", args[1]));
                }
            },

            "/say" if args.len() >= 2 => {
                let m = args[1..].join(" ");
                let p = serde_json::json!({"r_type":"IRC_SAY","sender":"§rConsole","content":m}).to_string();
                let map = online_users.lock().unwrap();
                for s in map.values() { s.tx.send(Message::Text(CryptoProvider::encrypt(&s.cipher, &p))).ok(); }
            },

            "/change" if args.len() >= 3 => match args[1] {
                "password" if args.len() == 4 => {
                    let mut users = DataStore::load_users();
                    let hashed = CryptoProvider::hash_sha256(args[3]);
                    if let Some(u) = users.iter_mut().find(|u| u.username == args[2]) { u.password = hashed; }
                    DataStore::save_users(&users);
                    Logger::info("密码更新成功");
                },
                "hwid" if args.len() == 4 => {
                    let mut users = DataStore::load_users();
                    if let Some(u) = users.iter_mut().find(|u| u.username == args[2]) { u.hwid = args[3].to_string(); }
                    DataStore::save_users(&users);
                    Logger::info("机器码更新成功");
                },
                "group" if args.len() == 4 => {
                    let mut users = DataStore::load_users();
                    if let Some(u) = users.iter_mut().find(|u| u.username == args[2]) { u.group = args[3].to_string(); }
                    DataStore::save_users(&users);
                    Logger::info("用户组更新成功");
                },
                "tag" if args.len() >= 4 => {
                    let mut users = DataStore::load_users();
                    let t = args[3..].join(" ").trim_matches('"').to_string();
                    if let Some(u) = users.iter_mut().find(|u| u.username == args[2]) { u.tag = t; }
                    DataStore::save_users(&users);
                    Logger::info("称号更新成功");
                },
                "delete" if args.len() == 3 => {
                    let mut users = DataStore::load_users();
                    users.retain(|u| u.username != args[2]);
                    DataStore::save_users(&users);
                    Logger::info("用户已删除");
                },
                _ => {}
            },

            "/stop" => std::process::exit(0),
            _ => println!("未知命令，输入 /help 查看帮助"),
        }
    }
}