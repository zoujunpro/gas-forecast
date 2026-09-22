package com.gas.forecast.system.service;

import com.gas.forecast.system.dto.req.AuthLoginRequest;
import com.gas.forecast.system.dto.resp.AuthLoginResponse;
import com.gas.forecast.system.dto.resp.AuthUserResponse;

public interface AuthService {
    AuthLoginResponse login(AuthLoginRequest reqDTO);

    AuthUserResponse currentUser(String username);

    AuthLoginResponse currentProfile(String username);
}
