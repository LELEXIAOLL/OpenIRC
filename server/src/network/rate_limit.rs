use std::{collections::HashMap, net::IpAddr, sync::Mutex, time::{Duration, Instant}};

pub struct RateLimiter {
    records: Mutex<HashMap<IpAddr, Instant>>,
}

impl RateLimiter {
    pub fn new() -> Self { Self { records: Mutex::new(HashMap::new()) } }
    pub fn check(&self, ip: IpAddr) -> bool {
        let mut map = self.records.lock().unwrap();
        let now = Instant::now();
        if let Some(last) = map.get(&ip) {
            if now.duration_since(*last) < Duration::from_secs(3) { return false; }
        }
        map.insert(ip, now);
        true
    }
}