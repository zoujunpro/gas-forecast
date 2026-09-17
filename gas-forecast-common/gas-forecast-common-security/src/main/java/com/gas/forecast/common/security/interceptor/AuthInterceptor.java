package com.gas.forecast.common.security.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gas.forecast.common.core.ResponseResult;
import com.gas.forecast.common.security.annotation.RequirePermission;
import com.gas.forecast.common.security.context.AuthContext;
import com.gas.forecast.common.security.permission.PermissionChecker;
import com.gas.forecast.common.security.token.AuthTokenPayload;
import com.gas.forecast.common.security.token.AuthTokenService;
import com.gas.forecast.common.util.ThreadLocalUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final AuthTokenService authTokenService;
    private final PermissionChecker permissionChecker;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(AuthTokenService authTokenService,
                           PermissionChecker permissionChecker,
                           ObjectMapper objectMapper) {
        this.authTokenService = authTokenService;
        this.permissionChecker = permissionChecker;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = resolveToken(request);
        AuthTokenPayload payload = authTokenService.parse(token);
        if (payload == null) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "401", "登录已过期，请重新登录");
            return false;
        }
        AuthContext.set(payload);
        ThreadLocalUtil.setLoginUser(payload.userId(), payload.username(), token);
        RequirePermission requirePermission = resolveRequirePermission(handler);
        if (requirePermission != null
                && permissionChecker.hasPermissions(payload.username(), requirePermission.value(), requirePermission.logical())) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "403", "无权访问该功能");
            return false;
        }
        return true;
    }

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

    private RequirePermission resolveRequirePermission(Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return null;
        }
        RequirePermission methodPermission = handlerMethod.getMethodAnnotation(RequirePermission.class);
        if (methodPermission != null) {
            return methodPermission;
        }
        return handlerMethod.getBeanType().getAnnotation(RequirePermission.class);
    }

    private void writeError(HttpServletResponse response, int status, String code, String message) throws Exception {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ResponseResult.error(code, message)));
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContext.clear();
        ThreadLocalUtil.clear();
    }
}
