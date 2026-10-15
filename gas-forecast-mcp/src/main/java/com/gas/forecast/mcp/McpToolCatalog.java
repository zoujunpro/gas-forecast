package com.gas.forecast.mcp;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/** 从YAML加载MCP工具名称、描述、输入输出Schema和安全提示。 */
@Component
public class McpToolCatalog {
    private static final Set<String> DISPATCHED_TOOLS = Set.of("memory_get", "memory_update", "customer_search", "region_search", "industry_search", "training_start",
            "training_status_get", "training_result_get", "forecast_start", "forecast_status_get", "forecast_result_get");
    private final Map<String, ToolDefinition> tools;

    /** Spring运行时入口，配置项允许以后切换成外部文件。 */
    @Autowired
    public McpToolCatalog(ResourceLoader resourceLoader, @Value("${gas.forecast.mcp.tool-catalog:classpath:mcp-tools.yml}") String location) {
        this(resourceLoader.getResource(location));
    }

    /** 测试及独立使用时加载模块内默认配置。 */
    McpToolCatalog() {
        this(new ClassPathResource("mcp-tools.yml"));
    }

    /** 加载并校验工具配置；配置错误时直接阻止应用启动。 */
    McpToolCatalog(Resource resource) {
        this.tools = load(resource);
    }

    /** 返回工具目录的只读快照。 */
    public List<ToolDefinition> list() {
        return List.copyOf(tools.values());
    }

    /** 根据名称获取白名单工具；未注册名称不能调用。 */
    public ToolDefinition require(String name) {
        ToolDefinition tool = tools.get(name);
        if (tool == null)
            throw new IllegalArgumentException("未知工具：" + name);
        return tool;
    }

    @SuppressWarnings("unchecked")
    private Map<String, ToolDefinition> load(Resource resource) {
        if (!resource.exists())
            throw new IllegalStateException("MCP工具配置文件不存在：" + resource.getDescription());
        YamlMapFactoryBean yaml = new YamlMapFactoryBean();
        yaml.setResources(resource);
        Map<String, Object> root = yaml.getObject();
        Object configured = root == null ? null : root.get("tools");
        if (!(configured instanceof List<?> rows) || rows.isEmpty())
            throw new IllegalStateException("MCP工具配置缺少非空tools列表：" + resource.getDescription());
        Map<String, ToolDefinition> loaded = new LinkedHashMap<>();
        for (Object value : rows) {
            if (!(value instanceof Map<?, ?> raw))
                throw new IllegalStateException("MCP工具配置项必须是对象");
            Map<String, Object> row = (Map<String, Object>) raw;
            String name = requiredText(row, "name");
            String title = text(row.get("title"), name);
            String description = requiredText(row, "description");
            Map<String, Object> inputSchema = requiredMap(row, "inputSchema");
            Map<String, Object> outputSchema = requiredMap(row, "outputSchema");
            Map<String, Object> annotations = requiredMap(row, "annotations");
            validateSchema(name, "inputSchema", inputSchema);
            validateSchema(name, "outputSchema", outputSchema);
            if (loaded.putIfAbsent(name, new ToolDefinition(name, title, description, inputSchema, outputSchema, annotations)) != null)
                throw new IllegalStateException("MCP工具名称重复：" + name);
        }
        Set<String> missing = new LinkedHashSet<>(DISPATCHED_TOOLS);
        missing.removeAll(loaded.keySet());
        Set<String> unsupported = new LinkedHashSet<>(loaded.keySet());
        unsupported.removeAll(DISPATCHED_TOOLS);
        if (!missing.isEmpty() || !unsupported.isEmpty())
            throw new IllegalStateException("MCP工具配置与分发器不一致，缺少=" + missing + "，未实现=" + unsupported);
        return Collections.unmodifiableMap(new LinkedHashMap<>(loaded));
    }

    private void validateSchema(String toolName, String field, Map<String, Object> schema) {
        if (!"object".equals(schema.get("type")) || !(schema.get("properties") instanceof Map<?, ?>))
            throw new IllegalStateException("工具" + toolName + "的" + field + "必须是包含properties的object Schema");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requiredMap(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (!(value instanceof Map<?, ?>))
            throw new IllegalStateException("MCP工具" + row.get("name") + "缺少" + key);
        return Map.copyOf((Map<String, Object>) value);
    }

    private String requiredText(Map<String, Object> row, String key) {
        String value = text(row.get(key), null);
        if (value == null || value.isBlank())
            throw new IllegalStateException("MCP工具配置缺少" + key);
        return value;
    }

    private String text(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value).trim();
    }

    /** tools/list返回的完整工具定义。 */
    public record ToolDefinition(String name, String title, String description, Map<String, Object> inputSchema, Map<String, Object> outputSchema,
            Map<String, Object> annotations) {
    }
}
