package com.gas.forecast.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class McpToolCatalogTest {
    private final McpToolCatalog catalog = new McpToolCatalog();

    @Test
    void exposesElevenStableBusinessTools() {
        Set<String> names = catalog.list().stream().map(McpToolCatalog.ToolDefinition::name).collect(Collectors.toSet());

        assertThat(names).containsExactlyInAnyOrder("memory_get", "memory_update", "customer_search", "region_search", "industry_search", "training_start", "training_status_get",
                "training_result_get", "forecast_start", "forecast_status_get", "forecast_result_get");
        assertThat(catalog.list()).allSatisfy(tool -> {
            assertThat(tool.description()).isNotBlank();
            assertThat(tool.inputSchema()).containsEntry("type", "object");
            assertThat(tool.outputSchema()).containsEntry("type", "object");
        });
        McpToolCatalog.ToolDefinition training = catalog.require("training_start");
        @SuppressWarnings("unchecked")
        var properties = (java.util.Map<String, Object>) training.inputSchema().get("properties");
        assertThat(properties).containsKeys("regionCode", "regionName", "industryCode", "industryName", "confirmed", "idempotencyKey");
        assertThat(training.inputSchema()).containsKey("allOf");
    }
}
