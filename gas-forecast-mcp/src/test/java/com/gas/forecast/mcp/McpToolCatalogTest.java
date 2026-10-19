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

        assertThat(names).containsExactlyInAnyOrder("conversation_prepare", "conversation_reply_append", "memory_get", "memory_update", "customer_search", "region_search", "industry_search", "region_list", "industry_list", "customer_list", "business_reference_resolve",
                "training_start", "training_status_get", "training_result_get", "training_latest_get", "training_history_get", "forecast_start", "forecast_status_get", "forecast_result_get",
                "forecast_latest_get", "forecast_history_get", "task_status_get", "task_steps_get", "task_step_record", "task_resume", "task_retry");
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
