# Gas Forecast Agent

Python 智能体服务。它负责 Skill 路由和 MCP 工具调用；对外会话入口、业务数据、训练、预测、会话消息和任务状态均由 Java Business Service 保存和执行。

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

Vue 先调用 Java `POST /ai/conversations/chat`。Java 创建会话、用户消息和 Agent Run 后，再调用 Python 内部接口 `POST /api/v1/internal/chat`：

```text
Java创建或校验会话并保存用户消息
  -> Java创建Agent Run并加载历史消息及业务引用
  -> Java调用Python内部Agent接口
  -> LangGraph：加载Java随请求传入的会话记忆
  -> LangGraph：一次识别意图、操作、实体、时间和指代
  -> RequestResolver：恢复澄清上下文、检查意图/操作、解析实体和缺失参数
  -> LangGraph：把稳定意图映射到唯一Skill
  -> ToolPlanner：在Skill工具白名单内确定唯一下一步工具
  -> PlanValidator：校验工具、参数、权限、依赖和写操作确认
  -> ToolExecutor：每次只执行一个MCP工具
  -> ResultEvaluator：判断完成、等待用户或继续规划
  -> LangGraph：保存会话记忆（失败只告警，不改变已完成业务结果）
  -> LangGraph：组装Python内部接口响应
  -> Python向Java返回Agent状态、回复、Skill和业务对象引用
  -> Java归档助手消息及引用并更新Agent Run
  -> Java向Vue返回会话结果
```

主流程在 `workflow.py` 中按 `load_memory -> understand_request -> select_skill -> execute_action -> save_memory -> build_response` 显式声明；`select_skill` 前执行请求预校验，`execute_action` 内部按规划、校验、单工具执行、结果评估循环推进。MCP 客户端、配置和 Skill 注册表通过运行上下文传入，不放进业务状态。

当前不启用独立的 LangGraph Checkpointer。会话、消息、Agent Run 和会话记忆的持久化来源仍是 Java 数据库；每次 Python 图运行只处理一个已有的 Agent Run。页面重开或服务重启后，由 Java 重新加载摘要、记忆和最近消息并随请求传给 Python，避免 Java 和 Python 各保存一套会话状态。

Skill 只声明能力边界和允许调用的 MCP 工具，不再重复实现意图识别、实体解析、指代消解、时间解析或用户澄清。每个意图唯一对应一个 Skill：

| 意图 | Skill | 职责 | 是否只读 |
| --- | --- | --- | --- |
| `TRAIN` | `model-training-management` | 创建训练或强制重训任务 | 否 |
| `FORECAST` | `customer-gas-forecast` | 创建预测任务 | 否 |
| `QUERY` | `business-data-query` | 查询主数据、训练、预测和执行任务 | 是 |
| `ANALYZE` | `forecast-analysis` | 获取真实结果后进行解释或比较 | 是 |
| `KNOWLEDGE_QA` | `knowledge-qa` | 回答概念和系统使用问题 | 是，不调用业务工具 |
| `CHAT` | `general-chat` | 处理问候、感谢、告别和普通寒暄 | 是，不调用业务工具 |
| `UNKNOWN` | `unknown-request` | 承接无法可靠识别或超出能力的请求 | 是，不调用业务工具 |

公共执行器读取 `SKILL.md` 的白名单，再使用 MCP 提供的真实工具名称、参数和返回结构。模型采用逐步规划：每轮只决定一个下一步，Python 执行工具并把真实结果交回模型后再规划。单次请求默认最多8轮、45秒，同一工具最多调用2次、写工具最多调用1次；这些是可配置的安全预算，不是固定业务工作流。创建训练或预测任务后立即结束本轮，不在一次请求中持续轮询。

工具选择位于 Skill 内部：程序先处理实体校验、待确认动作等确定性依赖，随后由模型在当前 Skill 的工具白名单中只规划“下一步一个工具”；每次执行前再由程序校验工具是否存在、参数 Schema、确认状态、调用预算和读写风险。意图识别层不选择或调用工具。

执行预算配置：

- `GAS_AGENT_MAX_STEPS=8`
- `GAS_AGENT_MAX_DURATION_SECONDS=45`
- `GAS_AGENT_MAX_SAME_TOOL_CALLS=2`
- `GAS_AGENT_MAX_WRITE_CALLS=1`

Skill 路由配置：

- `GAS_SKILL_ROUTING_MODE=hybrid` 或 `llm`：由一次结构化模型调用同时识别顶层意图和业务参数，需要同时配置 `LLM_API_KEY` 和 `LLM_MODEL`。
- 模型只返回 `TRAIN`、`FORECAST`、`QUERY`、`ANALYZE`、`KNOWLEDGE_QA`、`CHAT` 或 `UNKNOWN`，服务端再映射到已注册 Skill；模型不直接生成 Skill 目录名。
- 完整的问候、感谢和告别由程序保护为 `CHAT`；包含实际业务目标的长句仍进入语义模型。`UNKNOWN` 是安全出口，不能用猜测的执行型意图替代。
- 普通 `TEXT` 不恢复待处理动作；只有携带 `interactionId` 和 `replyToTurnId` 的结构化澄清、确认或拒绝回复才能恢复原请求。
- Skill 路由不使用关键词规则猜测生产意图。未返回意图时，RequestResolver 返回结构化澄清；用户回复后从原请求恢复并重新确定最终意图，而不是把短答案当作独立新请求。
- `rules` 目前仅保留给 Skill 内部工具决策的本地测试，不可作为完整会话的生产路由模式。
- Python复用项目现有的 `LLM_BASE_URL`、`LLM_MODEL`、`LLM_API_KEY`，同时兼容原来的 `GAS_LLM_*` 名称。

结构化意图识别只描述用户本轮表达，不查询主数据，也不决定是否需要澄清。输出契约为：

```json
{
  "dialogueAct": "NEW_REQUEST",
  "requests": [
    {
      "requestId": "R1",
      "intent": "QUERY",
      "scenario": null,
      "targetSelection": "EXPLICIT",
      "entities": {
        "regions": [],
        "industries": [],
        "customers": ["高邮安源", "扬州中燃"]
      },
      "references": {},
      "parameters": {},
      "dependsOn": []
    }
  ]
}
```

- `entities` 始终保存用户原话中的名称数组，不包含或猜测客户、地区、行业编码；编码由后续主数据解析获得。
- `targetSelection` 只描述用户如何选择目标：`EXPLICIT`、`ALL_MATCHED`、`CONTEXT` 或 `UNSPECIFIED`，不提前判断最终是单个还是批量执行。
- `references` 保存“刚才那批”“这些客户”等待上下文解析的指代；“最新”等查询要求保留在用户原话中，由查询 Skill 选择相应工具。
- 一句话包含多个独立操作时拆成多个 `requests`，通过 `dependsOn` 表达同轮依赖。
- 缺参、实体无匹配或多义匹配以及是否需要追问，由实体解析、工具 Schema 和执行规划阶段决定；意图识别结果不包含澄清结论或澄清问题。
- 单个和批量实体都必须调用主数据工具校验。批量名称逐项返回 `UNIQUE`、`MULTIPLE` 或 `NOT_FOUND`；同类实体按“或”、不同类实体按“且”组合，只有全部解析完成后才向训练或预测工具传递编码集合。

## 澄清与确认协议

用户始终通过普通对话输入。`inputType` 区分新消息 `TEXT`、澄清回复 `CLARIFICATION_REPLY` 和待确认操作回复 `INTERACTION_REPLY`。后两类回复携带上一轮返回的 `interactionId`、`replyToTurnId`，答案就在 `message` 中，不再提交表单 `answers`；Java 和 Python 都校验交互身份。对于 `INTERACTION_REPLY`，Python 先识别明确确认/取消词，其余原话再交给模型判断确认、取消、修改或继续澄清。

等待状态统一返回 `clarification`。实体和参数问题属于 `CLARIFICATION`，写操作批准属于 `CONFIRMATION`。会话记忆只保存两个字段：

- `pendingOperation`：唯一未完成操作，包含 `interaction`（编号、类型、来源轮次）、`semantics`（原请求语义）、`parameters`（已确认参数）、`issues`（待回答问题及候选）和 `pendingTool`。
- `currentBusinessContext`：Java 维护的页面焦点，只供明确指代使用，不自动补入新预测。

用户可输入候选序号或完整实体名称。序号只能从服务端保存的候选对象恢复编码；新名称必须经过主数据校验。同一澄清链只覆盖本轮有效参数；新 `TEXT` 请求不恢复上一操作快照。

Python 返回的 `memoryPatch.pendingOperation` 替换当前操作，值为 `null` 表示完成或取消；整个 `memoryPatch` 为 `null` 表示本轮失败、保留原记忆。Java 与助手消息同事务保存。旧记忆字段不再读取或双写；升级前未结束的澄清需重新发起，不需要修改数据库表结构。

## 运行时对象

`AgentGraphState` 有 7 个顶层字段：`request`、`timing`、`memory`、`understanding`、`result`、`response`、`memory_patch`。内部嵌套字段均为明确模型或标量/列表，不使用任意字典承载状态。

- 参数及指代：`operation_models.py` 中的 `OperationParameters`、`SemanticParameters`、`ReferenceMentions`。
- 实体校验：`entity_models.py` 中的 `EntityResolution`、`EntityResolutionItem`、`EntityCandidate`。
- 工具结果：`tool_results.py` 中的分页、任务、步骤、预测结果模型。
- 会话记忆：`schemas.py` 中的 `ConversationMemory`、`PendingOperation`、`MemoryPatch`。

例如调试时可直接查看 `state.understanding.entity_resolution.items[0].candidates` 和 `state.memory.pending_operation.parameters.forecast_horizon`。字典仅用于模型/MCP/JSON 适配及 LangGraph 节点更新；模型平台原始产物仍保留 Java 接口的文本快照，不改变业务结果协议。

## 记忆边界

- 短期记忆：保存在 Java `ai_conversation_tb.memory_json`，只记录未完成操作和页面焦点。每次 HTTP 请求创建新的图状态，不引入第二套 Checkpointer 持久化。
- 长期记忆：当前不启用。跨会话用户偏好、常用客户等信息以后应使用独立用户记忆表和MCP工具，并支持查看、修改和删除，不能把全部聊天内容永久保存。

## 当前接口

- `GET /health`：Python 服务健康检查
- `POST /api/v1/internal/chat`：仅供 Java 会话服务调用的 Agent 编排接口；要求 Java 已生成 `conversationNo`、`turnId` 和 `runNo`
