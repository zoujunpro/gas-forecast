package com.gas.forecast.common.security.config;

import com.gas.forecast.common.security.interceptor.AuthInterceptor;
import com.gas.forecast.common.security.properties.SecurityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityWebMvcConfig implements WebMvcConfigurer {
    private final AuthInterceptor authInterceptor;
    private final SecurityProperties securityProperties;

    public SecurityWebMvcConfig(AuthInterceptor authInterceptor, SecurityProperties securityProperties) {
        this.authInterceptor = authInterceptor;
        this.securityProperties = securityProperties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns(securityProperties.getIncludePathPatterns())
                .excludePathPatterns(securityProperties.getExcludePathPatterns());
    }
}
