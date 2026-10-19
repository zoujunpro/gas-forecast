---
name: business-data-query
description: 查询燃气预测系统中的地区、行业、客户和任务状态，也负责无需业务工具的问候与普通咨询；不用于创建训练或预测任务。
allowed-tools:
  - region_list
  - industry_list
  - region_search
  - industry_search
  - customer_list
  - customer_search
  - business_reference_resolve
  - task_status_get
  - task_steps_get
---

# 业务数据查询

只依据工具返回的业务事实回答。无需业务数据的问候和普通咨询直接回答，不调用工具。

处理请求前读取：

- [查询选择与参数](references/query-rules.md)
- [结果解释与异常](references/result-handling.md)

## 查询规则

- 查询地区、行业、客户列表时调用对应列表工具。
- “城市燃气”“城镇燃气”按“城燃”理解，行业编码仍须通过工具确认。
- 按地区名或行业名筛选客户时，先分别解析成标准编码，再调用 `customer_list`；不得把名称参数直接传给只接受编码的列表工具。
- 查询客户时保留本轮明确给出的地区和行业约束，不继承旧客户过滤条件。
- 查询单个客户时调用 `customer_search`；多个候选时展示候选并停止。
- 用户提供的编号类型不明确时先调用 `business_reference_resolve`；确认是任务号后再查询任务状态或步骤。
- 分页结果说明总数和已展示数量，不得把单页结果说成全部。

## 失败和停止条件

- 未找到数据时直接说明并建议补充条件。
- 工具返回业务失败时展示真实原因，不编造数据，不切换到写操作。
- 用户转为训练、预测、恢复或重试请求时，停止本 Skill 并重新选择相应 Skill。
