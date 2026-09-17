from __future__ import annotations

from dataclasses import asdict, dataclass, field
from typing import Any, Dict, List


@dataclass
class TrainResult:
    """训练接口返回值；由调用方决定如何落库。"""

    artifact: Any
    best_model: str
    metrics: Dict[str, float]
    selection_reason: str
    requested_candidate_count: int
    successful_candidate_count: int
    ranked_candidate_count: int
    selected_model_params: Dict[str, Any] = field(default_factory=dict)
    backtest_results: List[Dict[str, Any]] = field(default_factory=list)
    model_ranking: List[Dict[str, Any]] = field(default_factory=list)
    fold_metrics: List[Dict[str, Any]] = field(default_factory=list)
    features: List[str] = field(default_factory=list)
    summary: Dict[str, Any] = field(default_factory=dict)
    clean_log: List[Dict[str, Any]] = field(default_factory=list)
    issues: List[Dict[str, Any]] = field(default_factory=list)
    errors: List[Dict[str, Any]] = field(default_factory=list)
    status: str = "SUCCESS"

    def to_dict(self) -> Dict[str, Any]:
        """返回适合业务落库的内容，不包含二进制模型对象。"""
        return {
            "status": self.status,
            "best_model": self.best_model,
            "metrics": self.metrics,
            "selection_reason": self.selection_reason,
            "requested_candidate_count": self.requested_candidate_count,
            "successful_candidate_count": self.successful_candidate_count,
            "ranked_candidate_count": self.ranked_candidate_count,
            "selected_model_params": self.selected_model_params,
            "backtest_results": self.backtest_results,
            "model_ranking": self.model_ranking,
            "fold_metrics": self.fold_metrics,
            "features": self.features,
            "summary": self.summary,
            "clean_log": self.clean_log,
            "issues": self.issues,
            "errors": self.errors,
        }


@dataclass
class PredictionResult:
    """预测接口返回值；不包含任何数据库操作。"""

    model_name: str
    results: List[Dict[str, Any]]
    status: str = "SUCCESS"

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)
