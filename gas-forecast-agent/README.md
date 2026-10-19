# Gas Forecast Agent

Python 智能体服务。它负责统一会话入口、Skill 路由和 MCP 工具调用；业务数据、训练、预测、会话记忆和任务状态仍由 Java Business Service 保存和执行。

当前包含三个智能体入口：

- `short-term`：短期客户日预测
- `monthly-sales`：月度销量预测
- `winter-supply`：冬季保供预测

## 启动

```bash
cd gas-forecast-agent
python -m venv .venv
.venv/bin/pip install -e '.[dev]'
cp .env.example .env
.venv/bin/uvicorn gas_forecast_agent.main:app --reload --port 8091
```

Java 本地开发环境 MCP 默认地址为 `http://127.0.0.1:18083/gas-forecast/mcp`。Jenkins 部署环境通过独立的 `agent.env` 覆盖为 `http://127.0.0.1:18081/gas-forecast/mcp`。本地脱离浏览器联调时，可以在 `GAS_MCP_AUTHORIZATION` 中配置完整的 `Authorization` 请求头值。

生产请求会优先把 Web 请求的 `X-Access-Token` 或 `Authorization` 原样透传给 Java MCP，由 Java 对每次工具调用独立鉴权；`GAS_MCP_AUTHORIZATION` 只作为本地联调兜底，不会进入模型上下文或会话记忆。

## 智能体主流程

浏览器调用 `POST /api/v1/chat`，Python 负责完整对话轮次：

```text
调用Java conversation_prepare校验Token、创建或校验会话并保存用户消息
  -> 读取Java会话记忆
  -> 根据名称和描述选择 Skill
  -> 读取完整 SKILL.md
  -> 从MCP发现工具并按Skill白名单过滤
  -> 模型选择一个下一步工具或向用户追问
  -> Python校验白名单、确认和停止条件
  -> 调用工具并把结果交回模型
  -> 更新Java会话记忆（失败只告警，不改变已完成业务结果）
  -> 调用Java conversation_reply_append归档Agent回复
  -> 返回回答或异步任务状态
```

`SKILL.md` 是唯一业务处理说明；工具名称、参数和返回结构来自 MCP。模型采用逐步规划：每轮只决定一个下一步，Python 执行工具并把真实结果交回模型后再规划。单次请求默认最多8轮、45秒，同一工具最多调用2次、写工具最多调用1次；这些是可配置的安全预算，不是固定业务工作流。创建训练、预测、恢复或重试任务后立即结束本轮，不在一次请求中持续轮询。

执行预算配置：

- `GAS_AGENT_MAX_STEPS=8`
- `GAS_AGENT_MAX_DURATION_SECONDS=45`
- `GAS_AGENT_MAX_SAME_TOOL_CALLS=2`
- `GAS_AGENT_MAX_WRITE_CALLS=1`

Skill 路由配置：

- `GAS_SKILL_ROUTING_MODE=hybrid`：明确表达优先走确定性规则，其他表达交给千问选择 Skill；模型失败时降级为规则路由。
- `GAS_SKILL_ROUTING_MODE=llm`：优先使用千问选择 Skill，需要同时配置 `LLM_API_KEY` 和 `LLM_MODEL`。
- `GAS_SKILL_ROUTING_MODE=rules`：仅使用确定性规则选择 Skill，适合本地开发和自动化测试。
- Python复用项目现有的 `LLM_BASE_URL`、`LLM_MODEL`、`LLM_API_KEY`，同时兼容原来的 `GAS_LLM_*` 名称。

## 记忆边界

- 短期记忆：保存在Java `ai_conversation_tb.memory_json`，仅作用于当前会话，记录当前 Skill、已确认参数、任务批次和待确认工具。
- 长期记忆：当前不启用。跨会话用户偏好、常用客户等信息以后应使用独立用户记忆表和MCP工具，并支持查看、修改和删除，不能把全部聊天内容永久保存。

## 当前接口

- `GET /health`：Python 服务健康检查
- `GET /api/v1/mcp/tools`：连接 Java MCP 并读取工具目录
- `GET /api/v1/skills`：读取 Skill 名称、描述和工具白名单
- `POST /api/v1/chat`：浏览器统一对话入口，管理会话持久化和智能体执行
