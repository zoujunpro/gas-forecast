from datetime import date
from typing import Any

from gas_model_platform.schemas.modeling import (
    AgentCode,
    FeatureBatchComputeRequest,
    FeatureBatchComputeResult,
    FeatureComputeRequest,
    FeatureComputeResult,
)


class FeatureCalculator:
    """统一特征计算器。

    真实模型可以在这里继续拆出不同 agent 的特征工程实现。
    当前先提供所有模型都能复用的基础特征，并预留 input_row / history_dataset
    让 Java 后端可以把气象、销量、节假日等外部数据一并传进来。
    """

    def compute_one(self, request: FeatureComputeRequest) -> FeatureComputeResult:
        features = self._base_features(
            agent_code=request.agent_code,
            target_date=request.target_date,
            input_row=request.input_row,
            history_dataset=request.history_dataset,
            params=request.params,
        )
        features.update(self._dimension_features(request))
        return FeatureComputeResult(
            agent_code=request.agent_code,
            target_date=request.target_date,
            features=features,
            feature_names=sorted(features.keys()),
            metadata={
                "mode": "single",
                "history_size": len(request.history_dataset),
            },
        )

    def compute_batch(self, request: FeatureBatchComputeRequest) -> FeatureBatchComputeResult:
        results: list[FeatureComputeResult] = []
        rows_by_date = self._index_rows_by_date(request.input_rows)

        for target_date in request.target_dates:
            input_row = rows_by_date.get(target_date, {})
            single_request = FeatureComputeRequest(
                agent_code=request.agent_code,
                target_date=target_date,
                region_code=request.region_code,
                region_name=request.region_name,
                industry_code=request.industry_code,
                industry_name=request.industry_name,
                customer_code=request.customer_code,
                customer_name=request.customer_name,
                input_row=input_row,
                history_dataset=request.history_dataset,
                params=request.params,
            )
            results.append(self.compute_one(single_request))

        return FeatureBatchComputeResult(
            agent_code=request.agent_code,
            results=results,
            metadata={
                "mode": "batch",
                "target_date_count": len(request.target_dates),
                "history_size": len(request.history_dataset),
            },
        )

    def _base_features(
        self,
        agent_code: AgentCode,
        target_date: date,
        input_row: dict[str, Any],
        history_dataset: list[dict[str, Any]],
        params: dict[str, Any],
    ) -> dict[str, Any]:
        features: dict[str, Any] = {}
        if params.get("include_input_fields", True):
            features.update(input_row)

        features.update(self._calendar_features(target_date))
        features.update(self._weather_features(input_row, params))
        features.update(self._lag_features(target_date, history_dataset, params))

        if agent_code == "winter-supply":
            features.update(self._winter_supply_features(target_date, input_row))
        elif agent_code == "monthly-sales":
            features.update(self._monthly_sales_features(target_date))
        elif agent_code == "short-term":
            features.update(self._short_term_features(target_date))

        return features

    def _dimension_features(self, request: FeatureComputeRequest) -> dict[str, Any]:
        return {
            "region_code": request.region_code,
            "region_name": request.region_name,
            "industry_code": request.industry_code,
            "industry_name": request.industry_name,
            "customer_code": request.customer_code,
            "customer_name": request.customer_name,
        }

    def _calendar_features(self, target_date: date) -> dict[str, Any]:
        return {
            "year": target_date.year,
            "month": target_date.month,
            "day": target_date.day,
            "quarter": (target_date.month - 1) // 3 + 1,
            "day_of_week": target_date.weekday(),
            "day_of_year": target_date.timetuple().tm_yday,
            "is_weekend": target_date.weekday() >= 5,
            "is_month_start": target_date.day == 1,
        }

    def _winter_supply_features(self, target_date: date, input_row: dict[str, Any]) -> dict[str, Any]:
        tenday = min((target_date.day - 1) // 10 + 1, 3)
        heating_season = target_date.month in {11, 12, 1, 2, 3}
        return {
            "tenday": tenday,
            "tenday_label": input_row.get("tenday_label") or f"{target_date.month}月第{tenday}旬",
            "slot_of_year": target_date.month * 3 + tenday,
            "heating_season": heating_season,
            "winter_peak": target_date.month in {12, 1, 2},
        }

    def _monthly_sales_features(self, target_date: date) -> dict[str, Any]:
        return {
            "month_index": target_date.year * 12 + target_date.month,
            "is_year_start": target_date.month == 1,
            "is_year_end": target_date.month == 12,
        }

    def _short_term_features(self, target_date: date) -> dict[str, Any]:
        return {
            "is_workday": target_date.weekday() < 5,
            "week_of_year": target_date.isocalendar().week,
        }

    def _weather_features(self, input_row: dict[str, Any], params: dict[str, Any]) -> dict[str, Any]:
        base_temperature = float(params.get("hdd_base_temperature", 18))
        avg_temp = self._number(input_row, "avg_temp", "avgTemp")
        max_temp = self._number(input_row, "max_temp", "maxTemp")
        min_temp = self._number(input_row, "min_temp", "minTemp")

        features: dict[str, Any] = {}
        if avg_temp is not None:
            features["avg_temp"] = avg_temp
            features["hdd"] = max(base_temperature - avg_temp, 0)
            features["avg_temp_sq"] = avg_temp**2
        if max_temp is not None and min_temp is not None:
            features["temp_range"] = max_temp - min_temp
        return features

    def _lag_features(
        self,
        target_date: date,
        history_dataset: list[dict[str, Any]],
        params: dict[str, Any],
    ) -> dict[str, Any]:
        lag_days = params.get("lag_days", [1, 7, 30])
        indexed = self._index_rows_by_date(history_dataset)
        features: dict[str, Any] = {}
        for lag_day in lag_days:
            try:
                lag_day_int = int(lag_day)
            except (TypeError, ValueError):
                continue
            lag_date = date.fromordinal(target_date.toordinal() - lag_day_int)
            lag_row = indexed.get(lag_date)
            if not lag_row:
                continue
            lag_value = self._number(lag_row, "gas_sales", "gasSales", "value")
            if lag_value is not None:
                features[f"lag_{lag_day_int}"] = lag_value
        return features

    def _index_rows_by_date(self, rows: list[dict[str, Any]]) -> dict[date, dict[str, Any]]:
        indexed: dict[date, dict[str, Any]] = {}
        for row in rows:
            row_date = self._row_date(row)
            if row_date is not None:
                indexed[row_date] = row
        return indexed

    def _row_date(self, row: dict[str, Any]) -> date | None:
        value = row.get("stat_date", row.get("statDate", row.get("forecast_date")))
        if isinstance(value, date):
            return value
        if isinstance(value, str):
            return date.fromisoformat(value[:10])
        return None

    def _number(self, row: dict[str, Any], *keys: str) -> float | None:
        for key in keys:
            value = row.get(key)
            if value is not None:
                return float(value)
        return None

