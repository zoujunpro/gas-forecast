from ._base import TreeRegressorAlgorithm, tree_pipeline


def _factory(params):
    """按参数创建 XGBoost 回归流水线，并延迟导入可选依赖。"""
    from xgboost import XGBRegressor

    return tree_pipeline(XGBRegressor(**params))


class XGBoostAlgorithm(TreeRegressorAlgorithm):
    """XGBoost：带正则化和二阶梯度优化的高性能梯度提升树模型。"""

    code, name = "XGBOOST_V1", "XGBoost"
    defaults = {
        "n_estimators": 300,
        "learning_rate": 0.03,
        "max_depth": 4,
        "min_child_weight": 3,
        "objective": "reg:squarederror",
        "random_state": 42,
        "n_jobs": 1,
        "tree_method": "hist",
    }
    factory = staticmethod(_factory)

    def _fit_booster_with_validation(
        self, model, x_train, y_train, x_validation, y_validation, sample_weight, rounds
    ):
        """使用训练末段验证集确定XGBoost最佳提升轮次。"""
        model.set_params(early_stopping_rounds=rounds)
        kwargs = {"eval_set": [(x_validation, y_validation)], "verbose": False}
        if sample_weight is not None:
            kwargs["sample_weight"] = sample_weight
        model.fit(x_train, y_train, **kwargs)
