use serde::{Deserialize, Serialize};

#[derive(Serialize, Deserialize, Clone)]
pub struct AuthRequest {
    pub r_type: String,
    pub username: Option<String>,
    pub password: Option<String>,
    pub hwid: Option<String>,
    pub key: Option<String>,
}

#[derive(Serialize, Deserialize, Clone)]
pub struct AuthResponse {
    pub status: String,
    pub message: String,
    pub username: Option<String>,
    pub group: Option<String>,
    pub tag: Option<String>,
    pub expiredtime: Option<String>,
}

#[derive(Serialize, Deserialize)]
pub struct CriticalAction {
    pub r_type: String,
    pub action: String,
}

#[derive(Serialize, Deserialize)]
pub struct IrcMessage {
    pub r_type: String,
    pub sender: String,
    pub content: String,
}

#[derive(Serialize, Deserialize, Clone)]
pub struct McNameSync {
    pub r_type: String, // "SYNC_MC_NAME"
    pub mc_name: String,
}

#[derive(Serialize, Deserialize, Clone)]
pub struct McNameList {
    pub r_type: String, // "MC_NAME_LIST"
    pub mapping: std::collections::HashMap<String, String>, // IRC_Name -> MC_Name
}