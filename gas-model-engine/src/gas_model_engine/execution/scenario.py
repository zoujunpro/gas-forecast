from __future__ import annotations

import numpy as np
import pandas as pd

from ..algorithms.contracts import AlgorithmDataset, AlgorithmModel, PredictionResult
from ..backtesting import create_default_backtest_registry
from ..combinations import create_default_combination_registry
from ..engine import AlgorithmPredictRequest
from ..features import ConfiguredFeatureProcessor, FeaturePolicyResolver
from ..selection import create_default_selector_registry


class ForecastScenario:
    """统一预测流程的场景扩展点与底层组件集合。

    完整主流程由 ForecastPipeline 编排；具体预测场景继承本类，仅覆盖请求清洗、
    执行计划参数等差异钩子，不拥有另一套完整预测流程。
    """

    code = "DEFAULT_FORECAST_SCENARIO_V1"

    def __init__(
        self,
        feature_processor=None,
        backtest_registry=None,
        combination_registry=None,
        selector_registry=None,
        feature_policy_resolver=None,
    ) -> None:
        self.feature_processor = feature_processor or ConfiguredFeatureProcessor()
        self.backtest_registry = backtest_registry or create_default_backtest_registry()
        self.combination_registry = combination_registry or create_default_combination_registry()
        self.selector_registry = selector_registry or create_default_selector_registry()
        self.feature_policy_resolver = feature_policy_resolver or FeaturePolicyResolver()

    @staticmethod
    def _slice(data: AlgorithmDataset, start: int, end: int) -> AlgorithmDataset:
        return AlgorithmDataset(
            features=data.features.iloc[start:end].reset_index(drop=True),
            dates=data.dates[start:end],
            target=None
            if data.target is None
            else data.target.iloc[start:end].reset_index(drop=True),
            sample_weight=(
                None
                if data.sample_weight is None
                else data.sample_weight.iloc[start:end].reset_index(drop=True)
            ),
            metadata=dict(data.metadata),
        )

    def prepare_plan_params(self, request, profile) -> dict:
        """返回场景回测参数；子策略可覆盖，但不得修改统一请求契约。"""
        return dict(profile.backtest.params)

    @staticmethod
    def _with_oof_plan(params, plan):
        """把外层任务窗口注入残差组合，确保内部OOF模拟相同预测跨度。"""
        normalized = dict(params)
        if "oof_params" not in normalized:
            return normalized
        oof_params = dict(normalized["oof_params"])
        oof_params["validation_size"] = plan.validation_size
        oof_params["step_size"] = plan.step_size
        normalized["oof_params"] = oof_params
        return normalized

    @staticmethod
    def _with_calibrated_interval(prediction, radius, coverage):
        """使用Eval样本外误差为任意候选附加统一口径的预测区间。"""
        metadata = dict(prediction.metadata)
        metadata.update(
            {
                "interval_method": "OOF_ABSOLUTE_ERROR_CONFORMAL",
                "interval_coverage": coverage,
                "interval_radius": radius,
            }
        )
        return PredictionResult(
            prediction.values,
            prediction.values - radius,
            prediction.values + radius,
            metadata,
        )

    def prepare_request(self, request, profile):
        """场景数据清洗扩展点；公共模板默认不修改请求。"""
        return request

    def _resolve_features(self, request, profile):
        """解析场景 Policy；未配置 Policy 的自定义 Profile 继续使用内嵌特征。"""
        if not profile.feature_policy_code:
            return profile.features, {
                "policy_code": None,
                "selected_features": [spec.code for spec in profile.features],
                "skipped_features": [],
            }
        metadata = {**request.future_data.metadata, **request.train_data.metadata}
        plan = self.feature_policy_resolver.resolve(
            profile.feature_policy_code,
            metadata,
            request.train_data.features.columns,
            request.future_data.features.columns,
        )
        return plan.specs, {
            "policy_code": plan.policy_code,
            "policy_name": plan.policy_name,
            "target_type": plan.target_type,
            "selected_features": list(plan.selected_features),
            "skipped_features": list(plan.skipped_features),
        }

    @staticmethod
    def _feature_warmup(specs) -> int:
        """返回目标滞后类特征所需的最大历史长度。"""
        warmup = 0
        for spec in specs:
            params = dict(spec.params)
            if spec.code == "LAG_V1":
                warmup = max(warmup, *(int(value) for value in params.get("lags", [1, 7, 14])))
            elif spec.code == "ROLLING_V1":
                warmup = max(
                    warmup,
                    *(int(value) for value in params.get("windows", [3, 7, 14])),
                )
            elif spec.code == "TARGET_CHANGE_V1":
                warmup = max(
                    warmup,
                    *(int(value) + 1 for value in params.get("periods", [1, 7, 28])),
                )
        return warmup

    def _predict_range(
        self,
        engine,
        model: AlgorithmModel,
        raw_history: AlgorithmDataset,
        raw_future: AlgorithmDataset,
        prepared_future: AlgorithmDataset,
        feature_specs,
    ) -> PredictionResult:
        """预测一个完整区间；目标衍生特征存在时按天递归更新。"""
        if not self._uses_target_features(model):
            return engine.predict(AlgorithmPredictRequest(model, prepared_future))

        history = raw_history
        values: list[float] = []
        lowers: list[float] = []
        uppers: list[float] = []
        has_interval = True
        for index in range(len(raw_future.features)):
            raw_one = self._slice(raw_future, index, index + 1)
            _, prepared_one = self.feature_processor.prepare(history, raw_one, feature_specs)
            result = engine.predict(AlgorithmPredictRequest(model, prepared_one))
            value = float(result.values[0])
            values.append(value)
            if result.lower_bounds is None or result.upper_bounds is None:
                has_interval = False
            else:
                lowers.append(float(result.lower_bounds[0]))
                uppers.append(float(result.upper_bounds[0]))
            history = self._append_prediction(history, raw_one, value)
        return PredictionResult(
            np.asarray(values),
            np.asarray(lowers) if has_interval else None,
            np.asarray(uppers) if has_interval else None,
            {"prediction_mode": "recursive"},
        )

    @classmethod
    def _uses_target_features(cls, model: AlgorithmModel) -> bool:
        if any(name.startswith("target_") for name in model.feature_names):
            return True
        member_models = getattr(model.payload, "member_models", None)
        return bool(member_models) and any(
            cls._uses_target_features(member) for member in member_models.values()
        )

    @staticmethod
    def _append_prediction(
        history: AlgorithmDataset, future_row: AlgorithmDataset, prediction: float
    ) -> AlgorithmDataset:
        features = history.features.copy()
        appended = future_row.features.reindex(columns=features.columns)
        features = pd.concat([features, appended], ignore_index=True)
        target = pd.concat([history.target, pd.Series([prediction])], ignore_index=True)
        return AlgorithmDataset(
            features=features,
            dates=history.dates.append(future_row.dates),
            target=target,
            metadata=dict(history.metadata),
        )


class ForecastScenarioRegistry:
    """根据场景编码查找数据准备和参数钩子。"""

    def __init__(self, scenarios=()) -> None:
        self._scenarios = {}
        for scenario in scenarios:
            self.register(scenario)

    def register(self, scenario: ForecastScenario) -> None:
        if scenario.code in self._scenarios:
            raise ValueError(f"预测场景已注册: {scenario.code}")
        self._scenarios[scenario.code] = scenario

    def get(self, code: str) -> ForecastScenario:
        try:
            return self._scenarios[code]
        except KeyError as exc:
            raise KeyError(f"预测场景未注册: {code}") from exc


def create_default_scenario_registry() -> ForecastScenarioRegistry:
    """注册统一流程可使用的默认场景钩子。"""
    from .scenarios import (
        LegacyShortTermResidualScenario,
        MonthlyIndustryScenario,
        ShortTermCustomerScenario,
        WinterSupplyScenario,
    )

    return ForecastScenarioRegistry(
        [
            ForecastScenario(),
            ShortTermCustomerScenario(),
            WinterSupplyScenario(),
            LegacyShortTermResidualScenario(),
            MonthlyIndustryScenario(),
        ]
    )
