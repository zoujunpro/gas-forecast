"""
预测工具集 — 需求预测专用工具箱

按类别组织：
- data: 数据加载、清洗、分解、聚合、划分
- feature: 特征工程（滞后、滚动、时间特征、特征选择）
- stat_models: 统计模型（Naive, SMA, EWMA, Holt-Winters, ARIMA, Prophet）
- ml_models: 机器学习模型（LR, RF, XGBoost, LightGBM）
- optimization: 超参优化、自动模型选择
- ensemble: 集成方法（平均、加权、Stacking）
- eval: 评估指标、回测报告、模型对比
- viz: 可视化（预测图、分解图、对比图）
- report: 报告生成
"""

from __future__ import annotations

import logging

from ..base import build_tool, ToolRegistry, ToolCategory

logger = logging.getLogger(__name__)


# ============================================
# 工具注册器
# ============================================

def create_forecast_registry() -> ToolRegistry:
    """
    创建并注册所有预测工具到 ToolRegistry

    返回配置好的 ToolRegistry，可直接给 Agent 使用。
    """
    registry = ToolRegistry()

    # --- 数据层工具 ---
    try:
        from . import data_tools
        registry.register_from_def({
            "name": "load_data",
            "description": "加载时间序列数据（CSV/Excel），自动识别日期列和值列",
            "call": data_tools.load_data,
            "category": ToolCategory.DATA,
            "tags": ["data", "load", "csv", "excel"],
            "is_read_only": True,
            "when_to_use": "需要从文件加载数据时使用，是所有分析的第一步",
            "search_hint": "加载数据 读取文件 csv excel import",
        })
        registry.register_from_def({
            "name": "clean_data",
            "description": "清洗时间序列数据：处理缺失值、异常值、频率对齐",
            "call": data_tools.clean_data,
            "category": ToolCategory.DATA,
            "tags": ["data", "clean", "preprocess", "outlier", "missing"],
            "is_read_only": True,
            "when_to_use": "数据有缺失值、异常值或需要频率对齐时使用",
            "search_hint": "清洗数据 预处理 异常值 缺失值 插值",
        })
        registry.register_from_def({
            "name": "decompose",
            "description": "时间序列分解（趋势+季节+残差），支持 STL/移动平均/差分",
            "call": data_tools.decompose,
            "category": ToolCategory.DATA,
            "tags": ["data", "decompose", "trend", "seasonal", "stl"],
            "is_read_only": True,
            "when_to_use": "需要分析数据的趋势和季节性成分时使用",
            "search_hint": "分解 趋势 季节 残差 STL 周期",
        })
        registry.register_from_def({
            "name": "split_data",
            "description": "划分时间序列训练/验证/测试集（按时间顺序，不打乱）",
            "call": data_tools.split_data,
            "category": ToolCategory.DATA,
            "tags": ["data", "split", "train", "test", "validation"],
            "is_read_only": True,
            "when_to_use": "需要把数据分成训练集和测试集时使用",
            "search_hint": "划分数据 训练集 测试集 验证集 split",
        })
        registry.register_from_def({
            "name": "aggregate_data",
            "description": "按频率聚合数据（日/周/月/季/年）",
            "call": data_tools.aggregate_data,
            "category": ToolCategory.DATA,
            "tags": ["data", "aggregate", "resample", "freq"],
            "is_read_only": True,
            "when_to_use": "需要改变数据粒度（如日转周、周转月）时使用",
            "search_hint": "聚合 重采样 周 月 季度 resample",
        })
    except Exception as exc:
        logger.warning("forecast tool group skipped: %s", exc, exc_info=False)

    # --- 特征工程 ---
    try:
        from . import feature_tools
        registry.register_from_def({
            "name": "create_features",
            "description": "生成时间序列特征（时间特征+滞后+滚动统计）",
            "call": feature_tools.create_features,
            "category": ToolCategory.FEATURE,
            "tags": ["feature", "lag", "rolling", "time features"],
            "is_read_only": True,
            "when_to_use": "为机器学习模型准备特征时使用",
            "search_hint": "特征工程 滞后 滚动 时间特征 feature engineering",
        })
        registry.register_from_def({
            "name": "create_lag_features",
            "description": "生成滞后特征",
            "call": feature_tools.create_lag_features,
            "category": ToolCategory.FEATURE,
            "tags": ["feature", "lag"],
            "is_read_only": True,
            "when_to_use": "只需生成滞后特征时使用",
            "search_hint": "滞后特征 lag features",
        })
        registry.register_from_def({
            "name": "create_rolling_features",
            "description": "生成滚动窗口特征（均值/标准差/最大/最小）",
            "call": feature_tools.create_rolling_features,
            "category": ToolCategory.FEATURE,
            "tags": ["feature", "rolling", "window"],
            "is_read_only": True,
            "when_to_use": "需要生成滚动统计特征时使用",
            "search_hint": "滚动窗口 移动平均 rolling window",
        })
        registry.register_from_def({
            "name": "create_time_features",
            "description": "生成时间特征（年/月/日/星期/季度等）",
            "call": feature_tools.create_time_features,
            "category": ToolCategory.FEATURE,
            "tags": ["feature", "time", "calendar"],
            "is_read_only": True,
            "when_to_use": "需要基于日期生成日历特征时使用",
            "search_hint": "时间特征 日历 星期 月份 time features",
        })
        registry.register_from_def({
            "name": "select_features",
            "description": "特征选择（按相关性或方差）",
            "call": feature_tools.select_features,
            "category": ToolCategory.FEATURE,
            "tags": ["feature", "selection", "importance"],
            "is_read_only": True,
            "when_to_use": "特征太多，需要筛选最重要的特征时使用",
            "search_hint": "特征选择 筛选 相关性 feature selection",
        })
    except Exception as exc:
        logger.warning("forecast tool group skipped: %s", exc, exc_info=False)

    # --- 统计模型 ---
    try:
        from . import stat_models
        registry.register_from_def({
            "name": "forecast_naive",
            "description": "朴素预测 — 用最后一个观测值作为所有未来预测",
            "call": stat_models.forecast_naive,
            "category": ToolCategory.MODEL,
            "tags": ["model", "naive", "baseline"],
            "is_read_only": True,
            "when_to_use": "作为基准模型对比，或数据很少时使用",
            "search_hint": "朴素预测 基准 baseline naive",
        })
        registry.register_from_def({
            "name": "forecast_seasonal_naive",
            "description": "季节性朴素预测 — 用上一周期的对应值作为预测",
            "call": stat_models.forecast_seasonal_naive,
            "category": ToolCategory.MODEL,
            "tags": ["model", "seasonal", "baseline"],
            "is_read_only": True,
            "when_to_use": "数据有明显季节性时作为基准模型",
            "search_hint": "季节性朴素预测 seasonal naive baseline",
        })
        registry.register_from_def({
            "name": "forecast_sma",
            "description": "简单移动平均预测",
            "call": stat_models.forecast_sma,
            "category": ToolCategory.MODEL,
            "tags": ["model", "sma", "moving average"],
            "is_read_only": True,
            "when_to_use": "数据平稳，用近期平均值预测时使用",
            "search_hint": "移动平均 SMA simple moving average",
        })
        registry.register_from_def({
            "name": "forecast_ewma",
            "description": "指数加权移动平均预测（EWMA/SES）",
            "call": stat_models.forecast_ewma,
            "category": ToolCategory.MODEL,
            "tags": ["model", "ewma", "exponential smoothing"],
            "is_read_only": True,
            "when_to_use": "近期数据更重要，需要加权平均时使用",
            "search_hint": "指数加权 EWMA 指数平滑 SES",
        })
        registry.register_from_def({
            "name": "forecast_holt_winters",
            "description": "Holt-Winters 三次指数平滑（趋势+季节）",
            "call": stat_models.forecast_holt_winters,
            "category": ToolCategory.MODEL,
            "tags": ["model", "holt-winters", "exponential smoothing", "trend", "seasonal"],
            "is_read_only": True,
            "when_to_use": "数据有趋势和季节性时使用，是经典统计模型的首选",
            "search_hint": "Holt-Winters 三次指数平滑 趋势季节 holt winters",
        })
        registry.register_from_def({
            "name": "forecast_arima",
            "description": "ARIMA/SARIMA 预测（自动选阶）",
            "call": stat_models.forecast_arima,
            "category": ToolCategory.MODEL,
            "tags": ["model", "arima", "sarima", "statsmodels"],
            "is_read_only": True,
            "when_to_use": "需要精确统计模型，数据有自相关性时使用",
            "search_hint": "ARIMA SARIMA 自回归 差分 移动平均",
        })
        registry.register_from_def({
            "name": "forecast_prophet",
            "description": "Prophet 预测（Facebook/Meta）",
            "call": stat_models.forecast_prophet,
            "category": ToolCategory.MODEL,
            "tags": ["model", "prophet", "facebook", "meta"],
            "is_read_only": True,
            "when_to_use": "数据有强季节性和节假日效应时使用",
            "search_hint": "Prophet Facebook 节假日 prophet",
        })
    except Exception as exc:
        logger.warning("forecast tool group skipped: %s", exc, exc_info=False)

    # --- Prophet + 残差集成模型（优化版，经 WF 验证） ---
    try:
        from . import prophet_residual
        registry.register_from_def({
            "name": "forecast_prophet_xgboost",
            "description": "Prophet + XGBoost 残差集成预测（推荐）— 经 walk-forward 验证最优配置，MAPE 比纯 Prophet 降低 40%",
            "call": prophet_residual.forecast_prophet_xgboost,
            "category": ToolCategory.MODEL,
            "tags": ["model", "prophet", "xgboost", "residual", "ensemble", "optimized"],
            "is_read_only": True,
            "when_to_use": "需要高精度预测时首选，Prophet 捕捉趋势季节 + XGBoost 修正残差，是所有模型中表现最好的",
            "search_hint": "Prophet XGBoost 残差集成 最优模型 prophet residual",
        })
        registry.register_from_def({
            "name": "forecast_prophet_lightgbm",
            "description": "Prophet + LightGBM 残差集成预测 — 经 walk-forward 验证的优化配置",
            "call": prophet_residual.forecast_prophet_lightgbm,
            "category": ToolCategory.MODEL,
            "tags": ["model", "prophet", "lightgbm", "residual", "ensemble", "optimized"],
            "is_read_only": True,
            "when_to_use": "XGBoost 不可用或偏好 LightGBM 时使用，表现接近 XGBoost 版本",
            "search_hint": "Prophet LightGBM 残差集成 LGB prophet residual",
        })
        registry.register_from_def({
            "name": "forecast_prophet_ensemble",
            "description": "Prophet + XGB + LGB 多模型集成 — 同时训练两个残差模型按权重组合",
            "call": prophet_residual.forecast_prophet_ensemble,
            "category": ToolCategory.MODEL,
            "tags": ["model", "prophet", "ensemble", "xgboost", "lightgbm"],
            "is_read_only": True,
            "when_to_use": "追求最大稳定性时使用，多模型组合降低单模型偏差风险",
            "search_hint": "多模型集成 Prophet XGB LightGBM ensemble",
        })
    except Exception as exc:
        logger.warning("forecast tool group skipped: %s", exc, exc_info=False)

    # --- Walk-Forward 验证 ---
    try:
        from . import walkforward
        registry.register_from_def({
            "name": "walk_forward_validate",
            "description": "通用 Walk-Forward 验证 — 滚动时间窗评估模型真实预测能力",
            "call": walkforward.walk_forward_validate,
            "category": ToolCategory.EVAL,
            "tags": ["eval", "walk forward", "backtest", "rolling", "validation"],
            "is_read_only": True,
            "when_to_use": "需要准确评估时间序列模型的预测能力时使用，比留出法更可靠",
            "search_hint": "滚动验证 walk forward 滚动回测 rolling validation",
        })
        registry.register_from_def({
            "name": "walk_forward_prophet_residual",
            "description": "Prophet+残差模型 Walk-Forward 调参 — 自动搜索最优超参数配置",
            "call": walkforward.walk_forward_prophet_residual,
            "category": ToolCategory.OPTIMIZE,
            "tags": ["optimize", "walk forward", "tuning", "prophet", "residual", "xgboost", "lightgbm"],
            "is_read_only": True,
            "when_to_use": "需要用 walk-forward 方式为 Prophet+XGB/LGB 残差模型寻找最优超参时使用",
            "search_hint": "walk forward 调参 超参优化 prophet residual tuning",
        })
    except Exception as exc:
        logger.warning("forecast tool group skipped: %s", exc, exc_info=False)

    # --- ML 模型 ---
    try:
        from . import ml_models
        registry.register_from_def({
            "name": "forecast_linear_regression",
            "description": "线性回归预测（基于滞后+时间特征）",
            "call": ml_models.forecast_linear_regression,
            "category": ToolCategory.MODEL,
            "tags": ["model", "ml", "linear regression", "sklearn"],
            "is_read_only": True,
            "when_to_use": "需要可解释的线性模型作为基准时使用",
            "search_hint": "线性回归 linear regression sklearn",
        })
        registry.register_from_def({
            "name": "forecast_random_forest",
            "description": "随机森林预测",
            "call": ml_models.forecast_random_forest,
            "category": ToolCategory.MODEL,
            "tags": ["model", "ml", "random forest", "sklearn"],
            "is_read_only": True,
            "when_to_use": "数据有非线性关系，需要特征重要性时使用",
            "search_hint": "随机森林 random forest 机器学习",
        })
        registry.register_from_def({
            "name": "forecast_xgboost",
            "description": "XGBoost 预测",
            "call": ml_models.forecast_xgboost,
            "category": ToolCategory.MODEL,
            "tags": ["model", "ml", "xgboost", "gradient boosting"],
            "is_read_only": True,
            "when_to_use": "追求高精度的梯度提升树模型",
            "search_hint": "XGBoost 梯度提升树 GBDT",
        })
        registry.register_from_def({
            "name": "forecast_lightgbm",
            "description": "LightGBM 预测",
            "call": ml_models.forecast_lightgbm,
            "category": ToolCategory.MODEL,
            "tags": ["model", "ml", "lightgbm", "gradient boosting"],
            "is_read_only": True,
            "when_to_use": "大数据量下需要快速训练的梯度提升树",
            "search_hint": "LightGBM 轻量级梯度提升 LGBM",
        })
    except Exception as exc:
        logger.warning("forecast tool group skipped: %s", exc, exc_info=False)

    # --- 优化调参 ---
    try:
        from . import optimization_tools
        registry.register_from_def({
            "name": "auto_select_model",
            "description": "自动模型选择 — 默认对比统计模型、XGBoost/LightGBM 与 Prophet+XGBoost，选最优",
            "call": optimization_tools.auto_select_model,
            "category": ToolCategory.OPTIMIZE,
            "tags": ["optimize", "auto", "model selection", "prophet", "xgboost"],
            "is_read_only": True,
            "when_to_use": "不知道选什么模型时自动对比；务必覆盖 prophet_xgboost，不要只跑 EWMA/ARIMA",
            "search_hint": "自动模型选择 模型对比 prophet xgboost auto select best model",
        })
        registry.register_from_def({
            "name": "grid_search",
            "description": "网格搜索超参数",
            "call": optimization_tools.grid_search,
            "category": ToolCategory.OPTIMIZE,
            "tags": ["optimize", "grid search", "tuning"],
            "is_read_only": True,
            "when_to_use": "参数空间小时，穷举搜索最优超参数",
            "search_hint": "网格搜索 超参数调优 grid search tuning",
        })
        registry.register_from_def({
            "name": "random_search",
            "description": "随机搜索超参数",
            "call": optimization_tools.random_search,
            "category": ToolCategory.OPTIMIZE,
            "tags": ["optimize", "random search", "tuning"],
            "is_read_only": True,
            "when_to_use": "参数空间大时，随机采样找较优超参数",
            "search_hint": "随机搜索 超参数调优 random search",
        })
        registry.register_from_def({
            "name": "optuna_optimize",
            "description": "Optuna 贝叶斯超参数优化",
            "call": optimization_tools.optuna_optimize,
            "category": ToolCategory.OPTIMIZE,
            "tags": ["optimize", "optuna", "bayesian", "tuning"],
            "is_read_only": True,
            "when_to_use": "需要高效的贝叶斯优化搜索超参数时使用",
            "search_hint": "Optuna 贝叶斯优化 超参数 bayesian optimization",
        })
        pipeline_tools = [
            ("tune_best_model", "对最佳模型在验证集上做超参搜索", optimization_tools.tune_best_model),
            ("_tune_best_model", "工作流模板：超参搜索", optimization_tools.tune_best_model),
            ("predict_best_model", "用指定模型生成未来预测", optimization_tools.predict_best_model),
            ("_predict_best_model", "工作流模板：生成未来预测", optimization_tools.predict_best_model),
            ("_predict_final", "工作流模板：生成最终预测", optimization_tools.predict_best_model),
            ("evaluate_holdout", "在独立测试集上评估模型", optimization_tools.evaluate_holdout),
            ("_evaluate_holdout", "工作流模板：测试集评估", optimization_tools.evaluate_holdout),
            ("build_dataset_info", "构造报告用的数据集摘要", optimization_tools.build_dataset_info),
            ("_build_dataset_info", "工作流模板：数据集摘要", optimization_tools.build_dataset_info),
        ]
        for name, desc, fn in pipeline_tools:
            registry.register_from_def({
                "name": name,
                "description": desc,
                "call": fn,
                "category": ToolCategory.OPTIMIZE,
                "tags": ["optimize", "pipeline", "workflow"],
                "is_read_only": True,
                "when_to_use": desc,
                "search_hint": name.replace("_", " "),
            })
    except Exception as exc:
        logger.warning("forecast tool group skipped: %s", exc, exc_info=False)

    # --- 集成 ---
    try:
        from . import ensemble_tools
        registry.register_from_def({
            "name": "ensemble_average",
            "description": "平均/加权平均集成",
            "call": ensemble_tools.ensemble_average,
            "category": ToolCategory.MODEL,
            "tags": ["ensemble", "average", "weighted"],
            "is_read_only": True,
            "when_to_use": "组合多个模型的预测以提高稳定性",
            "search_hint": "平均集成 加权平均 ensemble average",
        })
        registry.register_from_def({
            "name": "ensemble_median",
            "description": "中位数集成（对异常值鲁棒）",
            "call": ensemble_tools.ensemble_median,
            "category": ToolCategory.MODEL,
            "tags": ["ensemble", "median", "robust"],
            "is_read_only": True,
            "when_to_use": "某些模型可能预测偏差大时用中位数集成",
            "search_hint": "中位数集成 ensemble median 鲁棒",
        })
        registry.register_from_def({
            "name": "ensemble_windsor",
            "description": "Windsor 平均集成（截尾平均）",
            "call": ensemble_tools.ensemble_windsor,
            "category": ToolCategory.MODEL,
            "tags": ["ensemble", "winsor", "trimmed mean"],
            "is_read_only": True,
            "when_to_use": "想减少极端预测的影响但保留多数模型的意见",
            "search_hint": "Windsor 截尾平均 trimmed mean ensemble",
        })
        registry.register_from_def({
            "name": "ensemble_best_on_validation",
            "description": "基于验证集表现的 Top-K 加权集成",
            "call": ensemble_tools.ensemble_best_on_validation,
            "category": ToolCategory.MODEL,
            "tags": ["ensemble", "validation", "weighted"],
            "is_read_only": True,
            "when_to_use": "有验证集时，根据验证集表现加权组合好模型",
            "search_hint": "验证集加权 最优集成 validation ensemble",
        })
        registry.register_from_def({
            "name": "ensemble_stacking",
            "description": "Stacking 集成（元模型学习组合权重）",
            "call": ensemble_tools.ensemble_stacking,
            "category": ToolCategory.MODEL,
            "tags": ["ensemble", "stacking", "meta model"],
            "is_read_only": True,
            "when_to_use": "需要学习最优组合方式的高级集成方法",
            "search_hint": "Stacking 元模型 堆叠 ensemble stacking",
        })
    except Exception as exc:
        logger.warning("forecast tool group skipped: %s", exc, exc_info=False)

    # --- 评估 ---
    try:
        from . import eval_tools
        registry.register_from_def({
            "name": "evaluate",
            "description": "多指标评估预测效果（RMSE/MAPE/MAE/MASE/方向准确率）",
            "call": eval_tools.evaluate,
            "category": ToolCategory.EVAL,
            "tags": ["eval", "metrics", "rmse", "mape", "mae"],
            "is_read_only": True,
            "when_to_use": "评估预测结果好坏时使用",
            "search_hint": "评估 指标 RMSE MAPE MAE 准确率",
        })
        registry.register_from_def({
            "name": "backtest_report",
            "description": "回测报告 — 分时段表现分析和稳定性检测",
            "call": eval_tools.backtest_report,
            "category": ToolCategory.EVAL,
            "tags": ["eval", "backtest", "stability"],
            "is_read_only": True,
            "when_to_use": "检查模型在不同时段的表现是否稳定时使用",
            "search_hint": "回测 稳定性 backtest 分段评估",
        })
        registry.register_from_def({
            "name": "compare_models",
            "description": "多模型对比排名",
            "call": eval_tools.compare_models,
            "category": ToolCategory.EVAL,
            "tags": ["eval", "compare", "ranking"],
            "is_read_only": True,
            "when_to_use": "比较多个模型的表现并排排名次时使用",
            "search_hint": "模型对比 排名 compare models ranking",
        })
    except Exception as exc:
        logger.warning("forecast tool group skipped: %s", exc, exc_info=False)

    # --- 可视化 ---
    try:
        from . import viz_tools
        registry.register_from_def({
            "name": "plot_forecast",
            "description": "绘制预测结果图（历史+预测+置信区间）",
            "call": viz_tools.plot_forecast,
            "category": ToolCategory.VIS,
            "tags": ["viz", "forecast", "plot"],
            "is_read_only": True,
            "when_to_use": "可视化预测结果时使用",
            "search_hint": "预测图 可视化 plot forecast chart",
        })
        registry.register_from_def({
            "name": "plot_decomposition",
            "description": "绘制时间序列分解图",
            "call": viz_tools.plot_decomposition,
            "category": ToolCategory.VIS,
            "tags": ["viz", "decompose", "trend", "seasonal"],
            "is_read_only": True,
            "when_to_use": "展示趋势/季节/残差成分时使用",
            "search_hint": "分解图 趋势 季节 残差 decomposition plot",
        })
        registry.register_from_def({
            "name": "plot_comparison",
            "description": "多模型预测对比图",
            "call": viz_tools.plot_comparison,
            "category": ToolCategory.VIS,
            "tags": ["viz", "comparison", "plot"],
            "is_read_only": True,
            "when_to_use": "对比多个模型的预测曲线时使用",
            "search_hint": "对比图 多模型 comparison plot",
        })
        registry.register_from_def({
            "name": "plot_metrics_bar",
            "description": "多模型评估指标柱状对比图",
            "call": viz_tools.plot_metrics_bar,
            "category": ToolCategory.VIS,
            "tags": ["viz", "metrics", "bar chart"],
            "is_read_only": True,
            "when_to_use": "用柱状图展示各模型指标对比时使用",
            "search_hint": "指标柱状图 bar chart metrics",
        })
        registry.register_from_def({
            "name": "plot_backtest_segments",
            "description": "分段回测表现图",
            "call": viz_tools.plot_backtest_segments,
            "category": ToolCategory.VIS,
            "tags": ["viz", "backtest", "segments"],
            "is_read_only": True,
            "when_to_use": "展示回测各分段表现趋势时使用",
            "search_hint": "回测分段图 segments backtest",
        })
    except Exception as exc:
        logger.warning("forecast tool group skipped: %s", exc, exc_info=False)

    # --- 报告 ---
    try:
        from . import report_tools
        registry.register_from_def({
            "name": "generate_forecast_report",
            "description": "生成完整的需求预测分析报告（Markdown 格式）",
            "call": report_tools.generate_forecast_report,
            "category": ToolCategory.REPORT,
            "tags": ["report", "markdown", "analysis"],
            "is_read_only": True,
            "when_to_use": "预测完成后生成结构化分析报告时使用",
            "search_hint": "生成报告 Markdown 分析报告 report",
        })
        registry.register_from_def({
            "name": "generate_summary_report",
            "description": "生成简短的预测摘要",
            "call": report_tools.generate_summary_report,
            "category": ToolCategory.REPORT,
            "tags": ["report", "summary"],
            "is_read_only": True,
            "when_to_use": "需要简洁的预测结果摘要时使用",
            "search_hint": "摘要 summary 简报",
        })
    except Exception as exc:
        logger.warning("forecast tool group skipped: %s", exc, exc_info=False)

    return registry
