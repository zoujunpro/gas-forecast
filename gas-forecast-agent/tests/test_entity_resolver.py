import asyncio
from datetime import date
from typing import Any

from gas_forecast_agent.capabilities import CapabilityRegistry
from gas_forecast_agent.entities import EntityResolutionStatus, EntityResolver


class FakeEntityMcp:
    def __init__(self, responses: dict[str, list[dict[str, Any]]]) -> None:
        self.responses = {key: list(value) for key, value in responses.items()}
        self.calls: list[tuple[str, dict[str, Any]]] = []

    async def call_tool(self, name: str, arguments: dict[str, Any]) -> dict[str, Any]:
        self.calls.append((name, arguments))
        queue = self.responses.get(name)
        if not queue:
            raise AssertionError(f"unexpected tool: {name}")
        data = queue.pop(0)
        return {"isError": False, "structuredContent": {"success": True, "message": "ok", "data": data}}


def resolve(capability_code: str, parameters: dict[str, Any], mcp: FakeEntityMcp):
    capability = CapabilityRegistry().get(capability_code)
    resolver = EntityResolver(today=lambda: date(2026, 10, 15))
    return asyncio.run(resolver.resolve(capability, parameters, mcp))


def test_customer_unique_match_writes_standard_codes() -> None:
    mcp = FakeEntityMcp({
        "customer_search": [{
            "status": "UNIQUE",
            "candidates": [{
                "type": "CUSTOMER", "code": "C001", "name": "杭州燃气集团",
                "regionCode": "ZJ", "regionName": "浙江", "industryCode": "CITY_GAS", "industryName": "城燃",
            }],
        }]
    })
    outcome = resolve("SHORT_TERM_FORECAST_LATEST", {"customerName": "杭州燃气"}, mcp)
    assert outcome.status == EntityResolutionStatus.RESOLVED
    assert outcome.parameters["customerCode"] == "C001"
    assert outcome.parameters["regionCode"] == "ZJ"
    assert outcome.resolved_entities["customer"]["name"] == "杭州燃气集团"


def test_region_typo_resolves_when_best_candidate_is_clearly_ahead() -> None:
    mcp = FakeEntityMcp({
        "region_search": [{"status": "NOT_FOUND", "candidates": []}],
        "region_list": [{"records": [
            {"regionCode": "ZJ", "regionName": "浙江"},
            {"regionCode": "JS", "regionName": "江苏"},
        ]}],
    })
    outcome = resolve("CUSTOMER_LIST", {"regionName": "浙将省"}, mcp)
    assert outcome.status == EntityResolutionStatus.RESOLVED
    assert outcome.parameters["regionCode"] == "ZJ"


def test_customer_fallback_is_limited_by_resolved_scope() -> None:
    mcp = FakeEntityMcp({
        "customer_search": [{"status": "NOT_FOUND", "candidates": []}],
        "customer_list": [{"records": [{"customerCode": "C001", "customerName": "杭州燃气集团"}]}],
    })
    outcome = resolve(
        "SHORT_TERM_FORECAST_LATEST",
        {"customerName": "杭洲燃气", "regionCode": "ZJ", "regionName": "浙江", "industryCode": "CITY_GAS", "industryName": "城燃"},
        mcp,
    )
    assert outcome.status == EntityResolutionStatus.RESOLVED
    assert mcp.calls[-1][1]["regionCode"] == "ZJ"
    assert mcp.calls[-1][1]["industryCode"] == "CITY_GAS"


def test_similar_customer_candidates_require_user_selection() -> None:
    mcp = FakeEntityMcp({
        "customer_search": [{"status": "MULTIPLE", "candidates": [
            {"code": "C001", "name": "杭州燃气一公司"},
            {"code": "C002", "name": "杭州燃气二公司"},
        ]}],
    })
    outcome = resolve("SHORT_TERM_FORECAST_LATEST", {"customerName": "杭州燃气"}, mcp)
    assert outcome.status == EntityResolutionStatus.NEED_SELECTION
    assert [item["code"] for item in outcome.candidates] == ["C001", "C002"]


def test_history_relative_time_is_normalized_by_business_purpose() -> None:
    mcp = FakeEntityMcp({
        "customer_search": [{"status": "UNIQUE", "candidates": [{"code": "C001", "name": "杭州燃气集团"}]}],
    })
    outcome = resolve(
        "SHORT_TERM_FORECAST_HISTORY",
        {"customerCode": "C001", "timeExpression": "最近30天"},
        mcp,
    )
    assert outcome.status == EntityResolutionStatus.RESOLVED
    assert outcome.parameters["startDate"] == "2026-09-16"
    assert outcome.parameters["endDate"] == "2026-10-15"


def test_forecast_next_week_is_resolved_from_shanghai_business_date() -> None:
    mcp = FakeEntityMcp({"customer_search": [{"status": "UNIQUE", "candidates": [{"code": "C001", "name": "测试客户"}]}]})
    outcome = resolve(
        "SHORT_TERM_FORECAST_START",
        {"conversationNo": "C-1", "customerCode": "C001", "timeExpression": "下周"},
        mcp,
    )
    assert outcome.status == EntityResolutionStatus.RESOLVED
    assert outcome.parameters["forecastStartDate"] == "2026-10-19"
    assert outcome.parameters["forecastEndDate"] == "2026-10-25"
    assert outcome.parameters["forecastHorizon"] == 7


def test_forecast_future_two_weeks_is_resolved_deterministically() -> None:
    mcp = FakeEntityMcp({"customer_search": [{"status": "UNIQUE", "candidates": [{"code": "C001", "name": "测试客户"}]}]})
    outcome = resolve(
        "SHORT_TERM_FORECAST_START",
        {"conversationNo": "C-1", "customerCode": "C001", "timeExpression": "未来两周"},
        mcp,
    )
    assert outcome.parameters["forecastStartDate"] == "2026-10-16"
    assert outcome.parameters["forecastEndDate"] == "2026-10-29"
    assert outcome.parameters["forecastHorizon"] == 14


def test_single_customer_start_requires_resolved_customer() -> None:
    mcp = FakeEntityMcp({})
    outcome = resolve("SHORT_TERM_FORECAST_START", {"conversationNo": "C-1", "bulk": False}, mcp)
    assert outcome.status == EntityResolutionStatus.NEED_INFORMATION
    assert "客户" in outcome.message
