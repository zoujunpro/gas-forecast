---
name: mysteel-market-analysis
description: 调用钢联内部分析接口获取大宗商品市场分析报告。用于价格、供需、库存和市场趋势分析，只返回 Markdown 报告，不提取训练指标。
---

# 钢联市场分析报告

## 目标

根据给定的市场分析问题调用钢联内部接口，返回完整的 Markdown 市场分析报告。

本 Skill 只负责获取报告，不从报告中提取指标定义，不生成指标值，不启动模型训练。

## 输入

输入为一条具体的市场分析查询。训练场景下，查询应包含：

- 分析品类或预测对象；
- 地区、行业或客户范围（存在时）；
- 分析开始和结束日期；
- 日、旬、月等时间粒度；
- 需要关注的价格、供应、需求、库存、政策或市场情绪维度。

## 执行

生产环境调用已注册的 Java 工具 `mysteel_market_analysis`，参数格式：

```json
{
  "query": "<市场分析问题>"
}
```

`scripts/analyze.js` 仅用于脱离 Java 应用时进行接口调试：

```bash
node scripts/analyze.js --query "<市场分析问题>"
```

API Key 从环境变量 `MYSTEEL_API_KEY` 读取。调试脚本也可以通过 `--api_key` 临时传入。不得将密钥写入 Skill、训练快照或日志。

接口协议和异常规则见 [references/mysteel-api.md](references/mysteel-api.md)。

## 输出

成功时原样返回接口 `data` 字段中的 Markdown 报告，不扩写、不提取指标、不包装成其他格式。

鉴权失败立即终止。网络、超时或服务端异常最多重试一次。
