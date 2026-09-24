package com.gas.forecast.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.core.BusinessResponseCode;
import com.gas.forecast.common.security.token.AuthTokenService;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.SysPermissionTb;
import com.gas.forecast.dao.domain.SysUserTb;
import com.gas.forecast.dao.mapper.SysPermissionTbMapper;
import com.gas.forecast.dao.mapper.SysUserRoleRefMapper;
import com.gas.forecast.dao.mapper.SysUserTbMapper;
import com.gas.forecast.system.dto.req.AuthLoginRequest;
import com.gas.forecast.system.dto.resp.AuthLoginResponse;
import com.gas.forecast.system.dto.resp.AuthMenuResponse;
import com.gas.forecast.system.dto.resp.AuthUserResponse;
import com.gas.forecast.system.service.AuthService;
import com.gas.forecast.system.service.CaptchaService;
import com.gas.forecast.system.service.LoginEncryptionService;
import com.gas.forecast.system.service.PasswordHashService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final SysUserTbMapper sysUserTbMapper;
    private final SysUserRoleRefMapper sysUserRoleRefMapper;
    private final SysPermissionTbMapper sysPermissionTbMapper;
    private final PasswordHashService passwordHashService;
    private final AuthTokenService authTokenService;
    private final CaptchaService captchaService;
    private final LoginEncryptionService loginEncryptionService;

    @Override
    public AuthLoginResponse login(AuthLoginRequest reqDTO) {
        captchaService.validate(reqDTO.captchaId(), reqDTO.captchaCode());
        String username = resolveLoginText(reqDTO.username(), reqDTO.rsaPublicKey());
        String password = resolveLoginText(reqDTO.password(), reqDTO.rsaPublicKey());
        SysUserTb user = loadEnabledUser(username);
        String inputHash = passwordHashService.hash(user.getUsername(), password, user.getPasswordSalt());
        if (!inputHash.equals(user.getPasswordHash())) {
            throw new BusinessException(BusinessResponseCode.LOGIN_FAILED);
        }
        return profile(user, authTokenService.createToken(user.getId(), user.getUsername()));
    }

    @Override
    public AuthUserResponse currentUser(String username) {
        return toUser(loadEnabledUser(username));
    }

    @Override
    public AuthLoginResponse currentProfile(String username) {
        return profile(loadEnabledUser(username), null);
    }

    private SysUserTb loadEnabledUser(String username) {
        SysUserTb user = sysUserTbMapper.selectOne(Wrappers.<SysUserTb>lambdaQuery().eq(SysUserTb::getUsername, username).eq(SysUserTb::getDeleted, 0));
        if (user == null) {
            throw new BusinessException(BusinessResponseCode.LOGIN_FAILED);
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException("403", "用户已禁用");
        }
        return user;
    }

    private String resolveLoginText(String value, String rsaPublicKey) {
        if (!TextUtils.hasText(rsaPublicKey)) {
            return value;
        }
        return loginEncryptionService.decrypt(value, rsaPublicKey);
    }

    private AuthLoginResponse profile(SysUserTb user, String token) {
        List<String> roles = sysUserRoleRefMapper.selectRoleCodesByUsername(user.getUsername());
        List<SysPermissionTb> permissions = sysPermissionTbMapper.selectByUsername(user.getUsername());
        List<String> perms = permissions.stream().map(SysPermissionTb::getPerms).filter(value -> value != null && !value.isBlank()).distinct().sorted().toList();
        List<AuthMenuResponse> menus = buildMenuTree(permissions.stream().filter(item -> "DIRECTORY".equals(item.getPermissionType()) || "MENU".equals(item.getPermissionType())).toList());
        return new AuthLoginResponse(token, toUser(user), roles, perms, menus);
    }

    private AuthUserResponse toUser(SysUserTb user) {
        return new AuthUserResponse(user.getId(), user.getUsername(), user.getRealName(), user.getAvatar(), user.getEmail(), user.getPhone(), user.getOrgCode());
    }

    private List<AuthMenuResponse> buildMenuTree(List<SysPermissionTb> permissions) {
        Map<Long, List<SysPermissionTb>> byParent = permissions.stream().collect(Collectors.groupingBy(item -> item.getParentId() == null ? 0L : item.getParentId()));
        return buildChildren(0L, byParent);
    }

    private List<AuthMenuResponse> buildChildren(Long parentId, Map<Long, List<SysPermissionTb>> byParent) {
        return byParent.getOrDefault(parentId, List.of()).stream().sorted(Comparator.comparing(SysPermissionTb::getSortNo).thenComparing(SysPermissionTb::getId))
                .map(item -> new AuthMenuResponse(item.getId(), item.getParentId(), item.getPermissionName(), item.getPath(), item.getComponent(), item.getIcon(), item.getSortNo(), item.getHidden(),
                        new ArrayList<>(buildChildren(item.getId(), byParent))))
                .toList();
    }
}
