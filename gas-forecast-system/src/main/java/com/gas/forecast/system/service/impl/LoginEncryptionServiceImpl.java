package com.gas.forecast.system.service.impl;

import com.gas.forecast.common.cache.CacheClient;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.core.BusinessResponseCode;
import com.gas.forecast.common.util.RsaCryptoUtil;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.system.service.LoginEncryptionService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import org.springframework.stereotype.Service;

@Service
public class LoginEncryptionServiceImpl implements LoginEncryptionService {
    private static final String RSA_PRIVATE_KEY_PREFIX = "gas-forecast:auth:rsa:";
    private static final Duration RSA_KEY_TTL = Duration.ofMinutes(2);

    private final CacheClient cacheClient;

    public LoginEncryptionServiceImpl(CacheClient cacheClient) {
        this.cacheClient = cacheClient;
    }

    @Override
    public String createRsaPublicKey() {
        RsaCryptoUtil.RsaKeyPair keyPair = RsaCryptoUtil.generateKeyPair();
        cacheClient.put(privateKeyCacheKey(keyPair.publicKey()), keyPair.privateKey(), RSA_KEY_TTL);
        return keyPair.publicKey();
    }

    @Override
    public String decrypt(String encryptedText, String publicKey) {
        if (!TextUtils.hasText(encryptedText) || !TextUtils.hasText(publicKey)) {
            throw new BusinessException(BusinessResponseCode.LOGIN_ENCRYPTION_PARAM_ERROR);
        }
        String privateKey = cacheClient.get(privateKeyCacheKey(publicKey), String.class);
        if (!TextUtils.hasText(privateKey)) {
            throw new BusinessException(BusinessResponseCode.LOGIN_PAGE_EXPIRED);
        }
        try {
            return RsaCryptoUtil.decryptWithPrivateKey(encryptedText, privateKey);
        } catch (Exception exception) {
            throw new BusinessException(BusinessResponseCode.LOGIN_DECRYPT_FAILED);
        }
    }

    private String privateKeyCacheKey(String publicKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(publicKey.getBytes(StandardCharsets.UTF_8));
            return RSA_PRIVATE_KEY_PREFIX
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to build RSA private key cache key", exception);
        }
    }
}
