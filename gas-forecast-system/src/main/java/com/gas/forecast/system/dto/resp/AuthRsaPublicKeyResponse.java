package com.gas.forecast.system.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthRsaPublicKeyResponse {
    private String publicKey;

    public String publicKey() {
        return publicKey;
    }
}
