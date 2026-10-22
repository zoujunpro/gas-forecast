# Gas Forecast MCP Server

`gas-forecast-mcp` exposes the existing Java gas-forecast business workflows to a
Python agent through an MCP-compatible JSON-RPC endpoint.

## Endpoint

With the default application context path the endpoint is:

```text
POST /gas-forecast/mcp
```

The endpoint is protected by the existing authentication interceptor. Clients must
send either `Authorization: Bearer <token>` or `X-Access-Token: <token>`.

Configuration:

```yaml
gas:
  forecast:
    mcp:
      enabled: true
      endpoint: /mcp
```

## Tools

- `region_list`, `industry_list`, `customer_list`
- `training_start`, `training_result_query`
- `forecast_start`, `forecast_result_query`

Conversation and message persistence is handled by the Java HTTP chat entry before
and after Python execution, so it is intentionally not exposed as an MCP tool.
Training and forecast tools call their corresponding deterministic business
services. Java does not select Skills, parse user intent or plan tool order. The MCP
adapter does not write model, training, forecast, customer, region or industry tables
directly.

实体名称校验由 Java 的内部 Agent 接口完成，不出现在 MCP `tools/list`。Batch
training and forecast calls require `bulk=true` and `confirmed=true`.

## Example

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/list",
  "params": {}
}
```

Tool call:

```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/call",
  "params": {
    "name": "forecast_result_query",
    "arguments": {
      "customerCode": "CUSTOMER-001",
      "agentCode": "short-term"
    }
  }
}
```

## Current boundary

The module implements MCP initialization, ping, `tools/list`, `tools/call`, and
initialized notifications over JSON HTTP. It deliberately stays independent from
Spring AI because this application currently runs Spring Boot 3.2.5, while current
Spring AI MCP server transports target a newer Spring Framework generation.
