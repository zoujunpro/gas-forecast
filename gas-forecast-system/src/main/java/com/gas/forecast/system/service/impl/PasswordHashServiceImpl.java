package com.gas.forecast.system.service.impl;

import com.gas.forecast.common.util.PasswordHashUtil;
import com.gas.forecast.system.service.PasswordHashService;
import org.springframework.stereotype.Service;

@Service
public class PasswordHashServiceImpl implements PasswordHashService {
    @Override
    public String hash(String username, String password, String salt) {
        return PasswordHashUtil.hash(username, password, salt);
    }
}
