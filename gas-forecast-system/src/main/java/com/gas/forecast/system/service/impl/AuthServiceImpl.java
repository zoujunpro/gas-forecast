package com.gas.forecast.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.system.dto.req.AuthLoginReqDTO;
import com.gas.forecast.system.dto.resp.AuthLoginRespDTO;
import com.gas.forecast.system.dto.resp.AuthMenuRespDTO;
import com.gas.forecast.system.dto.resp.AuthUserRespDTO;
import com.gas.forecast.system.service.AuthService;
import com.gas.forecast.system.service.CaptchaService;
import com.gas.forecast.system.service.LoginEncryptionService;
import com.gas.forecast.system.service.PasswordHashService;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.core.BusinessResponseCode;
import com.gas.forecast.common.security.token.AuthTokenService;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.SysPermissionTb;
import com.gas.forecast.dao.domain.SysUserTb;
import com.gas.forecast.dao.mapper.SysPermissionTbMapper;
import com.gas.forecast.dao.mapper.SysUserRoleRefMapper;
import com.gas.forecast.dao.mapper.SysUserTbMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AuthServiceImpl implements AuthService {
    private final SysUserTbMapper sysUserTbMapper;
    private final SysUserRoleRefMapper sysUserRoleRefMapper;
    private final SysPermissionTbMapper sysPermissionTbMapper;
    private final PasswordHashService passwordHashService;
    private final AuthTokenService authTokenService;
    private final CaptchaService captchaService;
    private final LoginEncryptionService loginEncryptionService;

    public AuthServiceImpl(SysUserTbMapper sysUserTbMapper,
                           SysUserRoleRefMapper sysUserRoleRefMapper,
                           SysPermissionTbMapper sysPermissionTbMapper,
                           PasswordHashService passwordHashService,
                           AuthTokenService authTokenService,
                           CaptchaService captchaService,
                           LoginEncryptionService loginEncryptionService) {
        this.sysUserTbMapper = sysUserTbMapper;
        this.sysUserRoleRefMapper = sysUserRoleRefMapper;
        this.sysPermissionTbMapper = sysPermissionTbMapper;
        this.passwordHashService = passwordHashService;
        this.authTokenService = authTokenService;
        this.captchaService = captchaService;
        this.loginEncryptionService = loginEncryptionService;
    }

    @Override
    public AuthLoginRespDTO login(AuthLoginReqDTO reqDTO) {
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
    public AuthUserRespDTO currentUser(String username) {
        return toUser(loadEnabledUser(username));
    }

    @Override
    public AuthLoginRespDTO currentProfile(String username) {
        return profile(loadEnabledUser(username), null);
    }

    private SysUserTb loadEnabledUser(String username) {
        SysUserTb user = sysUserTbMapper.selectOne(Wrappers.<SysUserTb>lambdaQuery()
                .eq(SysUserTb::getUsername, username)
                .eq(SysUserTb::getDeleted, 0));
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

    private AuthLoginRespDTO profile(SysUserTb user, String token) {
        List<String> roles = sysUserRoleRefMapper.selectRoleCodesByUsername(user.getUsername());
        List<SysPermissionTb> permissions = sysPermissionTbMapper.selectByUsername(user.getUsername());
        List<String> perms = permissions.stream()
                .map(SysPermissionTb::getPerms)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .sorted()
                .toList();
        List<AuthMenuRespDTO> menus = buildMenuTree(permissions.stream()
                .filter(item -> "MENU".equals(item.getPermissionType()))
                .toList());
        return new AuthLoginRespDTO(token, toUser(user), roles, perms, menus);
    }

    private AuthUserRespDTO toUser(SysUserTb user) {
        return new AuthUserRespDTO(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                user.getAvatar(),
                user.getEmail(),
                user.getPhone(),
                user.getOrgCode()
        );
    }

    private List<AuthMenuRespDTO> buildMenuTree(List<SysPermissionTb> permissions) {
        Map<Long, List<SysPermissionTb>> byParent = permissions.stream()
                .collect(Collectors.groupingBy(item -> item.getParentId() == null ? 0L : item.getParentId()));
        return buildChildren(0L, byParent);
    }

    private List<AuthMenuRespDTO> buildChildren(Long parentId, Map<Long, List<SysPermissionTb>> byParent) {
        return byParent.getOrDefault(parentId, List.of()).stream()
                .sorted(Comparator.comparing(SysPermissionTb::getSortNo).thenComparing(SysPermissionTb::getId))
                .map(item -> new AuthMenuRespDTO(
                        item.getId(),
                        item.getParentId(),
                        item.getPermissionName(),
                        item.getPath(),
                        item.getComponent(),
                        item.getIcon(),
                        item.getSortNo(),
                        item.getHidden(),
                        new ArrayList<>(buildChildren(item.getId(), byParent))
                ))
                .toList();
    }
}
