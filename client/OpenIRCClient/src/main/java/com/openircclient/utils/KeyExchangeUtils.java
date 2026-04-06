package com.openircclient.utils;

import javax.crypto.KeyAgreement;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

public class KeyExchangeUtils {
    private static final String ALGORITHM = "XDH";

    public static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(ALGORITHM);
        return kpg.generateKeyPair();
    }

    public static byte[] getRawPublicKeyBytes(KeyPair keyPair) {
        byte[] x509Bytes = keyPair.getPublic().getEncoded();
        if (x509Bytes.length == 44) {
            return Arrays.copyOfRange(x509Bytes, 12, 44);
        }
        return x509Bytes;
    }

    public static SecretKeySpec deriveSharedSecret(PrivateKey privateKey, byte[] rawPublicKeyBytes) throws Exception {
        byte[] x509Header = {0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x6e, 0x03, 0x21, 0x00};
        byte[] x509Bytes = new byte[x509Header.length + rawPublicKeyBytes.length];
        System.arraycopy(x509Header, 0, x509Bytes, 0, x509Header.length);
        System.arraycopy(rawPublicKeyBytes, 0, x509Bytes, x509Header.length, rawPublicKeyBytes.length);

        KeyFactory kf = KeyFactory.getInstance(ALGORITHM);
        PublicKey publicKey = kf.generatePublic(new X509EncodedKeySpec(x509Bytes));

        KeyAgreement ka = KeyAgreement.getInstance(ALGORITHM);
        ka.init(privateKey);
        ka.doPhase(publicKey, true);

        byte[] rawSecret = ka.generateSecret();
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        return new SecretKeySpec(sha256.digest(rawSecret), "AES");
    }
}