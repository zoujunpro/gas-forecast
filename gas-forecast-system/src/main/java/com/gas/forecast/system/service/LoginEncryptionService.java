package com.gas.forecast.system.service;

public interface LoginEncryptionService {
    String createRsaPublicKey();

    String decrypt(String encryptedText, String publicKey);
}
