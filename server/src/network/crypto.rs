use x25519_dalek::{EphemeralSecret, PublicKey};
use sha2::{Digest, Sha256};
use aes_gcm::{Aes256Gcm, KeyInit, aead::Aead, Nonce, Key as AesKey};
use base64::{engine::general_purpose, Engine as _};
use rand::{rngs::OsRng, RngCore};

pub struct CryptoProvider {
    secret: EphemeralSecret,
    pub public_key_base64: String,
}

impl CryptoProvider {
    pub fn new() -> Self {
        let secret = EphemeralSecret::random_from_rng(OsRng);
        let public_key_base64 = general_purpose::STANDARD.encode(PublicKey::from(&secret).as_bytes());
        Self { secret, public_key_base64 }
    }

    pub fn hash_sha256(data: &str) -> String {
        let mut hasher = Sha256::new();
        hasher.update(data.as_bytes());
        format!("{:x}", hasher.finalize())
    }

    pub fn derive_aes_key(self, client_pub_b64: &str) -> Result<Aes256Gcm, String> {
        let bytes = general_purpose::STANDARD.decode(client_pub_b64).map_err(|_| "B64Err".to_string())?;
        let mut key = [0u8; 32];
        if bytes.len() != 32 { return Err("KeyLenErr".to_string()); }
        key.copy_from_slice(&bytes);
        let shared = self.secret.diffie_hellman(&PublicKey::from(key));
        let hash = Sha256::digest(shared.as_bytes());
        Ok(Aes256Gcm::new(AesKey::<Aes256Gcm>::from_slice(&hash)))
    }

    pub fn encrypt(cipher: &Aes256Gcm, data: &str) -> String {
        let mut iv = [0u8; 12]; OsRng.fill_bytes(&mut iv);
        let ciphertext = cipher.encrypt(Nonce::from_slice(&iv), data.as_bytes()).unwrap();
        let mut res = iv.to_vec(); res.extend(ciphertext);
        general_purpose::STANDARD.encode(res)
    }

    pub fn decrypt(cipher: &Aes256Gcm, b64: &str) -> Result<String, String> {
        let data = general_purpose::STANDARD.decode(b64).map_err(|_| "B64Err".to_string())?;
        if data.len() < 12 { return Err("DataLenErr".to_string()); }
        let (iv, ct) = data.split_at(12);
        let pt = cipher.decrypt(Nonce::from_slice(iv), ct).map_err(|_| "DecErr".to_string())?;
        String::from_utf8(pt).map_err(|_| "Utf8Err".to_string())
    }
}