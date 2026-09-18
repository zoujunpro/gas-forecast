import json
from dataclasses import dataclass
from datetime import date
from pathlib import Path
from typing import Any


@dataclass(frozen=True)
class WinterSupplyResult:
    province: str
    model_name: str
    metrics: dict[str, Any]
    raw: dict[str, Any]


class WinterSupplyResultStore:
    """Loads migrated winter-supply result files.

    These JSON files are the current winter-supply model outputs migrated from
    the Java service resources. They contain the selected model, backtest
    metrics, backtest points, and future prediction points per province.
    """

    def __init__(self, data_dir: Path | None = None) -> None:
        self.data_dir = data_dir or self._default_data_dir()

    def list_results(self) -> list[WinterSupplyResult]:
        return [self._load_file(path) for path in sorted(self.data_dir.glob("*.json"))]

    def get_by_context(self, region_code: str | None, region_name: str | None, params: dict[str, Any]) -> WinterSupplyResult:
        province = params.get("province") or params.get("province_name") or region_name or region_code
        results = self.list_results()
        if not results:
            raise KeyError("winter-supply result data not found")

        if province:
            for result in results:
                if result.province == province:
                    return result
            raise KeyError(f"winter-supply result data not found for province: {province}")

        return min(results, key=lambda result: float(result.metrics.get("mape", float("inf"))))

    def _load_file(self, path: Path) -> WinterSupplyResult:
        payload = json.loads(path.read_text(encoding="utf-8"))
        return WinterSupplyResult(
            province=payload.get("province") or path.stem,
            model_name=payload.get("model_name") or "unknown",
            metrics=payload.get("metrics") or {},
            raw=payload,
        )

    def _default_data_dir(self) -> Path:
        return Path(__file__).resolve().parents[2] / "resources" / "winter_supply_data"


def parse_date(value: Any) -> date:
    if isinstance(value, date):
        return value
    return date.fromisoformat(str(value)[:10])

