package com.gas.forecast.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class McpToolCatalogTest {
    private final McpToolCatalog catalog = new McpToolCatalog();

    @Test
    void exposesStableBusinessAndTaskTools() {
        Set<String> names = catalog.list().stream().map(McpToolCatalog.ToolDefinition::name).collect(Collectors.toSet());

        assertThat(names).containsExactlyInAnyOrder("region_list", "industry_list", "customer_list",
                "training_start", "training_result_query", "forecast_start", "forecast_result_query", "task_status_get", "task_steps_get");
        assertThat(catalog.list()).allSatisfy(tool -> {
            assertThat(tool.description()).isNotBlank();
            assertThat(tool.inputSchema()).containsEntry("type", "object");
            assertThat(tool.outputSchema()).containsEntry("type", "object");
        });
        McpToolCatalog.ToolDefinition training = catalog.require("training_start");
        @SuppressWarnings("unchecked")
        var properties = (java.util.Map<String, Object>) training.inputSchema().get("properties");
        assertThat(properties).containsKeys("customerCodes", "regionCode", "regionName", "industryCode", "industryName", "confirmed", "idempotencyKey");
        assertThat(training.inputSchema()).containsKey("allOf");
    }
}
