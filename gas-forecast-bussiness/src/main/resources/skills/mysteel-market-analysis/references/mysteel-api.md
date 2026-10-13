# 钢联市场分析接口

- 方法：`POST`
- 地址：`https://mcp.mysteel.com/mcp/info/chat-robot/rag/answer`
- 超时：90 秒
- 请求头：`Content-Type: application/json`、`token: <API key>`

请求体：

```json
{
  "query": "市场分析问题"
}
```

成功响应：

```json
{
  "success": true,
  "code": 200,
  "message": "success",
  "data": "# Markdown 市场分析报告"
}
```

处理规则：

- HTTP 错误或 `success=false` 均视为失败。
- 业务码 `400` 表示 Token 校验失败，立即终止且不重试。
- 网络异常、超时以及其他服务端错误最多重试一次。
- `data` 为空视为失败。
