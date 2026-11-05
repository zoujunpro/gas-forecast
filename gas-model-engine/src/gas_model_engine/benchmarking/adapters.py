from __future__ import annotations

from collections.abc import Callable
from typing import Any

import numpy as np


class LegacyForecastAdapter:
    """把旧工程任意预测函数适配为 Benchmark 所需的一维数组接口。"""

    def __init__(
        self,
        runner: Callable[[Any], Any],
        prediction_extractor: Callable[[Any], Any],
    ) -> None:
        self.runner = runner
        self.prediction_extractor = prediction_extractor

    def __call__(self, payload) -> np.ndarray:
        return np.asarray(self.prediction_extractor(self.runner(payload)), dtype=float)


class PipelineForecastAdapter:
    """从共享验收输入构造ForecastRequest并提取新Pipeline最终预测。"""

    def __init__(self, pipeline, request_factory: Callable[[Any], Any]) -> None:
        self.pipeline = pipeline
        self.request_factory = request_factory

    def __call__(self, payload) -> np.ndarray:
        result = self.pipeline.run(self.request_factory(payload))
        return np.asarray(result.prediction.final_prediction.values, dtype=float)
