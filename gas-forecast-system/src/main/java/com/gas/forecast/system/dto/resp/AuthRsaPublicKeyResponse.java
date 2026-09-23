package com.gas.forecast.system.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RSA公钥应答参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthRsaPublicKeyResponse {
    /**
     * RSA公钥。
     */
    private String publicKey;
}
