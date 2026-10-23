"""
集成预测工具 — 多模型组合

支持方法：
- 简单平均 / 加权平均
- 中位数集成
- Windsor 平均（截尾平均）
- 基于验证集表现选择 top_k
- 简单 Stacking（线性 / 岭回归元模型）
"""

from __future__ import annotations

import numpy as np

from ..base import ToolResult


# ============================================
# 工具函数
# ============================================

def ensemble_average(
    predictions: dict,
    weights: dict | None = None,
) -> ToolResult:
    """
    简单平均或加权平均集成

    Args:
        predictions: {model_name: np.ndarray 或 list}
        weights: {model_name: float}，为 None 时等权

    Returns:
        ToolResult.data = {
            "ensemble_pred": np.ndarray,
            "weights": dict,
            "models": list[str],
        }
    """
    try:
        if not predictions:
            return ToolResult(
                data=None,
                success=False,
                error="predictions is empty",
            )

        models = list(predictions.keys())
        arrays = []
        for m in models:
            arr = np.asarray(predictions[m], dtype=float)
            arrays.append(arr)

        # 校验形状一致
        shape = arrays[0].shape
        for i, arr in enumerate(arrays):
            if arr.shape != shape:
                return ToolResult(
                    data=None,
                    success=False,
                    error=f"shape mismatch: {models[i]} has shape {arr.shape}, expected {shape}",
                )

        # 确定权重
        if weights is None:
            w = np.ones(len(models)) / len(models)
            weights_dict = {m: 1.0 / len(models) for m in models}
        else:
            w = np.array([float(weights.get(m, 0.0)) for m in models])
            total = w.sum()
            if total <= 0:
                return ToolResult(
                    data=None,
                    success=False,
                    error="sum of weights must be positive",
                )
            w = w / total
            weights_dict = {m: w[i] for i, m in enumerate(models)}

        stacked = np.stack(arrays, axis=0)  # (n_models, ...)
        ensemble = np.tensordot(w, stacked, axes=([0], [0]))

        return ToolResult(
            data={
                "ensemble_pred": ensemble,
                "weights": weights_dict,
                "models": models,
            },
        )
    except Exception as e:
        return ToolResult(
            data=None,
            success=False,
            error=str(e),
        )


def ensemble_median(predictions: dict) -> ToolResult:
    """
    中位数集成（对异常值更鲁棒）

    Args:
        predictions: {model_name: np.ndarray 或 list}

    Returns:
        ToolResult.data = {
            "ensemble_pred": np.ndarray,
            "models": list[str],
        }
    """
    try:
        if not predictions:
            return ToolResult(
                data=None,
                success=False,
                error="predictions is empty",
            )

        models = list(predictions.keys())
        arrays = []
        for m in models:
            arr = np.asarray(predictions[m], dtype=float)
            arrays.append(arr)

        shape = arrays[0].shape
        for i, arr in enumerate(arrays):
            if arr.shape != shape:
                return ToolResult(
                    data=None,
                    success=False,
                    error=f"shape mismatch: {models[i]} has shape {arr.shape}, expected {shape}",
                )

        stacked = np.stack(arrays, axis=0)
        median = np.median(stacked, axis=0)

        return ToolResult(
            data={
                "ensemble_pred": median,
                "models": models,
            },
        )
    except Exception as e:
        return ToolResult(
            data=None,
            success=False,
            error=str(e),
        )


def ensemble_windsor(
    predictions: dict,
    trim_pct: float = 0.1,
) -> ToolResult:
    """
    Windsor 平均：去掉最高最低 trim_pct 后平均

    Args:
        predictions: {model_name: np.ndarray 或 list}
        trim_pct: 每端截尾比例 (0 ~ 0.5)

    Returns:
        ToolResult.data = {
            "ensemble_pred": np.ndarray,
            "trim_pct": float,
            "models": list[str],
        }
    """
    try:
        if not predictions:
            return ToolResult(
                data=None,
                success=False,
                error="predictions is empty",
            )
        if trim_pct < 0 or trim_pct >= 0.5:
            return ToolResult(
                data=None,
                success=False,
                error="trim_pct must be in [0, 0.5)",
            )

        models = list(predictions.keys())
        n_models = len(models)
        if n_models < 3:
            # 模型太少，退化为简单平均
            return ensemble_average(predictions)

        arrays = []
        for m in models:
            arr = np.asarray(predictions[m], dtype=float)
            arrays.append(arr)

        shape = arrays[0].shape
        for i, arr in enumerate(arrays):
            if arr.shape != shape:
                return ToolResult(
                    data=None,
                    success=False,
                    error=f"shape mismatch: {models[i]} has shape {arr.shape}, expected {shape}",
                )

        stacked = np.stack(arrays, axis=0)  # (n_models, ...)
        sorted_arr = np.sort(stacked, axis=0)

        n_trim = max(1, int(np.floor(n_models * trim_pct)))
        n_trim = min(n_trim, n_models // 2 - 1)
        n_trim = max(n_trim, 0)

        if n_trim == 0:
            result = np.mean(sorted_arr, axis=0)
        else:
            # Windsor: 把截掉的两端替换成最近保留的值，再平均
            winsorized = sorted_arr.copy()
            winsorized[:n_trim] = sorted_arr[n_trim]
            winsorized[-n_trim:] = sorted_arr[-n_trim - 1]
            result = np.mean(winsorized, axis=0)

        return ToolResult(
            data={
                "ensemble_pred": result,
                "trim_pct": trim_pct,
                "n_trim_each_side": n_trim,
                "models": models,
            },
        )
    except Exception as e:
        return ToolResult(
            data=None,
            success=False,
            error=str(e),
        )


def _compute_metric(y_true: np.ndarray, y_pred: np.ndarray, metric: str) -> float:
    """辅助：计算单指标"""
    if metric == "rmse":
        return float(np.sqrt(np.mean((y_true - y_pred) ** 2)))
    elif metric == "mae":
        return float(np.mean(np.abs(y_true - y_pred)))
    elif metric == "mse":
        return float(np.mean((y_true - y_pred) ** 2))
    elif metric == "mape":
        mask = y_true != 0
        if not mask.any():
            return float("inf")
        return float(np.mean(np.abs((y_true[mask] - y_pred[mask]) / y_true[mask])) * 100)
    else:
        raise ValueError(f"unknown metric: {metric}")


def ensemble_best_on_validation(
    predictions: dict,
    y_val: np.ndarray,
    metric: str = "rmse",
    top_k: int = 3,
) -> ToolResult:
    """
    基于验证集表现选 top_k 模型，按 1/metric 加权平均

    Args:
        predictions: {model_name: np.ndarray}，每个模型的预测要和 y_val 等长
        y_val: 验证集真值
        metric: "rmse" | "mae" | "mse" | "mape"
        top_k: 选取前 k 个模型

    Returns:
        ToolResult.data = {
            "ensemble_pred": np.ndarray,
            "weights": dict,
            "model_scores": dict,
            "top_models": list[str],
        }
    """
    try:
        if not predictions:
            return ToolResult(
                data=None,
                success=False,
                error="predictions is empty",
            )

        y_val = np.asarray(y_val, dtype=float).ravel()
        metric = metric.lower()

        # 计算每个模型的指标
        scores: dict[str, float] = {}
        arrays: dict[str, np.ndarray] = {}
        for name, pred in predictions.items():
            arr = np.asarray(pred, dtype=float).ravel()
            if arr.shape != y_val.shape:
                return ToolResult(
                    data=None,
                    success=False,
                    error=f"length mismatch: model '{name}' has length {len(arr)}, y_val has {len(y_val)}",
                )
            arrays[name] = arr
            scores[name] = _compute_metric(y_val, arr, metric)

        # 按 metric 升序（越小越好）
        sorted_models = sorted(scores.keys(), key=lambda m: scores[m])
        top_k = min(top_k, len(sorted_models))
        top_models = sorted_models[:top_k]

        # 按 1/metric 加权
        inv_scores = []
        for m in top_models:
            s = scores[m]
            if s <= 0:
                inv_scores.append(1e12)
            else:
                inv_scores.append(1.0 / s)
        inv_arr = np.array(inv_scores)
        inv_arr = inv_arr / inv_arr.sum()

        weights = {m: float(inv_arr[i]) for i, m in enumerate(top_models)}

        # 加权平均
        top_arrays = np.stack([arrays[m] for m in top_models], axis=0)
        ensemble = np.tensordot(inv_arr, top_arrays, axes=([0], [0]))

        return ToolResult(
            data={
                "ensemble_pred": ensemble,
                "weights": weights,
                "model_scores": scores,
                "top_models": top_models,
                "metric": metric,
            },
        )
    except Exception as e:
        return ToolResult(
            data=None,
            success=False,
            error=str(e),
        )


def ensemble_stacking(
    predictions_train: dict,
    y_train: np.ndarray,
    predictions_test: dict,
    meta_model: str = "linear",
) -> ToolResult:
    """
    简单 Stacking：用基模型预测作为特征，训练元模型

    sklearn 不可用时用 numpy 实现简单线性组合。

    Args:
        predictions_train: {model: train_pred_array}
        y_train: 训练集真值
        predictions_test: {model: test_pred_array}
        meta_model: "linear" (numpy 最小二乘) 或 "ridge"

    Returns:
        ToolResult.data = {
            "train_pred": np.ndarray,
            "test_pred": np.ndarray,
            "meta_coef": dict,
            "meta_intercept": float,
            "meta_model": str,
            "models": list[str],
        }
    """
    try:
        if not predictions_train or not predictions_test:
            return ToolResult(
                data=None,
                success=False,
                error="predictions_train and predictions_test must not be empty",
            )

        models = list(predictions_train.keys())
        # 校验 test 有同样的模型
        for m in models:
            if m not in predictions_test:
                return ToolResult(
                    data=None,
                    success=False,
                    error=f"model '{m}' missing in predictions_test",
                )

        y_train = np.asarray(y_train, dtype=float).ravel()
        n_train = len(y_train)

        # 构建特征矩阵
        train_cols = []
        test_cols = []
        for m in models:
            tr = np.asarray(predictions_train[m], dtype=float).ravel()
            te = np.asarray(predictions_test[m], dtype=float).ravel()
            if len(tr) != n_train:
                return ToolResult(
                    data=None,
                    success=False,
                    error=f"train length mismatch for model '{m}'",
                )
            train_cols.append(tr)
            test_cols.append(te)

        X_train = np.column_stack(train_cols)  # (n_train, n_models)
        X_test = np.column_stack(test_cols)    # (n_test, n_models)

        # 尝试用 sklearn，否则退化为 numpy
        use_sklearn = False
        try:
            from sklearn.linear_model import LinearRegression, Ridge  # noqa: F401
            use_sklearn = True
        except ImportError:
            use_sklearn = False

        if use_sklearn:
            if meta_model == "ridge":
                from sklearn.linear_model import Ridge
                model = Ridge(alpha=1.0, fit_intercept=True)
            else:
                from sklearn.linear_model import LinearRegression
                model = LinearRegression(fit_intercept=True)

            model.fit(X_train, y_train)
            train_pred = model.predict(X_train)
            test_pred = model.predict(X_test)
            coef = {m: float(model.coef_[i]) for i, m in enumerate(models)}
            intercept = float(model.intercept_)
        else:
            # numpy 最小二乘实现
            # 加截距项
            X_train_aug = np.column_stack([X_train, np.ones(n_train)])
            if meta_model == "ridge":
                # 简单岭回归 alpha=1.0
                alpha = 1.0
                n_features = X_train_aug.shape[1]
                I = np.eye(n_features)
                I[-1, -1] = 0  # 不对截距加正则
                theta, *_ = np.linalg.lstsq(
                    X_train_aug.T @ X_train_aug + alpha * I,
                    X_train_aug.T @ y_train,
                    rcond=None,
                )
            else:
                theta, *_ = np.linalg.lstsq(X_train_aug, y_train, rcond=None)

            train_pred = X_train_aug @ theta
            n_test = X_test.shape[0]
            X_test_aug = np.column_stack([X_test, np.ones(n_test)])
            test_pred = X_test_aug @ theta
            coef = {m: float(theta[i]) for i, m in enumerate(models)}
            intercept = float(theta[-1])

        return ToolResult(
            data={
                "train_pred": train_pred,
                "test_pred": test_pred,
                "meta_coef": coef,
                "meta_intercept": intercept,
                "meta_model": meta_model,
                "models": models,
                "used_sklearn": use_sklearn,
            },
        )
    except Exception as e:
        return ToolResult(
            data=None,
            success=False,
            error=str(e),
        )
