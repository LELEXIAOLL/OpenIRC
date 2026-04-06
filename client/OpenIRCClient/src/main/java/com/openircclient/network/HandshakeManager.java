package com.openircclient.network;

import com.openircclient.utils.KeyExchangeUtils;
import javax.crypto.spec.SecretKeySpec;
import java.security.KeyPair;
import java.util.Base64;

public class HandshakeManager {
    private final KeyPair keyPair;
    private SecretKeySpec sessionKey;
    private boolean completed = false;

    public HandshakeManager() throws Exception {
        this.keyPair = KeyExchangeUtils.generateKeyPair();
    }

    public String getPublicKeyBase64() {
        return java.util.Base64.getEncoder().encodeToString(KeyExchangeUtils.getRawPublicKeyBytes(keyPair));
    }

    public void completeHandshake(String serverPublicKeyBase64) throws Exception {
        byte[] serverKeyBytes = Base64.getDecoder().decode(serverPublicKeyBase64);
        this.sessionKey = KeyExchangeUtils.deriveSharedSecret(keyPair.getPrivate(), serverKeyBytes);
        this.completed = true;
    }

    public SecretKeySpec getSessionKey() {
        if (!completed) return null;
        return sessionKey;
    }

    public boolean isCompleted() {
        return completed;
    }
}