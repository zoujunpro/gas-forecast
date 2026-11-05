from __future__ import annotations

import numpy as np

from ..algorithms.contracts import PredictionResult
from ..engine import AlgorithmEngine, AlgorithmPredictRequest, AlgorithmTrainRequest
from ..profiles import AggregationMode, AlgorithmProfile, AlgorithmProfileRegistry
from .contracts import (
    ProfileModelBundle,
    ProfilePredictionResult,
    ProfilePredictRequest,
    ProfileTrainRequest,
)


class AlgorithmProfileExecutor:
    """执行算法方案；不承担完整智能体的业务编排职责。"""

    def __init__(
        self,
        algorithm_engine: AlgorithmEngine,
        profile_registry: AlgorithmProfileRegistry,
    ) -> None:
        self.algorithm_engine = algorithm_engine
        self.profile_registry = profile_registry

    def train(self, request: ProfileTrainRequest) -> ProfileModelBundle:
        profile = self.profile_registry.get(request.profile_code)
        selected_ids = set(
            request.candidate_ids or (candidate.id for candidate in profile.candidates)
        )
        available_ids = {candidate.id for candidate in profile.candidates}
        unknown_ids = selected_ids - available_ids
        if unknown_ids:
            raise ValueError(f"算法方案不存在候选标识: {', '.join(sorted(unknown_ids))}")
        if not selected_ids:
            raise ValueError("至少需要选择一个候选算法")
        models = {}
        failures = {}
        for candidate in profile.candidates:
            if candidate.id not in selected_ids:
                continue
            try:
                models[candidate.id] = self.algorithm_engine.train(
                    AlgorithmTrainRequest(
                        algorithm_code=candidate.algorithm_code,
                        dataset=request.train_data,
                        params=candidate.params,
                    )
                )
            except Exception as exc:
                failures[candidate.id] = str(exc)
                if candidate.required:
                    raise RuntimeError(f"必需候选算法训练失败: {candidate.id}: {exc}") from exc
        if not models:
            details = "; ".join(f"{key}: {value}" for key, value in failures.items())
            raise RuntimeError(f"算法方案{profile.code}没有成功训练的候选算法: {details}")
        return ProfileModelBundle(profile.code, profile.version, models, failures)

    def predict(self, request: ProfilePredictRequest) -> ProfilePredictionResult:
        profile = self.profile_registry.get(request.model_bundle.profile_code)
        if request.model_bundle.profile_version != profile.version:
            raise ValueError(
                f"模型包方案版本为{request.model_bundle.profile_version}，当前版本为{profile.version}"
            )
        predictions = {}
        failures = {}
        expected_size = len(request.future_data.features)
        for candidate_id, model in request.model_bundle.candidate_models.items():
            try:
                result = self.algorithm_engine.predict(
                    AlgorithmPredictRequest(model, request.future_data)
                )
                if len(result.values) != expected_size:
                    raise ValueError(f"预测点数量应为{expected_size}，实际为{len(result.values)}")
                if not np.isfinite(result.values).all():
                    raise ValueError("预测结果包含非有限数值")
                predictions[candidate_id] = result
            except Exception as exc:
                failures[candidate_id] = str(exc)
        if not predictions:
            details = "; ".join(f"{key}: {value}" for key, value in failures.items())
            raise RuntimeError(f"算法方案{profile.code}没有成功生成预测: {details}")
        return ProfilePredictionResult(
            profile_code=profile.code,
            predictions=predictions,
            final_prediction=self._aggregate(profile, predictions),
            failed_candidates=failures,
        )

    @staticmethod
    def _aggregate(
        profile: AlgorithmProfile,
        predictions: dict[str, PredictionResult],
    ) -> PredictionResult:
        matrix = np.stack([result.values for result in predictions.values()])
        if profile.aggregation == AggregationMode.MEDIAN:
            values = np.median(matrix, axis=0)
        elif profile.aggregation == AggregationMode.MEAN:
            values = matrix.mean(axis=0)
        else:
            raise ValueError(f"不支持的汇总方式: {profile.aggregation}")
        return PredictionResult(
            values=values,
            metadata={
                "aggregation": profile.aggregation.value.lower(),
                "members": list(predictions),
            },
        )
