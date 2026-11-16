# 公共展示节点

流程：understand_request → execute_action → presentation → build_response。

节点读取真实工具结果，生成 content_blocks；接口层仅追加由真实业务结果提取的任务卡片。
新消息只使用 type + data 协议，旧 presentation 配置不再由工作流输出。Java 中旧表格信封字段保留，用于历史消息反序列化。

## 组件数据

- MARKDOWN：content。
- TABLE：title、columns（field、title）、rows、total、page、pageSize，可选 pagination。
- CHART：title、chartType（LINE/BAR）、xField、yFields、rows。
- TASK_CARD：真实 taskNo，由现有任务组件读取任务状态。
- RESULT_LINK：前端只接受站内绝对路径。

TABLE 只承载记录（包括空列表）；普通任务、错误、澄清不生成表格。工具执行信封保留在内部执行状态，不能再从 TABLE 读取。
两个聊天页面共用 ContentBlocks，按数组顺序渲染；无 blocks 的旧消息继续使用原有逻辑。

## 分页

完整内联记录使用浏览器本地分页。客户记录未完整加载且存在真实 customer_list 执行轨迹时，节点从轨迹参数绑定 keyword、regionCode、industryCode；模型不能修改分页配置。
Vue 固定调用 POST /base-customer/agentPage，接口每次检查 base:customer:list 权限，并复用 MCP 的客户查询实现，单页最多 200 条。
该接口是实时查询，不是结果快照；客户数据变更可能影响页数。不支持任意工具的分页，也不创建虚构 dataRef。其他未完整加载的结果仅显示已加载数据和总数，不提供无效翻页。

## 模型规划

GAS_PRESENTATION_LLM_ENABLED 默认 true；未配置模型或凭证时直接使用规则。
GAS_PRESENTATION_TIMEOUT_SECONDS 默认 8 秒。模型只接收用户问题、数据集编号、字段类型及已加载数量，不接收业务记录。
输出必须通过 Pydantic 计划校验，所有数据集必须覆盖，图表字段必须存在，Y 轴必须有数值。真实 rows、分页过滤条件、正文均由程序提供。
模型失败、超时、未知字段、非数值纵轴均回退原规则结果，不重试或影响业务执行。

## 验证

Python：在 gas-forecast-agent 中运行 .venv/bin/python -m pytest。
前端：在 gas-forecast-web 中运行 npm run build 和 npm run test。
Java：在仓库根目录运行 mvn -pl gas-forecast-bussiness -am test。
