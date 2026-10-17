"""无需业务工具的普通对话回答。"""

import json
import logging
import re
from typing import Any

from gas_forecast_agent.capabilities import CapabilityDefinition
from gas_forecast_agent.config import Settings
from gas_forecast_agent.llm import build_llm_client

logger = logging.getLogger(__name__)


class ConversationResponder:
    """让大模型回答问候和通用问题，不携带预测、训练或客户上下文。"""

    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._model = settings.llm_model

    async def respond(self, message: str, capability: CapabilityDefinition, history: list[dict[str, str]] | None = None) -> str:
        if not self._model:
            raise ValueError("未配置LLM_MODEL")
        response = await build_llm_client(self._settings).chat.completions.create(
            model=self._model,
            temperature=0.7,
            messages=[
                {
                    "role": "system",
                    "content": (
                        "你是天然气预测平台的统一智能助手。"
                        "当前请求不属于具体预测、训练或业务查询，不得自称短期客户、月度销量或冬季保供智能体。"
                        "语气应温暖、自然、可靠，避免使用生硬的通用客服套话。"
                        "用户问候时，先友好回应，再用一句话介绍自己是天然气预测平台的智能助手，"
                        "可以协助查询客户与业务信息、发起或查看模型训练和用气预测、跟踪任务进度；"
                        "最后自然询问用户今天想了解什么，整体控制在三句话以内。"
                        "如果用户询问“你是谁”或“你能做什么”，清楚介绍上述身份和能力边界。"
                        "不得声称已经查询数据、调用工具、训练模型或生成预测。"
                    ),
                },
                *[
                    {"role": item["role"], "content": item["content"]}
                    for item in (history or [])[-12:]
                    if item.get("role") in {"user", "assistant"} and item.get("content")
                ],
                {"role": "user", "content": message},
            ],
        )
        answer = (response.choices[0].message.content or "").strip()
        if not answer:
            raise RuntimeError(f"{capability.name}未获得大模型回答")
        return answer

    async def respond_with_result(
        self, message: str, capability: CapabilityDefinition, result: dict[str, Any]
    ) -> str:
        """只依据业务工具结果生成可读答复；模型不可用时使用确定性格式。"""
        fallback = self._format_result(result, message)
        if str(result.get("resultType") or "").upper() == "PAGE":
            return fallback
        if not self._model:
            return fallback
        compact = json.dumps(result, ensure_ascii=False, default=str)
        if len(compact) > 12000:
            compact = compact[:12000] + "…"
        try:
            response = await build_llm_client(self._settings).chat.completions.create(
                model=self._model,
                temperature=0.2,
                messages=[
                    {
                        "role": "system",
                        "content": (
                            "你是天然气预测平台的业务结果回复助手。只根据给定工具结果回答，不得编造、补齐或修改客户、编码、数量、状态和日期。"
                            "列表查询必须展示实际records中的名称，说明total和当前展示数量；分页未覆盖全部时不得声称已经列出全部。"
                            "status为WAITING_TRAINING时只能说明用户预测任务正在执行训练阶段；训练成功后同一任务会继续预测，不得声称预测已经完成或已有预测结果。"
                            "status为EXECUTING时说明任务仍在执行，不得使用已完成、预测完成等表达。"
                            "结果为空时明确说明没有查到。表达自然友好、简洁，可使用编号列表。不得暴露接口、SQL或内部实现。"
                        ),
                    },
                    {"role": "user", "content": json.dumps({"question": message, "capability": capability.code, "toolResultJson": compact}, ensure_ascii=False)},
                ],
            )
            answer = (response.choices[0].message.content or "").strip()
            return answer or fallback
        except Exception as exc:
            logger.warning("event=result_response_model_fallback capability=%s reason=%s", capability.code, exc)
            return fallback

    async def respond_for_status(self, facts: dict[str, Any], fallback: str) -> str:
        """把系统已经确定的流程状态改写成自然回复，不允许模型改变事实或决策。"""
        if not self._model:
            return fallback
        try:
            response = await build_llm_client(self._settings).chat.completions.create(
                model=self._model,
                temperature=0.3,
                messages=[
                    {
                        "role": "system",
                        "content": (
                            "你是天然气预测平台的对话回复助手。系统已经完成状态和安全决策，你只负责生成自然、友好、简洁的中文回复。"
                            "不得改变status，不得编造客户、数量、编码、日期、候选项或执行结果。"
                            "NEED_CONFIRMATION必须明确说明将执行什么并询问是否继续；NEED_INFORMATION说明缺少什么；"
                            "NEED_SELECTION完整保留候选信息；NOT_FOUND说明未找到并建议补充。"
                            "批量操作不得声称已经执行。只输出最终回复，不输出JSON、标题或分析过程，控制在180字以内。"
                        ),
                    },
                    {"role": "user", "content": json.dumps({"facts": facts, "safeFallback": fallback}, ensure_ascii=False, default=str)},
                ],
            )
            answer = (response.choices[0].message.content or "").strip()
            return answer or fallback
        except Exception as exc:
            logger.warning("event=status_response_model_fallback status=%s reason=%s", facts.get("status"), exc)
            return fallback

    @staticmethod
    def _format_result(result: dict[str, Any], message: str = "") -> str:
        data = result.get("data") if isinstance(result.get("data"), dict) else {}
        records = data.get("records") if isinstance(data.get("records"), list) else None
        if records is not None:
            total = int(data.get("total") or len(records))
            page = int(data.get("page") or 1)
            if not records:
                return "没有查询到符合条件的数据。"
            if re.search(r"(多少|数量|几家|几条|总数)", message) or re.search(r"只(告诉|返回|要).*(数量|总数)", message):
                return f"共查询到 {total} 条符合条件的数据。"
            lines = []
            for index, item in enumerate(records, 1):
                if not isinstance(item, dict):
                    lines.append(f"{index}. {item}")
                    continue
                name = next((item.get(key) for key in ("customerName", "regionName", "industryName", "name") if item.get(key)), None)
                code = next((item.get(key) for key in ("customerCode", "regionCode", "industryCode", "code") if item.get(key)), None)
                lines.append(f"{index}. {name or code or '未命名'}" + (f"（{code}）" if name and code else ""))
            all_fetched = data.get("allFetched") is True
            scope = f"共查询到 {total} 条，" + (f"已为你列出全部 {len(records)} 条：" if all_fetched else f"当前第 {page} 页展示 {len(records)} 条：")
            if total > len(records):
                scope += "（其余结果可继续分页查看）"
            return scope + "\n" + "\n".join(lines)
        return str(result.get("message") or "业务处理已完成。")
