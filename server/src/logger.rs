use chrono::Local;
use colored::*;

pub struct Logger;

impl Logger {
    pub fn info(msg: &str) {
        Self::log("信息".green(), msg);
    }

    pub fn warn(msg: &str) {
        Self::log("警告".yellow(), msg);
    }

    pub fn error(msg: &str) {
        Self::log("错误".red(), msg);
    }

    pub fn auth(msg: &str) {
        Self::log("认证".blue(), msg);
    }

    fn log(level: ColoredString, msg: &str) {
        let now = Local::now().format("%Y-%m-%d %H:%M:%S").to_string().white();
        println!("[{}][{}] {}", now, level, msg);
    }
}