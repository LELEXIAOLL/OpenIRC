use crate::storage::database::{DataStore, User};
use crate::network::packets::{AuthRequest, AuthResponse};
use crate::logger::Logger;
use crate::network::crypto::CryptoProvider;
use chrono::{Local, Duration, NaiveDateTime};

pub fn handle_login(req: AuthRequest) -> AuthResponse {
    let users = DataStore::load_users();
    let username = req.username.unwrap_or_default();
    let password = req.password.unwrap_or_default();
    let hwid = req.hwid.unwrap_or_default();

    if let Some(u) = users.iter().find(|u| u.username == username) {
        if u.password != password {
            Logger::auth(&format!("用户 {} 登录失败: 密码不匹配", username));
            return error_resp("密码错误");
        }
        if u.hwid != hwid {
            Logger::auth(&format!("用户 {} 登录失败: 机器码不匹配", username));
            return error_resp("机器码不匹配");
        }
        if u.ban {
            return error_resp("账号已被封禁");
        }

        let now = Local::now().naive_local();
        if let Ok(exp) = NaiveDateTime::parse_from_str(&u.expiredtime, "%Y-%m-%d %H:%M") {
            if now > exp { return error_resp("订阅已到期"); }
        }

        Logger::auth(&format!("用户 {} 登录成功", username));
        AuthResponse {
            status: "success".into(),
            message: "登录成功".into(),
            username: Some(u.username.clone()),
            group: Some(u.group.clone()),
            tag: Some(u.tag.clone()),
            expiredtime: Some(u.expiredtime.clone()),
        }
    } else {
        error_resp("用户不存在")
    }
}

pub fn handle_register(req: AuthRequest) -> AuthResponse {
    let username = req.username.unwrap_or_default();
    let client_password = req.password.unwrap_or_default();
    let client_hwid = req.hwid.unwrap_or_default();

    if username.len() > 16 || !username.chars().all(|c| c.is_alphanumeric()) {
        return error_resp("用户名不合法");
    }

    let mut users = DataStore::load_users();
    if users.iter().any(|u| u.username == username) {
        return error_resp("用户已存在");
    }

    let bans = DataStore::load_hwid_bans();
    if bans.banned_hwids.contains(&client_hwid) {
        return error_resp("HWID已被封禁");
    }

    let mut keys = DataStore::load_keys();
    let key_val = req.key.unwrap_or_default();
    if let Some(idx) = keys.iter().position(|k| k.key == key_val && k.used == "false") {
        let days = keys[idx].time.parse::<i64>().unwrap_or(0);
        let exp = (Local::now() + Duration::days(days)).format("%Y-%m-%d %H:%M").to_string();

        let new_user = User {
            username: username.clone(),
            password: client_password,
            hwid: client_hwid,
            group: "user".into(),
            tag: "".into(),
            expiredtime: exp.clone(),
            ban: false,
            mute: false,
        };

        keys[idx].used = "true".into();
        users.push(new_user);
        DataStore::save_users(&users);
        DataStore::save_keys(&keys);

        Logger::auth(&format!("新用户注册成功: {}", username));
        AuthResponse {
            status: "success".into(),
            message: "注册成功".into(),
            username: Some(username),
            group: Some("user".into()),
            tag: Some("".into()),
            expiredtime: Some(exp),
        }
    } else {
        error_resp("卡密无效或已使用")
    }
}

pub fn handle_renew(req: AuthRequest) -> AuthResponse {
    let username = req.username.unwrap_or_default();
    let key_val = req.key.unwrap_or_default();
    let mut users = DataStore::load_users();

    let user_idx = users.iter().position(|u| u.username == username);
    if user_idx.is_none() { return error_resp("用户不存在"); }
    let idx = user_idx.unwrap();

    let mut keys = DataStore::load_keys();
    if let Some(k_idx) = keys.iter().position(|k| k.key == key_val && k.used == "false") {
        let days = keys[k_idx].time.parse::<i64>().unwrap_or(0);
        let now = Local::now().naive_local();
        let current_exp_str = users[idx].expiredtime.clone();
        let current_exp = NaiveDateTime::parse_from_str(&current_exp_str, "%Y-%m-%d %H:%M").unwrap_or(now);
        let base_time = if current_exp > now { current_exp } else { now };
        let new_exp = (base_time + Duration::days(days)).format("%Y-%m-%d %H:%M").to_string();

        users[idx].expiredtime = new_exp.clone();
        keys[k_idx].used = "true".into();

        let resp_data = (users[idx].group.clone(), users[idx].tag.clone());
        DataStore::save_users(&users);
        DataStore::save_keys(&keys);

        Logger::auth(&format!("用户 {} 续费成功", username));
        AuthResponse {
            status: "success".into(),
            message: "续费成功".into(),
            username: Some(username),
            group: Some(resp_data.0),
            tag: Some(resp_data.1),
            expiredtime: Some(new_exp),
        }
    } else {
        error_resp("卡密无效或已使用")
    }
}

fn error_resp(msg: &str) -> AuthResponse {
    AuthResponse { status: "error".into(), message: msg.into(), username: None, group: None, tag: None, expiredtime: None }
}