package com.gas.forecast.system.service;

import com.gas.forecast.system.dto.req.AuthLoginReqDTO;
import com.gas.forecast.system.dto.resp.AuthLoginRespDTO;
import com.gas.forecast.system.dto.resp.AuthMenuRespDTO;
import com.gas.forecast.system.dto.resp.AuthUserRespDTO;

public interface AuthService {
    AuthLoginRespDTO login(AuthLoginReqDTO reqDTO);

    AuthUserRespDTO currentUser(String username);

    AuthLoginRespDTO currentProfile(String username);
}
