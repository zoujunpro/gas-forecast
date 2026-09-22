package com.gas.forecast.system.controller;

import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.core.ResponseResult;
import com.gas.forecast.common.security.context.AuthContext;
import com.gas.forecast.common.security.token.AuthTokenPayload;
import com.gas.forecast.common.security.token.AuthTokenService;
import com.gas.forecast.common.web.WebLog;
import com.gas.forecast.system.dto.req.AuthLoginRequest;
import com.gas.forecast.system.dto.resp.AuthCaptchaResponse;
import com.gas.forecast.system.dto.resp.AuthLoginResponse;
import com.gas.forecast.system.dto.resp.AuthRsaPublicKeyResponse;
import com.gas.forecast.system.dto.resp.AuthUserResponse;
import com.gas.forecast.system.service.AuthService;
import com.gas.forecast.system.service.CaptchaService;
import com.gas.forecast.system.service.LoginEncryptionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthLoginController {
    private final AuthService authService;
    private final CaptchaService captchaService;
    private final AuthTokenService authTokenService;
    private final LoginEncryptionService loginEncryptionService;

    public AuthLoginController(
            AuthService authService,
            CaptchaService captchaService,
            AuthTokenService authTokenService,
            LoginEncryptionService loginEncryptionService) {
        this.authService = authService;
        this.captchaService = captchaService;
        this.authTokenService = authTokenService;
        this.loginEncryptionService = loginEncryptionService;
    }

    /**
     * 获取登录验证码。
     *
     * @return 验证码标识和验证码图片
     */
    @WebLog("获取登录验证码")
    @GetMapping("/captcha")
    public ResponseResult<AuthCaptchaResponse> captcha() {
        return ResponseResult.success(captchaService.createCaptcha());
    }

    /**
     * 获取登录 RSA 公钥。
     * <p>
     * 前端使用该公钥加密用户名和密码，后端登录时通过缓存的私钥解密。
     *
     * @return RSA 公钥
     */
    @WebLog("获取登录RSA公钥")
    @GetMapping("/rsa-public-key")
    public ResponseResult<AuthRsaPublicKeyResponse> rsaPublicKey() {
        return ResponseResult.success(new AuthRsaPublicKeyResponse(loginEncryptionService.createRsaPublicKey()));
    }

    /**
     * 用户登录。
     *
     * @param reqDTO 登录请求参数
     * @return 登录用户信息、角色、权限、菜单和访问令牌
     */
    @WebLog("用户登录")
    @PostMapping("/login")
    public ResponseResult<AuthLoginResponse> login(@Valid @RequestBody AuthLoginRequest reqDTO) {
        return ResponseResult.success(authService.login(reqDTO));
    }

    /**
     * 用户退出登录。
     *
     * @param request HTTP 请求
     * @return 空结果
     */
    @WebLog("用户退出登录")
    @PostMapping("/logout")
    public ResponseResult<Void> logout(HttpServletRequest request) {
        authTokenService.deleteToken(resolveToken(request));
        return ResponseResult.success(null);
    }

    /**
     * 获取当前登录用户信息。
     *
     * @return 当前登录用户信息
     */
    @WebLog("获取当前登录用户信息")
    @GetMapping("/me")
    public ResponseResult<AuthUserResponse> me() {
        return ResponseResult.success(authService.currentUser(currentUsername()));
    }

    /**
     * 获取当前登录用户权限信息。
     *
     * @return 当前登录用户信息、角色、权限和菜单
     */
    @WebLog("获取当前登录用户权限")
    @GetMapping("/permissions")
    public ResponseResult<AuthLoginResponse> permissions() {
        return ResponseResult.success(authService.currentProfile(currentUsername()));
    }

    /**
     * 获取当前登录用户名。
     *
     * @return 当前登录用户名
     */
    private String currentUsername() {
        AuthTokenPayload payload = AuthContext.get();
        if (payload == null) {
            throw new BusinessException("401", "未登录");
        }
        return payload.username();
    }

    /**
     * 从请求头中解析访问令牌。
     *
     * @param request HTTP 请求
     * @return 访问令牌
     */
    private String resolveToken(HttpServletRequest request) {
        String token = request.getHeader("X-Access-Token");
        if (token == null || token.isBlank()) {
            String authorization = request.getHeader("Authorization");
            if (authorization != null && authorization.startsWith("Bearer ")) {
                token = authorization.substring("Bearer ".length());
            }
        }
        return token;
    }
}
