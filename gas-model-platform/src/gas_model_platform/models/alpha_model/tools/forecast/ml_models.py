"""
机器学习预测模型工具

支持模型：
- linear_regression: 线性回归（sklearn / numpy 降级）
- random_forest: 随机森林（sklearn，降级为线性回归）
- xgboost: XGBoost（降级为 RF，再降级为线性回归）
- lightgbm: LightGBM（降级为 XGBoost，再降级为 RF）

所有模型统一接口：train_df + date_col + value_col + horizon -> 预测结果
返回 ToolResult，含预测值、置信区间、模型信息。
"""

from __future__ import annotations

from typing import Optional

import numpy as np
import pandas as pd

from ..base import ToolResult


# ============================================
# 公共辅助函数
# ============================================

def _prepare_series(df, date_col, value_col):
    """准备时间序列（与 stat_models 保持一致）"""
    df = df.copy()
    df[date_col] = pd.to_datetime(df[date_col])
    df = df.sort_values(date_col).set_index(date_col)
    y = pd.to_numeric(df[value_col], errors="coerce")
    y = y.interpolate().ffill().bfill()
    return y


def _prepare_ml_data(train_df, date_col, value_col, use_time_features=True,
                     lag_count=5, roll_windows=None):
    """
    准备 ML 训练数据：生成滞后特征、滚动特征、时间特征

    返回 X (DataFrame), y (Series), feature_cols (list)
    """
    if roll_windows is None:
        roll_windows = [3, 7]

    y = _prepare_series(train_df, date_col, value_col)
    df = pd.DataFrame({value_col: y})

    # 滞后特征
    for i in range(1, lag_count + 1):
        df[f"lag_{i}"] = df[value_col].shift(i)

    # 滚动统计特征
    for w in roll_windows:
        if w < len(df):
            df[f"roll_mean_{w}"] = df[value_col].shift(1).rolling(window=w, min_periods=2).mean()
            df[f"roll_std_{w}"] = df[value_col].shift(1).rolling(window=w, min_periods=2).std()

    # 时间特征
    if use_time_features:
        idx = df.index
        df["year"] = idx.year
        df["month"] = idx.month
        df["day"] = idx.day
        df["dayofweek"] = idx.dayofweek
        df["dayofyear"] = idx.dayofyear
        df["weekofyear"] = idx.isocalendar().week.astype(int)

    # 去掉 NaN 行
    df_clean = df.dropna()
    if len(df_clean) < 3:
        # 数据太少，退回只用滞后 1
        df = pd.DataFrame({value_col: y})
        df["lag_1"] = df[value_col].shift(1)
        df_clean = df.dropna()

    feature_cols = [c for c in df_clean.columns if c != value_col]
    X = df_clean[feature_cols]
    y_clean = df_clean[value_col]

    return X, y_clean, feature_cols, y


def _recursive_forecast(model, last_known_series, date_col, value_col,
                        horizon, feature_cols, use_time_features=True,
                        roll_windows=None, predict_kwargs=None):
    """
    递归多步预测：用前一步的预测值作为下一个滞后特征

    Parameters
    ----------
    model : 已拟合模型（需有 .predict 方法）
    last_known_series : pd.Series，以日期为索引的最后一段已知序列
    horizon : int
    feature_cols : list
    use_time_features : bool
    roll_windows : list
    predict_kwargs : dict，传给 model.predict 的额外参数

    Returns
    -------
    forecast : np.array, shape (horizon,)
    extra : dict，额外输出（如各树预测用于 RF 置信区间）
    """
    if predict_kwargs is None:
        predict_kwargs = {}
    if roll_windows is None:
        roll_windows = [3, 7]

    # 从 feature_cols 推断滞后阶数
    lag_count = max(
        int(c.split("_")[1]) for c in feature_cols if c.startswith("lag_")
    ) if any(c.startswith("lag_") for c in feature_cols) else 1

    last_date = last_known_series.index[-1]
    freq = pd.infer_freq(last_known_series.index) or "D"
    future_dates = pd.date_range(
        start=last_date + pd.tseries.frequencies.to_offset(freq),
        periods=horizon,
        freq=freq,
    )

    # 维护一个值历史，用于计算滞后和滚动
    history = list(last_known_series.values)
    forecast_values = []
    extra_predictions = []  # 存每一步的详细预测（如各树结果）

    for i, dt in enumerate(future_dates):
        # 构造单行特征
        row = {}

        # 滞后特征
        for lag_i in range(1, lag_count + 1):
            pos = len(history) - lag_i
            row[f"lag_{lag_i}"] = history[pos] if pos >= 0 else history[-1]

        # 滚动特征
        for w in roll_windows:
            col_mean = f"roll_mean_{w}"
            col_std = f"roll_std_{w}"
            if col_mean in feature_cols or col_std in feature_cols:
                recent = history[-w:] if len(history) >= w else history
                if col_mean in feature_cols:
                    row[col_mean] = np.mean(recent)
                if col_std in feature_cols:
                    row[col_std] = np.std(recent) if len(recent) > 1 else 0.0

        # 时间特征
        if use_time_features:
            row["year"] = dt.year
            row["month"] = dt.month
            row["day"] = dt.day
            row["dayofweek"] = dt.dayofweek
            row["dayofyear"] = dt.dayofyear
            row["weekofyear"] = dt.isocalendar().week

        # 只保留模型见过的特征
        feat_row = {c: row.get(c, 0.0) for c in feature_cols}
        X_step = pd.DataFrame([feat_row])[feature_cols]

        # 检查是否需要返回多输出（如 RF 各树）
        if hasattr(model, "estimators_") and predict_kwargs.get("_return_tree_preds", False):
            # 随机森林：收集每棵树的预测
            tree_preds = np.array([tree.predict(X_step.values)[0] for tree in model.estimators_])
            pred = np.mean(tree_preds)
            extra_predictions.append(tree_preds)
        else:
            pred = float(model.predict(X_step.values.reshape(1, -1) if hasattr(X_step, 'values') else X_step, **{k: v for k, v in predict_kwargs.items() if k != "_return_tree_preds"})[0])

        forecast_values.append(pred)
        history.append(pred)

    extra = {"tree_predictions": np.array(extra_predictions).T} if extra_predictions else {}
    return np.array(forecast_values), extra


def _make_forecast_df(y_train, forecast, date_col, value_col, conf_int=None):
    """构造预测结果 DataFrame（与 stat_models 保持一致）"""
    last_date = y_train.index[-1]
    freq = pd.infer_freq(y_train.index) or "D"
    future_dates = pd.date_range(
        start=last_date + pd.tseries.frequencies.to_offset(freq),
        periods=len(forecast),
        freq=freq,
    )
    result = pd.DataFrame({
        date_col: future_dates,
        value_col: forecast,
    })
    if conf_int is not None:
        result[f"{value_col}_lower"] = conf_int[:, 0]
        result[f"{value_col}_upper"] = conf_int[:, 1]
    return result


def _feature_importance_dict(model, feature_cols):
    """从模型提取特征重要性，返回 dict"""
    if hasattr(model, "feature_importances_"):
        importance = model.feature_importances_
    elif hasattr(model, "coef_"):
        importance = np.abs(np.asarray(model.coef_).flatten())
    else:
        return {}
    # 长度对齐
    if len(importance) != len(feature_cols):
        importance = importance[:len(feature_cols)]
    return dict(zip(feature_cols, [float(v) for v in importance]))


# ============================================
# 1. 线性回归
# ============================================

def forecast_linear_regression(
    train_df: pd.DataFrame,
    date_col: str,
    value_col: str,
    horizon: int = 7,
    use_time_features: bool = True,
) -> ToolResult:
    """
    线性回归预测

    基于滞后特征 + 滚动特征 + 时间特征，递归多步预测。
    首选 sklearn.LinearRegression；不可用时用 numpy 最小二乘实现简易版。
    置信区间用残差标准差近似。
    """
    try:
        # 尝试 sklearn
        try:
            from sklearn.linear_model import LinearRegression
            use_sklearn = True
        except ImportError:
            LinearRegression = None
            use_sklearn = False

        X, y_clean, feature_cols, y_full = _prepare_ml_data(
            train_df, date_col, value_col, use_time_features=use_time_features
        )

        X_vals = X.values
        y_vals = y_clean.values

        if use_sklearn:
            model = LinearRegression()
            model.fit(X_vals, y_vals)
            fitted = model.predict(X_vals)
        else:
            # numpy 最小二乘：y = X @ beta，加截距
            X_with_intercept = np.column_stack([np.ones(len(X_vals)), X_vals])
            beta, _, _, _ = np.linalg.lstsq(X_with_intercept, y_vals, rcond=None)

            class _NumpyLR:
                def __init__(self, beta_):
                    self.coef_ = beta_[1:]
                    self.intercept_ = beta_[0]

                def predict(self, X_input):
                    X_input = np.asarray(X_input)
                    if X_input.ndim == 1:
                        X_input = X_input.reshape(1, -1)
                    return X_input @ self.coef_ + self.intercept_

            model = _NumpyLR(beta)
            fitted = model.predict(X_vals)

        # 残差标准差
        resid = y_vals - fitted
        resid_std = float(np.std(resid)) if len(resid) > 1 else 0.0

        # 递归预测
        last_known = y_full.iloc[-max(10, len(feature_cols)):]
        forecast, _ = _recursive_forecast(
            model, last_known, date_col, value_col, horizon, feature_cols,
            use_time_features=use_time_features,
        )

        # 置信区间：用残差标准近似，随步长略增
        step_scales = np.sqrt(np.arange(1, horizon + 1))
        conf_int = np.column_stack([
            forecast - 1.96 * resid_std * step_scales,
            forecast + 1.96 * resid_std * step_scales,
        ])

        result_df = _make_forecast_df(y_full, forecast, date_col, value_col, conf_int)
        feat_imp = _feature_importance_dict(model, feature_cols)
        model_name = "linear_regression" if use_sklearn else "linear_regression(numpy)"

        return ToolResult(
            data={
                "forecast_df": result_df,
                "forecast_values": forecast.tolist(),
                "conf_int": conf_int.tolist(),
                "model": model_name,
                "feature_importance": feat_imp,
                "n_features": len(feature_cols),
                "resid_std": resid_std,
            },
            metadata={
                "model": model_name,
                "horizon": horizon,
                "n_features": len(feature_cols),
                "resid_std": resid_std,
            },
        )
    except Exception as e:
        return ToolResult(data=None, success=False, error=str(e))


# ============================================
# 2. 随机森林
# ============================================

def forecast_random_forest(
    train_df: pd.DataFrame,
    date_col: str,
    value_col: str,
    horizon: int = 7,
    n_estimators: int = 100,
    max_depth: Optional[int] = None,
) -> ToolResult:
    """
    随机森林回归预测

    基于滞后+滚动+时间特征，递归多步预测。
    置信区间基于各决策树预测的方差。
    sklearn 不可用时退化为线性回归。
    """
    try:
        try:
            from sklearn.ensemble import RandomForestRegressor
        except ImportError:
            # 退化为线性回归
            result = forecast_linear_regression(train_df, date_col, value_col, horizon)
            if result.success:
                result.data["model"] = "random_forest(linear_regression_fallback)"
                result.metadata["note"] = "sklearn 不可用，使用线性回归替代"
            return result

        X, y_clean, feature_cols, y_full = _prepare_ml_data(
            train_df, date_col, value_col, use_time_features=True
        )

        # 调整 n_estimators 避免数据太少
        n_est = max(10, min(n_estimators, len(y_clean)))

        model = RandomForestRegressor(
            n_estimators=n_est,
            max_depth=max_depth,
            random_state=42,
            n_jobs=-1,
        )
        model.fit(X.values, y_clean.values)

        # 递归预测，同时收集每棵树的预测
        last_known = y_full.iloc[-max(10, len(feature_cols)):]
        forecast, extra = _recursive_forecast(
            model, last_known, date_col, value_col, horizon, feature_cols,
            use_time_features=True,
            predict_kwargs={"_return_tree_preds": True},
        )

        # 置信区间：用各树预测的标准差
        tree_preds = extra.get("tree_predictions")  # shape (n_trees, horizon)
        if tree_preds is not None and tree_preds.shape[1] == horizon:
            std_per_step = np.std(tree_preds, axis=0)
        else:
            # 回退用残差标准差
            fitted = model.predict(X.values)
            resid_std = float(np.std(y_clean.values - fitted))
            std_per_step = resid_std * np.sqrt(np.arange(1, horizon + 1))

        conf_int = np.column_stack([
            forecast - 1.96 * std_per_step,
            forecast + 1.96 * std_per_step,
        ])

        result_df = _make_forecast_df(y_full, forecast, date_col, value_col, conf_int)
        feat_imp = _feature_importance_dict(model, feature_cols)

        return ToolResult(
            data={
                "forecast_df": result_df,
                "forecast_values": forecast.tolist(),
                "conf_int": conf_int.tolist(),
                "model": "random_forest",
                "feature_importance": feat_imp,
                "n_features": len(feature_cols),
                "n_estimators": n_est,
                "max_depth": max_depth,
            },
            metadata={
                "model": "random_forest",
                "horizon": horizon,
                "n_features": len(feature_cols),
                "n_estimators": n_est,
            },
        )
    except Exception as e:
        return ToolResult(data=None, success=False, error=str(e))


# ============================================
# 3. XGBoost
# ============================================

def forecast_xgboost(
    train_df: pd.DataFrame,
    date_col: str,
    value_col: str,
    horizon: int = 7,
    n_estimators: int = 100,
    max_depth: int = 6,
    learning_rate: float = 0.1,
) -> ToolResult:
    """
    XGBoost 回归预测

    基于滞后+滚动+时间特征，递归多步预测。
    置信区间用多棵树预测方差近似。
    不可用时退化为 RandomForest，再退化为线性回归。
    """
    try:
        try:
            import xgboost as xgb
        except ImportError:
            # 退化为随机森林
            result = forecast_random_forest(train_df, date_col, value_col, horizon)
            if result.success:
                result.data["model"] = "xgboost(random_forest_fallback)"
                result.metadata["note"] = "xgboost 未安装，使用 RandomForest 替代"
            return result

        X, y_clean, feature_cols, y_full = _prepare_ml_data(
            train_df, date_col, value_col, use_time_features=True
        )

        # 调整轮次
        n_est = max(10, min(n_estimators, len(y_clean) * 2))

        model = xgb.XGBRegressor(
            n_estimators=n_est,
            max_depth=max_depth,
            learning_rate=learning_rate,
            verbosity=0,
            random_state=42,
            objective="reg:squarederror",
        )
        model.fit(X.values, y_clean.values)

        # 递归预测
        last_known = y_full.iloc[-max(10, len(feature_cols)):]
        forecast, _ = _recursive_forecast(
            model, last_known, date_col, value_col, horizon, feature_cols,
            use_time_features=True,
        )

        # 置信区间：用残差标准差近似（XGBoost 单模型无内置不确定性）
        fitted = model.predict(X.values)
        resid = y_clean.values - fitted
        resid_std = float(np.std(resid)) if len(resid) > 1 else 0.0
        step_scales = np.sqrt(np.arange(1, horizon + 1))
        conf_int = np.column_stack([
            forecast - 1.96 * resid_std * step_scales,
            forecast + 1.96 * resid_std * step_scales,
        ])

        result_df = _make_forecast_df(y_full, forecast, date_col, value_col, conf_int)
        feat_imp = _feature_importance_dict(model, feature_cols)

        return ToolResult(
            data={
                "forecast_df": result_df,
                "forecast_values": forecast.tolist(),
                "conf_int": conf_int.tolist(),
                "model": "xgboost",
                "feature_importance": feat_imp,
                "n_features": len(feature_cols),
                "n_estimators": n_est,
                "max_depth": max_depth,
                "learning_rate": learning_rate,
            },
            metadata={
                "model": "xgboost",
                "horizon": horizon,
                "n_features": len(feature_cols),
                "n_estimators": n_est,
                "max_depth": max_depth,
            },
        )
    except Exception as e:
        return ToolResult(data=None, success=False, error=str(e))


# ============================================
# 4. LightGBM
# ============================================

def forecast_lightgbm(
    train_df: pd.DataFrame,
    date_col: str,
    value_col: str,
    horizon: int = 7,
    n_estimators: int = 100,
    max_depth: int = -1,
    learning_rate: float = 0.1,
) -> ToolResult:
    """
    LightGBM 回归预测

    基于滞后+滚动+时间特征，递归多步预测。
    不可用时退化为 XGBoost，再退化为 RandomForest。
    """
    try:
        try:
            import lightgbm as lgb
        except ImportError:
            # 退化为 XGBoost
            result = forecast_xgboost(train_df, date_col, value_col, horizon)
            if result.success:
                result.data["model"] = "lightgbm(xgboost_fallback)"
                result.metadata["note"] = "lightgbm 未安装，使用 XGBoost 替代"
            return result

        X, y_clean, feature_cols, y_full = _prepare_ml_data(
            train_df, date_col, value_col, use_time_features=True
        )

        n_est = max(10, min(n_estimators, len(y_clean) * 2))

        model = lgb.LGBMRegressor(
            n_estimators=n_est,
            max_depth=max_depth,
            learning_rate=learning_rate,
            verbose=-1,
            random_state=42,
        )
        model.fit(X.values, y_clean.values)

        # 递归预测
        last_known = y_full.iloc[-max(10, len(feature_cols)):]
        forecast, _ = _recursive_forecast(
            model, last_known, date_col, value_col, horizon, feature_cols,
            use_time_features=True,
        )

        # 置信区间：残差标准差近似
        fitted = model.predict(X.values)
        resid = y_clean.values - fitted
        resid_std = float(np.std(resid)) if len(resid) > 1 else 0.0
        step_scales = np.sqrt(np.arange(1, horizon + 1))
        conf_int = np.column_stack([
            forecast - 1.96 * resid_std * step_scales,
            forecast + 1.96 * resid_std * step_scales,
        ])

        result_df = _make_forecast_df(y_full, forecast, date_col, value_col, conf_int)
        feat_imp = _feature_importance_dict(model, feature_cols)

        return ToolResult(
            data={
                "forecast_df": result_df,
                "forecast_values": forecast.tolist(),
                "conf_int": conf_int.tolist(),
                "model": "lightgbm",
                "feature_importance": feat_imp,
                "n_features": len(feature_cols),
                "n_estimators": n_est,
                "max_depth": max_depth,
                "learning_rate": learning_rate,
            },
            metadata={
                "model": "lightgbm",
                "horizon": horizon,
                "n_features": len(feature_cols),
                "n_estimators": n_est,
                "max_depth": max_depth,
            },
        )
    except Exception as e:
        return ToolResult(data=None, success=False, error=str(e))
