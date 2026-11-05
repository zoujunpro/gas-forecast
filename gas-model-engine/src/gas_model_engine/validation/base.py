from __future__ import annotations

from typing import TYPE_CHECKING

import numpy as np

if TYPE_CHECKING:
    from ..algorithms.contracts import AlgorithmDataset
    from ..execution.contracts import ForecastRequest


class BaseForecastRequestValidator:
    """预测请求校验器基类，只提供各场景都会使用的结构检查工具。"""

    code = "BASE"

    def validate(self, request: ForecastRequest) -> None:
        """校验请求；不合法时抛出包含明确字段信息的 ValueError。"""
        raise NotImplementedError

    @staticmethod
    def validate_history(data: AlgorithmDataset, scene_name: str) -> None:
        """校验历史日期、目标值、数据长度及已清洗的外部特征。"""
        if len(data.dates) < 1:
            raise ValueError(f"{scene_name}历史数据不能为空")
        if data.target is None:
            raise ValueError(f"{scene_name}历史数据必须包含销量target")
        if len(data.target) != len(data.dates) or len(data.features) != len(data.dates):
            raise ValueError(f"{scene_name}历史日期、销量和特征行数必须一致")
        if data.dates.hasnans:
            raise ValueError(f"{scene_name}历史日期包含无效值")
        if data.dates.has_duplicates:
            raise ValueError(f"{scene_name}历史日期不能重复")
        if not np.isfinite(data.target.to_numpy(dtype=float)).all():
            raise ValueError(f"{scene_name}历史销量包含空值或无穷值")
        BaseForecastRequestValidator.validate_features(data, f"{scene_name}历史")

    @staticmethod
    def validate_future(
        data: AlgorithmDataset,
        scene_name: str,
        *,
        features_optional: bool,
    ) -> None:
        """校验未来日期；外部特征是否必填由具体场景决定。"""
        if len(data.dates) < 1:
            raise ValueError(f"{scene_name}未来预测日期不能为空")
        if len(data.features) != len(data.dates):
            raise ValueError(f"{scene_name}未来日期和特征行数必须一致")
        if data.dates.hasnans:
            raise ValueError(f"{scene_name}未来预测日期包含无效值")
        if data.dates.has_duplicates:
            raise ValueError(f"{scene_name}未来预测日期不能重复")
        if not features_optional and not len(data.features.columns):
            raise ValueError(f"{scene_name}必须提供未来外部特征")
        if len(data.features.columns):
            BaseForecastRequestValidator.validate_features(data, f"{scene_name}未来")

    @staticmethod
    def validate_features(data: AlgorithmDataset, label: str) -> None:
        """仅在存在外部特征时检查其值是否全部为有限数值。"""
        if not len(data.features.columns):
            return
        try:
            values = data.features.to_numpy(dtype=float)
        except (TypeError, ValueError) as exc:
            raise ValueError(f"{label}外部特征包含非数值") from exc
        if not np.isfinite(values).all():
            invalid = [
                column
                for column in data.features.columns
                if not np.isfinite(data.features[column].to_numpy(dtype=float)).all()
            ]
            raise ValueError(f"{label}外部特征包含空值或无穷值: {', '.join(invalid)}")
