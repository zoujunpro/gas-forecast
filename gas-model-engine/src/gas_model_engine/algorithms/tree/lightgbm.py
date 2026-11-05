from ._base import TreeRegressorAlgorithm, tree_pipeline


def _factory(params):
    """按参数创建 LightGBM 回归流水线，并延迟导入可选依赖。"""
    from lightgbm import LGBMRegressor

    return tree_pipeline(LGBMRegressor(**params))


class LightGbmAlgorithm(TreeRegressorAlgorithm):
    """LightGBM：采用高效直方图和叶子优先生长策略的梯度提升树模型。"""

    code, name = "LIGHTGBM_V1", "LightGBM"
    defaults = {
        "n_estimators": 280,
        "learning_rate": 0.03,
        "num_leaves": 15,
        "min_child_samples": 15,
        "random_state": 42,
        "n_jobs": 1,
        "verbosity": -1,
    }
    factory = staticmethod(_factory)

    def _fit_booster_with_validation(
        self, model, x_train, y_train, x_validation, y_validation, sample_weight, rounds
    ):
        """使用训练末段验证集确定LightGBM最佳提升轮次。"""
        from lightgbm import early_stopping

        kwargs = {
            "eval_set": [(x_validation, y_validation)],
            "callbacks": [early_stopping(rounds, verbose=False)],
        }
        if sample_weight is not None:
            kwargs["sample_weight"] = sample_weight
        model.fit(x_train, y_train, **kwargs)
