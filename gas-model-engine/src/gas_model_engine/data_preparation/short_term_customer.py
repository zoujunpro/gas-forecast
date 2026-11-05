from __future__ import annotations

from typing import TYPE_CHECKING

import numpy as np
import pandas as pd

from ..algorithms.contracts import AlgorithmDataset

if TYPE_CHECKING:
    from ..execution.contracts import ForecastRequest


class ShortTermCustomerDataCleaner:
    """短期客户数据清洗器，第一版保持旧城燃数据处理口径。

    历史数据清洗日期、销量和全部外部值；未来数据只要求存在预测日期，
    外部特征可不提供；本类只负责转换和填充，是否合法由独立校验器判断。
    历史与未来分别处理，禁止未来值参与历史缺失值填充。
    """

    def clean(self, request: ForecastRequest) -> ForecastRequest:
        """返回完成日期排序、无效历史行删除和外部值清洗的新请求。"""
        history = self._clean_history(request.train_data)
        future = self._clean_future(request.future_data)
        return request.__class__(request.profile_code, history, future)

    def _clean_history(self, data: AlgorithmDataset) -> AlgorithmDataset:
        if data.target is None:
            raise ValueError("短期客户历史数据必须包含销量target")
        dates = pd.to_datetime(data.dates, errors="coerce")
        target = pd.to_numeric(data.target, errors="coerce")
        valid = dates.notna() & target.notna()
        features = data.features.loc[valid].reset_index(drop=True)
        dates = dates[valid]
        target = target.loc[valid].reset_index(drop=True)
        features = self._clean_external_values(features)
        order = np.argsort(dates.to_numpy())
        return AlgorithmDataset(
            features=features.iloc[order].reset_index(drop=True),
            dates=dates[order],
            target=target.iloc[order].reset_index(drop=True),
            sample_weight=None
            if data.sample_weight is None
            else data.sample_weight.loc[valid].iloc[order].reset_index(drop=True),
            metadata={
                **data.metadata,
                "data_cleaning": {
                    "cleaner": "SHORT_TERM_CUSTOMER_V1",
                    "removed_invalid_history_rows": int((~valid).sum()),
                },
            },
        )

    def _clean_future(self, data: AlgorithmDataset) -> AlgorithmDataset:
        dates = pd.to_datetime(data.dates, errors="coerce")
        features = self._clean_external_values(data.features.copy())
        order = np.argsort(dates.to_numpy())
        return AlgorithmDataset(
            features=features.iloc[order].reset_index(drop=True),
            dates=dates[order],
            target=None,
            metadata={
                **data.metadata,
                "data_cleaning": {
                    "cleaner": "SHORT_TERM_CUSTOMER_V1",
                    "future_features_provided": bool(len(features.columns)),
                },
            },
        )

    @staticmethod
    def _clean_external_values(frame: pd.DataFrame) -> pd.DataFrame:
        """按旧城燃顺序转数值、插值、前填和后填。"""
        if not len(frame.columns):
            return frame.reset_index(drop=True)
        cleaned = frame.apply(pd.to_numeric, errors="coerce")
        cleaned = cleaned.interpolate(limit_direction="both").ffill().bfill()
        return cleaned.reset_index(drop=True)
