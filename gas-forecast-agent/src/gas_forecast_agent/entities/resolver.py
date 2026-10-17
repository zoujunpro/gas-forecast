"""地区、行业、客户和业务时间范围的标准化解析。"""

from __future__ import annotations

import re
import calendar
from datetime import date, timedelta
from difflib import SequenceMatcher
from typing import Any, Callable

from gas_forecast_agent.capabilities import CapabilityDefinition
from gas_forecast_agent.mcp import McpClientError, StreamableHttpMcpClient

from .models import EntityResolutionOutcome, EntityResolutionStatus


class EntityResolver:
    """按能力需要解析实体；不会把候选列表交给大模型选择。"""

    def __init__(self, today: Callable[[], date] = date.today) -> None:
        self._today = today

    async def resolve(
        self,
        capability: CapabilityDefinition,
        parameters: dict[str, Any],
        client: StreamableHttpMcpClient,
    ) -> EntityResolutionOutcome:
        values = dict(parameters)
        resolved: dict[str, dict[str, Any]] = {}
        time_error = self._resolve_time(capability, values)
        if time_error:
            return EntityResolutionOutcome(status=EntityResolutionStatus.INVALID_TIME, parameters=values, message=time_error)

        for entity_type in ("REGION", "INDUSTRY"):
            outcome = await self._resolve_named(entity_type, values, client)
            if outcome:
                if outcome.status != EntityResolutionStatus.RESOLVED:
                    outcome.parameters = values
                    return outcome
                values.update(outcome.parameters)
                resolved.update(outcome.resolved_entities)

        if capability.code != "CUSTOMER_LIST":
            outcome = await self._resolve_named("CUSTOMER", values, client)
            if outcome:
                if outcome.status != EntityResolutionStatus.RESOLVED:
                    outcome.parameters = values
                    return outcome
                values.update(outcome.parameters)
                resolved.update(outcome.resolved_entities)

        missing = self._business_missing(capability, values)
        if missing:
            return EntityResolutionOutcome(
                status=EntityResolutionStatus.NEED_INFORMATION,
                parameters=values,
                resolved_entities=resolved,
                message=missing,
            )
        return EntityResolutionOutcome(status=EntityResolutionStatus.RESOLVED, parameters=values, resolved_entities=resolved)

    async def _resolve_named(
        self,
        entity_type: str,
        values: dict[str, Any],
        client: StreamableHttpMcpClient,
    ) -> EntityResolutionOutcome | None:
        prefix = entity_type.lower()
        code_key, name_key = f"{prefix}Code", f"{prefix}Name"
        code, raw_name = values.get(code_key), values.get(name_key)
        if code and raw_name:
            return None
        if not code and not raw_name:
            return None

        arguments = {code_key: code, "limit": 20} if code else {name_key: raw_name, "limit": 20}
        for scope in ("regionCode", "industryCode"):
            if entity_type == "CUSTOMER" and values.get(scope):
                arguments[scope] = values[scope]
        data = await self._tool(client, f"{prefix}_search", arguments)
        candidates = list(data.get("candidates") or [])
        if data.get("status") == "UNIQUE" and candidates:
            return self._resolved(entity_type, str(raw_name or code), candidates[0], "SEARCH")
        if code:
            return EntityResolutionOutcome(
                status=EntityResolutionStatus.NOT_FOUND,
                entity_type=entity_type,
                raw_text=str(code),
                message=f"没有找到编码为“{code}”的{self._label(entity_type)}",
            )
        if not candidates:
            candidates = await self._fallback_candidates(entity_type, values, client)
        return self._rank(entity_type, str(raw_name), candidates)

    async def _fallback_candidates(self, entity_type: str, values: dict[str, Any], client: StreamableHttpMcpClient) -> list[dict[str, Any]]:
        if entity_type == "REGION":
            data = await self._tool(client, "region_list", {"pageNo": 1, "pageSize": 100})
        elif entity_type == "INDUSTRY":
            data = await self._tool(client, "industry_list", {"pageNo": 1, "pageSize": 100})
        else:
            arguments = {"pageNo": 1, "pageSize": 100}
            if values.get("regionCode"):
                arguments["regionCode"] = values["regionCode"]
            if values.get("industryCode"):
                arguments["industryCode"] = values["industryCode"]
            data = await self._tool(client, "customer_list", arguments)
        return list(data.get("records") or [])

    def _rank(self, entity_type: str, raw_name: str, candidates: list[dict[str, Any]]) -> EntityResolutionOutcome:
        name_key = f"{entity_type.lower()}Name"
        ranked = []
        for candidate in candidates:
            name = candidate.get("name") or candidate.get(name_key)
            if not name:
                continue
            similarity = self._similarity(raw_name, str(name), entity_type)
            ranked.append({**candidate, "similarity": round(similarity, 4)})
        ranked.sort(key=lambda item: item["similarity"], reverse=True)
        if not ranked or ranked[0]["similarity"] < self._minimum(entity_type, raw_name):
            return EntityResolutionOutcome(
                status=EntityResolutionStatus.NOT_FOUND,
                entity_type=entity_type,
                raw_text=raw_name,
                candidates=ranked[:5],
                message=f"没有找到与“{raw_name}”可靠匹配的{self._label(entity_type)}",
            )
        first, second = ranked[0], ranked[1] if len(ranked) > 1 else None
        if first["similarity"] >= self._automatic(entity_type, raw_name) and (second is None or first["similarity"] - second["similarity"] >= 0.1):
            return self._resolved(entity_type, raw_name, first, "FUZZY")
        return EntityResolutionOutcome(
            status=EntityResolutionStatus.NEED_SELECTION,
            entity_type=entity_type,
            raw_text=raw_name,
            candidates=ranked[:5],
            message=f"找到多个可能的{self._label(entity_type)}，请用户选择",
        )

    @staticmethod
    def _resolved(entity_type: str, raw_name: str, candidate: dict[str, Any], match_type: str) -> EntityResolutionOutcome:
        prefix = entity_type.lower()
        code = candidate.get("code") or candidate.get(f"{prefix}Code")
        name = candidate.get("name") or candidate.get(f"{prefix}Name")
        parameters = {f"{prefix}Code": code, f"{prefix}Name": name}
        if entity_type == "CUSTOMER":
            for key in ("regionCode", "regionName", "industryCode", "industryName"):
                if candidate.get(key):
                    parameters[key] = candidate[key]
        return EntityResolutionOutcome(
            status=EntityResolutionStatus.RESOLVED,
            parameters=parameters,
            resolved_entities={prefix: {"rawText": raw_name, "code": code, "name": name, "matchType": match_type}},
        )

    def _resolve_time(self, capability: CapabilityDefinition, values: dict[str, Any]) -> str | None:
        expression = str(values.get("timeExpression") or "").strip()
        if expression:
            dates = re.findall(r"20\d{2}[-/.年]\d{1,2}[-/.月]\d{1,2}", expression)
            if len(dates) >= 2:
                start, end = self._date(dates[0]), self._date(dates[1])
                if start > end:
                    return "开始日期不能晚于结束日期"
                if capability.intent.domain == "TRAINING" and capability.intent.action == "START":
                    values.update({"trainMode": "RANGE", "trainingStartDate": start.isoformat(), "trainingEndDate": end.isoformat()})
                elif capability.intent.domain == "FORECAST" and capability.intent.action == "START":
                    values.update({"forecastStartDate": start.isoformat(), "forecastEndDate": end.isoformat()})
                else:
                    values.update({"startDate": start.isoformat(), "endDate": end.isoformat()})
            relative = re.search(r"(?:最近|近|过去)(\d{1,4})(天|日|个月|月)", expression.replace(" ", ""))
            if relative and capability.intent.action == "HISTORY":
                amount = int(relative.group(1))
                days = amount * 30 if "月" in relative.group(2) else amount
                end = self._today()
                values.update({"startDate": (end - timedelta(days=max(0, days - 1))).isoformat(), "endDate": end.isoformat()})
            if not dates:
                resolved = self._relative_range(expression)
                if resolved:
                    start, end = resolved
                    if capability.intent.domain == "TRAINING" and capability.intent.action == "START":
                        values.update({"trainMode": "RANGE", "trainingStartDate": start.isoformat(), "trainingEndDate": end.isoformat()})
                    elif capability.intent.domain == "FORECAST" and capability.intent.action == "START":
                        values.update({"forecastStartDate": start.isoformat(), "forecastEndDate": end.isoformat(),
                                       "forecastHorizon": (end - start).days + 1})
                    else:
                        values.update({"startDate": start.isoformat(), "endDate": end.isoformat()})
        for start_key, end_key in (("trainingStartDate", "trainingEndDate"), ("forecastStartDate", "forecastEndDate"), ("startDate", "endDate")):
            if values.get(start_key) and values.get(end_key) and self._date(str(values[start_key])) > self._date(str(values[end_key])):
                return f"{start_key}不能晚于{end_key}"
        return None

    def _relative_range(self, expression: str) -> tuple[date, date] | None:
        text = re.sub(r"\s+", "", expression)
        today = self._today()
        if text in {"今天"}: return today, today
        if text == "明天": return today + timedelta(days=1), today + timedelta(days=1)
        if text == "后天": return today + timedelta(days=2), today + timedelta(days=2)
        if text == "昨天": return today - timedelta(days=1), today - timedelta(days=1)
        if text in {"本周", "这周"}:
            start = today - timedelta(days=today.weekday())
            return start, start + timedelta(days=6)
        if text == "下周":
            start = today + timedelta(days=7 - today.weekday())
            return start, start + timedelta(days=6)
        if text in {"本月", "这个月"}:
            return date(today.year, today.month, 1), date(today.year, today.month, calendar.monthrange(today.year, today.month)[1])
        if text == "下个月":
            year, month = (today.year + 1, 1) if today.month == 12 else (today.year, today.month + 1)
            return date(year, month, 1), date(year, month, calendar.monthrange(year, month)[1])
        if text in {"月底", "月末"}:
            end = date(today.year, today.month, calendar.monthrange(today.year, today.month)[1])
            return end, end
        match = re.fullmatch(r"未来([一二两三四五六七八九十\d]+)(天|日|周|个月|月)", text)
        if match:
            amount = self._number(match.group(1))
            days = amount * (7 if match.group(2) == "周" else 30 if "月" in match.group(2) else 1)
            start = today + timedelta(days=1)
            return start, start + timedelta(days=max(0, days - 1))
        match = re.fullmatch(r"最近(一个|[一二两三四五六七八九十\d]+)(周|个月|月)", text)
        if match:
            amount = 1 if match.group(1) == "一个" else self._number(match.group(1))
            days = amount * (7 if match.group(2) == "周" else 30)
            return today - timedelta(days=max(0, days - 1)), today
        return None

    @staticmethod
    def _number(value: str) -> int:
        if value.isdigit(): return int(value)
        digits = {"一": 1, "二": 2, "两": 2, "三": 3, "四": 4, "五": 5, "六": 6, "七": 7, "八": 8, "九": 9, "十": 10}
        if value.startswith("十"): return 10 + digits.get(value[1:], 0)
        if "十" in value:
            left, right = value.split("十", 1)
            return digits.get(left, 1) * 10 + digits.get(right, 0)
        return digits.get(value, 0)

    @staticmethod
    def _business_missing(capability: CapabilityDefinition, values: dict[str, Any]) -> str | None:
        if capability.code == "CUSTOMER_DETAIL" and not values.get("customerCode"):
            return "请提供需要查询的客户名称或客户编码"
        if capability.code.startswith("SHORT_TERM_") and capability.intent.action in {"LATEST", "HISTORY"} and not values.get("customerCode"):
            return "请提供客户名称或客户编码"
        if capability.code in {"SHORT_TERM_TRAINING_START", "SHORT_TERM_FORECAST_START"}:
            if values.get("bulk"):
                if not any(values.get(key) for key in ("regionCode", "industryCode", "customerCode")):
                    return "批量任务需要提供地区、行业或客户范围"
            elif not values.get("customerCode"):
                return "单客户任务需要提供客户名称或客户编码"
        return None

    async def _tool(self, client: StreamableHttpMcpClient, name: str, arguments: dict[str, Any]) -> dict[str, Any]:
        try:
            raw = await client.call_tool(name, arguments)
        except McpClientError as exc:
            raise RuntimeError(str(exc)) from exc
        structured = raw.get("structuredContent") or raw.get("structured_content")
        if not isinstance(structured, dict):
            raise RuntimeError(f"MCP工具{name}未返回structuredContent")
        if raw.get("isError") or structured.get("success") is False:
            raise RuntimeError(structured.get("message") or f"MCP工具{name}调用失败")
        data = structured.get("data")
        return data if isinstance(data, dict) else {}

    @staticmethod
    def _normalize(value: str, entity_type: str) -> str:
        text = re.sub(r"\s+", "", value).lower()
        if entity_type == "REGION":
            text = re.sub(r"(特别行政区|壮族自治区|回族自治区|维吾尔自治区|自治区|省|市|地区)$", "", text)
        elif entity_type == "CUSTOMER":
            text = re.sub(r"(有限责任公司|股份有限公司|有限公司|集团|公司)$", "", text)
        return text

    def _similarity(self, source: str, target: str, entity_type: str) -> float:
        left, right = self._normalize(source, entity_type), self._normalize(target, entity_type)
        if left == right:
            return 1.0
        if left in right or right in left:
            return max(0.85, min(len(left), len(right)) / max(len(left), len(right)))
        return SequenceMatcher(None, left, right).ratio()

    @staticmethod
    def _minimum(entity_type: str, raw_name: str) -> float:
        return 0.45 if entity_type in {"REGION", "INDUSTRY"} and len(raw_name) <= 3 else 0.55

    @staticmethod
    def _automatic(entity_type: str, raw_name: str) -> float:
        return 0.5 if entity_type in {"REGION", "INDUSTRY"} and len(raw_name) <= 3 else 0.72

    @staticmethod
    def _label(entity_type: str) -> str:
        return {"REGION": "地区", "INDUSTRY": "行业", "CUSTOMER": "客户"}[entity_type]

    @staticmethod
    def _date(value: str) -> date:
        normalized = value.replace("年", "-").replace("月", "-").replace("日", "").replace("/", ".").replace(".", "-")
        parts = normalized.split("-")
        return date(int(parts[0]), int(parts[1]), int(parts[2]))
