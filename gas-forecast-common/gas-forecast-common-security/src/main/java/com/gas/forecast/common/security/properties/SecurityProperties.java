package com.gas.forecast.common.security.properties;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gas.security")
public class SecurityProperties {
    private List<String> includePathPatterns = new ArrayList<>(List.of(
            "/auth/**",
            "/system/**",
            "/base-region/**",
            "/base-customer/**",
            "/base-industry/**",
            "/data-file-info/**",
            "/data-daily-sales/**",
            "/data-monthly-sales/**",
            "/forecast/**",
            "/agents/**",
            "/predict",
            "/chat"));
    private List<String> excludePathPatterns = new ArrayList<>(List.of(
            "/auth/captcha",
            "/auth/rsa-public-key",
            "/auth/login",
            "/auth/logout",
            "/monthly-results/**",
            "/short-term-results/**",
            "/winter-supply-results/**"));

    public List<String> getIncludePathPatterns() {
        return includePathPatterns;
    }

    public void setIncludePathPatterns(List<String> includePathPatterns) {
        this.includePathPatterns = includePathPatterns;
    }

    public List<String> getExcludePathPatterns() {
        return excludePathPatterns;
    }

    public void setExcludePathPatterns(List<String> excludePathPatterns) {
        this.excludePathPatterns = excludePathPatterns;
    }
}
