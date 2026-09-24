package com.gas.forecast.common.util;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP调用工具。
 */
public final class HttpUtil {

    private static final RestTemplate REST_TEMPLATE = new RestTemplate();

    private HttpUtil() {
    }

    public static JsonNode postJson(String url, JsonNode body) {
        if (!TextUtils.hasText(url)) {
            throw new IllegalArgumentException("HTTP请求地址不能为空");
        }
        try {
            ResponseEntity<JsonNode> response = REST_TEMPLATE.postForEntity(url, body, JsonNode.class);
            return response.getBody();
        } catch (RestClientException e) {
            throw new IllegalStateException("HTTP请求失败：" + e.getMessage(), e);
        }
    }

    public static JsonNode getJson(String url) {
        if (!TextUtils.hasText(url)) {
            throw new IllegalArgumentException("HTTP请求地址不能为空");
        }
        try {
            return REST_TEMPLATE.getForObject(url, JsonNode.class);
        } catch (RestClientException e) {
            throw new IllegalStateException("HTTP请求失败：" + e.getMessage(), e);
        }
    }
}
