# Gas Forecast Agent

Python 智能体服务。它负责统一会话入口、意图理解、能力路由和 MCP 工具调用；业务数据、训练、预测、会话记忆和任务状态仍由 Java Business Service 保存和执行。

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

Java 本地环境 MCP 默认地址为 `http://[::1]:18083/gas-forecast/mcp`，以匹配 Java 当前仅监听 IPv6 的本机环境。如果 Java 服务开启登录鉴权，需要在 `GAS_MCP_AUTHORIZATION` 中配置完整的 `Authorization` 请求头值。

生产请求会优先把 Web 请求的 `Authorization` 头透传给 Java MCP；`GAS_MCP_AUTHORIZATION` 只作为本地联调兜底，不会进入 LangGraph 状态或会话记忆。

## 智能体主流程

`POST /api/v1/conversations/{conversation_no}/messages` 当前执行以下 LangGraph：

```text
读取Java会话记忆
  -> 结构化意图识别
  -> 初步抽取原始实体
  -> 匹配YAML业务能力
  -> 按能力解析地区/行业/客户/时间
  -> 校验标准参数
  -> 更新Java会话记忆
  -> 返回待进入的业务工作流
```

主图只负责理解与路由。训练、预测、查询和恢复将分别实现为业务子图；模型不会直接根据自然语言猜 MCP 工具名。

实体解析采用两阶段：意图识别只保留用户原话中的 `raw_entities`；能力匹配后才调用Java MCP确认真实地区、行业和客户。客户首先调用 `customer_search`，查不到时最多再按已解析地区/行业调用一次受限 `customer_list`，候选由Python确定性排序，不交给模型选择。解析状态包括 `RESOLVED`、`NEED_INFORMATION`、`NEED_SELECTION`、`NOT_FOUND` 和 `INVALID_TIME`。

意图识别配置：

- `GAS_INTENT_MODE=hybrid`：明确表达优先走确定性规则，其他表达交给千问；模型失败时安全降级为澄清。
- `GAS_INTENT_MODE=llm`：强制使用千问识别，需要同时配置 `LLM_API_KEY` 和 `LLM_MODEL`。
- `GAS_INTENT_MODE=rules`：仅用于本地开发和自动化测试。
- Python复用项目现有的 `LLM_BASE_URL`、`LLM_MODEL`、`LLM_API_KEY`，同时兼容原来的 `GAS_LLM_*` 名称。

## 记忆边界

- 短期记忆：保存在Java `ai_conversation_tb.memory_json`，仅作用于当前会话，记录当前能力、客户/地区/行业、任务批次、缺失参数和待确认动作。
- 长期记忆：当前不启用。跨会话用户偏好、常用客户等信息以后应使用独立用户记忆表和MCP工具，并支持查看、修改和删除，不能把全部聊天内容永久保存。
- LangGraph状态：只服务本次编排，不保存Authorization，也不替代Java业务任务表。

## 当前接口

- `GET /health`：Python 服务健康检查
- `GET /api/v1/mcp/tools`：连接 Java MCP 并读取工具目录
- `GET /api/v1/capabilities`：读取业务能力配置及当前可用程度
- `POST /api/v1/conversations/{conversation_no}/messages`：执行记忆读取、意图识别、能力匹配和记忆更新

下一阶段是在 `graph` 下增加查询、训练、预测和恢复子图，并由主图根据能力配置中的 `workflow` 字段进入对应子图。
