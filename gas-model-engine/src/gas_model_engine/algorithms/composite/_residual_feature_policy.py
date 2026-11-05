from __future__ import annotations

from abc import ABC, abstractmethod

import numpy as np

from ..contracts import AlgorithmDataset, AlgorithmModel


class ResidualFeaturePolicy(ABC):
    """残差组合的特征和未来推演策略；组合算法本身不感知行业特征名。"""

    code: str

    def prepare_training(self, residual_data, full_target, source_indices):
        """整理残差训练特征；默认直接使用特征流水线提供的字段。"""
        return residual_data

    @abstractmethod
    def predict(
        self,
        composite,
        model: AlgorithmModel,
        future_data: AlgorithmDataset,
        base_values,
        target_history,
        correction_bounds,
    ):
        """生成未来残差预测。"""
        raise NotImplementedError


class PassthroughResidualFeaturePolicy(ResidualFeaturePolicy):
    """直接特征策略：不增加目标历史特征，也不执行递归预测。"""

    code = "PASSTHROUGH_V1"

    def predict(
        self,
        composite,
        model,
        future_data,
        base_values,
        target_history,
        correction_bounds,
    ):
        return composite._prediction_on(model.algorithm_code, model, future_data)


class TargetHistoryResidualFeaturePolicy(ResidualFeaturePolicy):
    """目标历史策略：构造Lag/Rolling，并用安全修正后的预测逐日递推。"""

    code = "TARGET_HISTORY_V1"
    lags = (1, 7, 14)
    rolling_window = 7

    def prepare_training(self, residual_data, full_target, source_indices):
        target = np.asarray(full_target, dtype=float)
        indices = np.asarray(source_indices, dtype=int)
        frame = residual_data.features.copy()
        for lag in self.lags:
            frame[f"target_lag_{lag}"] = [
                target[index - lag] if index >= lag else np.nan for index in indices
            ]
        histories = [target[max(0, index - self.rolling_window) : index] for index in indices]
        frame["target_rolling_mean_7"] = [
            float(np.mean(values)) if len(values) else np.nan for values in histories
        ]
        frame["target_rolling_std_7"] = [
            float(np.std(values)) if len(values) > 1 else 0.0 for values in histories
        ]
        frame["target_diff_1"] = frame["target_lag_1"] - [
            target[index - 2] if index >= 2 else np.nan for index in indices
        ]
        frame["target_diff_7"] = frame["target_lag_1"] - frame["target_lag_7"]
        residual_data.features = frame
        residual_data.metadata["residual_feature_policy"] = self.code
        residual_data.metadata["residual_target_features"] = [
            name for name in frame if name.startswith("target_")
        ]
        return residual_data

    def predict(
        self,
        composite,
        model,
        future_data,
        base_values,
        target_history,
        correction_bounds,
    ):
        if model.algorithm_code not in {"XGBOOST_V1", "LIGHTGBM_V1"}:
            return composite._prediction_on(model.algorithm_code, model, future_data)
        if target_history is None:
            raise ValueError("目标历史递归策略需要训练期target_history")
        history = list(np.asarray(target_history, dtype=float))
        predictions = []
        for index in range(len(future_data.features)):
            frame = future_data.features.iloc[[index]].copy().reset_index(drop=True)
            for lag in self.lags:
                frame[f"target_lag_{lag}"] = history[-lag] if len(history) >= lag else np.nan
            recent = history[-self.rolling_window :]
            frame["target_rolling_mean_7"] = float(np.mean(recent))
            frame["target_rolling_std_7"] = float(np.std(recent)) if len(recent) > 1 else 0.0
            frame["target_diff_1"] = history[-1] - history[-2] if len(history) >= 2 else 0.0
            frame["target_diff_7"] = history[-1] - history[-7] if len(history) >= 7 else 0.0
            row = AlgorithmDataset(frame, future_data.dates[index : index + 1])
            correction = float(composite._prediction_on(model.algorithm_code, model, row)[0])
            # 递归历史只能写入经过安全边界处理的结果，否则单日异常会污染后续日期。
            if correction_bounds is not None:
                correction = float(np.clip(correction, *correction_bounds))
            predictions.append(correction)
            history.append(float(base_values[index]) + correction)
        return np.asarray(predictions)


_POLICIES = {
    policy.code: policy
    for policy in (
        PassthroughResidualFeaturePolicy(),
        TargetHistoryResidualFeaturePolicy(),
    )
}


def get_residual_feature_policy(code: str) -> ResidualFeaturePolicy:
    """按稳定编码加载残差特征策略，供Profile按行业选择。"""
    try:
        return _POLICIES[code]
    except KeyError as exc:
        raise ValueError(f"不支持的残差特征策略: {code}") from exc
