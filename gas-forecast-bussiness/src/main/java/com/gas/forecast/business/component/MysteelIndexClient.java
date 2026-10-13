package com.gas.forecast.business.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gas.forecast.business.component.dto.MysteelIndexCandidate;
import com.gas.forecast.business.component.dto.MysteelIndexValue;
import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.util.TextUtils;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 钢联指标搜索和历史数据查询客户端。 */
@Component
public class MysteelIndexClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private volatile String loginToken;

    @Value("${gas.mysteel.index-search-url:https://client.mysteel.com/mysteelsearch/search/searchIndex2}")
    private String indexSearchUrl;
    @Value("${gas.mysteel.search-token:}")
    private String searchToken;
    @Value("${gas.mysteel.search-cookie:}")
    private String searchCookie;
    @Value("${gas.mysteel.search-referer:https://client.mysteel.com/search_ui/}")
    private String searchReferer;
    @Value("${gas.mysteel.index-data-url:https://client.mysteel.com/pmp_api/pmp/web/unifyData/queryIndexDataList}")
    private String indexDataUrl;
    @Value("${gas.mysteel.login-url:https://client.mysteel.com/uniuser-api/v3/common/login}")
    private String loginUrl;
    @Value("${gas.mysteel.access-token:}")
    private String configuredToken;
    @Value("${gas.mysteel.account-name:}")
    private String accountName;
    @Value("${gas.mysteel.password:}")
    private String password;
    @Value("${gas.mysteel.product-num:PD_01}")
    private String productNum;
    @Value("${gas.mysteel.device-id:}")
    private String deviceId;
    @Value("${gas.mysteel.version:2}")
    private String version;
    @Value("${gas.mysteel.cookie:}")
    private String cookie;
    @Value("${gas.mysteel.assembly-id:308}")
    private int assemblyId;
    @Value("${gas.mysteel.index-timeout-seconds:30}")
    private long timeoutSeconds;

    public MysteelIndexClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<MysteelIndexCandidate> searchIndexes(String description, int pageSize) {
        if (!TextUtils.hasText(description)) {
            throw new BusinessException("钢联指标搜索描述不能为空");
        }
        ObjectNode body = objectMapper.createObjectNode();
        body.put("query", description.trim());
        body.put("pageNo", 1);
        body.put("pageSize", Math.min(Math.max(pageSize, 1), 200));
        body.put("isAll", "false");
        body.put("updateStatus", "false");
        body.put("isFilterLimit", "false");
        body.put("advancedSearch", "true");
        JsonNode response = post(indexSearchUrl, body, searchAccessToken(), searchCookie, null);
        String resultCode = response.path("resultCode").asText("").trim();
        if (resultCode.startsWith("-")) {
            throw new BusinessException("钢联指标搜索失败（" + resultCode + "）：" + response.path("resultMsg").asText("未知错误"));
        }
        Map<String, MysteelIndexCandidate> candidates = new LinkedHashMap<>();
        JsonNode data = response.path("data");
        if (data.isMissingNode() || data.isNull() || data.isEmpty()) {
            data = response.path("dataList");
        }
        if (data.isMissingNode() || data.isNull() || data.isEmpty()) {
            data = response.path("searchHits");
        }
        if (data.isObject()) {
            if (data.path("list").isArray())
                data = data.path("list");
            else if (data.path("rows").isArray())
                data = data.path("rows");
            else if (data.path("items").isArray())
                data = data.path("items");
        }
        if (data.isArray()) {
            for (JsonNode item : data) {
                String code = firstText(item, "indexCode", "index_code", "code", "Code");
                String name = firstText(item, "indexName", "index_name", "name", "Name");
                if (TextUtils.hasText(code) && TextUtils.hasText(name)) {
                    candidates.putIfAbsent(code, new MysteelIndexCandidate(code, name));
                }
            }
        }
        return List.copyOf(candidates.values());
    }

    private String firstText(JsonNode item, String... fields) {
        for (String field : fields) {
            String value = item.path(field).asText("").trim();
            if (TextUtils.hasText(value))
                return value;
        }
        return "";
    }

    public List<MysteelIndexValue> fetchIndexValues(String indexCode, LocalDate beginDate, LocalDate endDate) {
        if (!TextUtils.hasText(indexCode) || !indexCode.trim().matches("^[A-Za-z0-9]+$")) {
            throw new BusinessException("钢联指标编码格式不合法");
        }
        if (beginDate == null || endDate == null || beginDate.isAfter(endDate)) {
            throw new BusinessException("钢联指标查询日期范围不合法");
        }
        ObjectNode body = objectMapper.createObjectNode();
        body.put("assemblyId", assemblyId);
        body.putArray("indexCodes").add(indexCode.trim());
        body.put("beginTime", beginDate.toString());
        body.put("endTime", endDate.toString());
        body.put("sortFiled", "dataDate");
        body.put("sortOrder", 1);
        JsonNode response = authenticatedPost(indexDataUrl, body);
        List<MysteelIndexValue> values = new ArrayList<>();
        collectValues(response.path("data"), indexCode.trim(), beginDate, endDate, values);
        values.sort(Comparator.comparing(MysteelIndexValue::dataDate));
        return values;
    }

    private JsonNode authenticatedPost(String url, JsonNode body) {
        RuntimeException firstFailure;
        try {
            return post(url, body, token());
        } catch (RuntimeException exception) {
            firstFailure = exception;
            loginToken = null;
        }
        try {
            return post(url, body, token());
        } catch (RuntimeException exception) {
            exception.addSuppressed(firstFailure);
            throw new BusinessException("钢联指标接口请求失败：" + exception.getMessage());
        }
    }

    private String token() {
        if (TextUtils.hasText(configuredToken)) {
            return configuredToken.trim();
        }
        if (TextUtils.hasText(loginToken)) {
            return loginToken;
        }
        synchronized (this) {
            if (!TextUtils.hasText(loginToken)) {
                loginToken = login();
            }
            return loginToken;
        }
    }

    private String searchAccessToken() {
        return TextUtils.hasText(searchToken) ? searchToken.trim() : token();
    }

    private String login() {
        if (!TextUtils.hasText(accountName) || !TextUtils.hasText(password) || !TextUtils.hasText(deviceId)) {
            throw new BusinessException("未配置钢联指标账号、密码和设备ID");
        }
        ObjectNode body = objectMapper.createObjectNode();
        body.put("accountName", accountName.trim());
        body.put("password", password.trim());
        body.put("productNum", productNum);
        body.put("deviceId", deviceId.trim());
        body.put("version", version);
        JsonNode response = post(loginUrl, body, null);
        String value = response.path("data").path("token").asText("").trim();
        if (!TextUtils.hasText(value)) {
            throw new BusinessException("钢联登录接口未返回Token");
        }
        return value;
    }

    private JsonNode post(String url, JsonNode body, String token) {
        return post(url, body, token, cookie, null);
    }

    private JsonNode post(String url, JsonNode body, String token, String requestCookie, String referer) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(Math.max(timeoutSeconds, 10))).header("Accept", "application/json, text/plain, */*")
                    .header("Content-Type", "application/json;charset=UTF-8").header("Accept-Language", "zh-CN,zh;q=0.9").header("sec-ch-ua", "\"Chromium\";v=\"91\", \" Not;A Brand\";v=\"99\"")
                    .header("sec-ch-ua-mobile", "?0").header("Sec-Fetch-Site", "same-origin").header("Sec-Fetch-Mode", "cors").header("Sec-Fetch-Dest", "empty")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.114 Safari/537.36")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
            if (TextUtils.hasText(token)) {
                builder.header("token", token);
            }
            if (TextUtils.hasText(requestCookie)) {
                builder.header("Cookie", requestCookie);
            }
            if (TextUtils.hasText(referer)) {
                builder.header("Referer", referer);
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("HTTP " + response.statusCode() + "：" + abbreviate(response.body(), 200));
            }
            return objectMapper.readTree(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("钢联指标请求已中断", exception);
        } catch (Exception exception) {
            if (exception instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(exception.getMessage(), exception);
        }
    }

    private void collectValues(JsonNode node, String requestedCode, LocalDate beginDate, LocalDate endDate, List<MysteelIndexValue> values) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return;
        }
        JsonNode hits = node.path("searchHits");
        if (hits.isArray()) {
            for (JsonNode item : hits) {
                try {
                    String dateText = item.path("dataDate").asText("");
                    String valueText = item.path("dataValue").asText("").replace(",", "").trim();
                    LocalDate date = LocalDate.parse(dateText.substring(0, 10));
                    if (!date.isBefore(beginDate) && !date.isAfter(endDate) && TextUtils.hasText(valueText)) {
                        values.add(new MysteelIndexValue(item.path("indexCode").asText(requestedCode), item.path("indexName").asText(""), date, new BigDecimal(valueText)));
                    }
                } catch (RuntimeException ignored) {
                    // 单个异常数据点不影响同一指标的其他有效数据。
                }
            }
        }
        if (node.isArray()) {
            node.forEach(child -> collectValues(child, requestedCode, beginDate, endDate, values));
        } else if (node.isObject()) {
            node.fields().forEachRemaining(entry -> collectValues(entry.getValue(), requestedCode, beginDate, endDate, values));
        }
    }

    private String abbreviate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
