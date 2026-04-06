use serde::{Deserialize, Serialize};
use std::{fs, path::Path};
use crate::logger::Logger;

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct User {
    pub username: String,
    pub password: String,
    pub hwid: String,
    pub group: String,
    pub tag: String,
    pub expiredtime: String,
    pub ban: bool,
    pub mute: bool,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct Key {
    pub key: String,
    pub time: String,
    pub used: String,
}

#[derive(Serialize, Deserialize, Debug, Clone, Default)]
pub struct HwidBans {
    pub banned_hwids: Vec<String>,
}

pub struct DataStore;

impl DataStore {
    pub fn init_files() {
        Self::ensure_file("users.json", "[]");
        Self::ensure_file("keys.json", "[]");
        Self::ensure_file("hwidbans.json", "{\"banned_hwids\": []}");
    }

    fn ensure_file(name: &str, content: &str) {
        if !Path::new(name).exists() {
            fs::write(name, content).ok();
            Logger::info(&format!("已初始化数据文件: {}", name));
        }
    }

    pub fn load_users() -> Vec<User> {
        let content = fs::read_to_string("users.json").unwrap_or_else(|_| "[]".into());
        serde_json::from_str(&content).unwrap_or_default()
    }

    pub fn save_users(users: &Vec<User>) {
        let content = serde_json::to_string_pretty(users).unwrap();
        fs::write("users.json", content).ok();
    }

    pub fn load_keys() -> Vec<Key> {
        let content = fs::read_to_string("keys.json").unwrap_or_else(|_| "[]".into());
        serde_json::from_str(&content).unwrap_or_default()
    }

    pub fn save_keys(keys: &Vec<Key>) {
        let content = serde_json::to_string_pretty(keys).unwrap();
        fs::write("keys.json", content).ok();
    }

    pub fn load_hwid_bans() -> HwidBans {
        let content = fs::read_to_string("hwidbans.json").unwrap_or_else(|_| "{\"banned_hwids\":[]}".into());
        serde_json::from_str(&content).unwrap_or_default()
    }
}