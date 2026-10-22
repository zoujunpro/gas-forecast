package com.gas.forecast.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.gas.forecast.mcp.dto.JsonRpcRequest;
import com.gas.forecast.mcp.dto.JsonRpcResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class McpServerControllerTest {
    // 这些用例只覆盖 initialize、tools/list 和协议校验，不会进入工具分发器。
    private final McpServerController controller = new McpServerController(new McpToolCatalog(), null, new ObjectMapper());

    @Test
    void initializesAndAdvertisesToolCapability() {
        JsonRpcRequest request = new JsonRpcRequest("2.0", IntNode.valueOf(1), "initialize", JsonNodeFactory.instance.objectNode());
        ResponseEntity<JsonRpcResponse<?>> response = controller.handle(request, null);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getFirst("Mcp-Session-Id")).isNotBlank();
        assertThat(response.getBody()).asString().contains("protocolVersion", "tools", "gas-forecast-business-server");
    }

    @Test
    void listsBusinessToolsWithoutSessionMemoryTools() {
        JsonRpcRequest request = new JsonRpcRequest("2.0", IntNode.valueOf(2), "tools/list", JsonNodeFactory.instance.objectNode());
        ResponseEntity<JsonRpcResponse<?>> response = controller.handle(request, "2025-03-26");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).asString().contains("forecast_result_query").doesNotContain("memory_get", "memory_update");
    }

    @Test
    void rejectsNonJsonRpcTwoRequestWithUnifiedBusinessErrorInData() {
        JsonRpcRequest request = new JsonRpcRequest("1.0", IntNode.valueOf(3), "tools/list", JsonNodeFactory.instance.objectNode());
        ResponseEntity<JsonRpcResponse<?>> response = controller.handle(request, "2025-03-26");

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        assertThat(response.getBody().error().code()).isEqualTo(-32600);
        assertThat(response.getBody().error().data()).hasFieldOrPropertyWithValue("code", "INVALID_REQUEST");
    }
}
