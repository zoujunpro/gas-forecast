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

- `memory_get`, `memory_update`
- `customer_search`, `region_search`, `industry_search`
- `training_start`, `training_status_get`, `training_result_get`
- `forecast_start`, `forecast_status_get`, `forecast_result_get`

Mutation tools reuse `AiConversationService`, which in turn calls the existing
training and forecast business services. Entity tools reuse `AiEntityTranslator`.
The MCP adapter does not write model, training, forecast, customer, region or
industry tables directly.

Batch training and forecast calls require `bulk=true` and `confirmed=true`.

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
    "name": "forecast_status_get",
    "arguments": {
      "conversationNo": "CONV-001",
      "forecastBatchNo": "FCBATCH-001"
    }
  }
}
```

## Current boundary

The module implements MCP initialization, ping, `tools/list`, `tools/call`, and
initialized notifications over JSON HTTP. It deliberately stays independent from
Spring AI because this application currently runs Spring Boot 3.2.5, while current
Spring AI MCP server transports target a newer Spring Framework generation.
