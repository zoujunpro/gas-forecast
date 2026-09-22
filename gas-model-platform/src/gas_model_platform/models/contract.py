"""模型接入契约校验。

注册中心在应用启动时执行这些检查，让不符合平台标准的模型尽早失败，
避免请求进入运行阶段后才发现 Handler 缺方法或元数据不一致。
"""

from __future__ import annotations

import re
from typing import Any

from gas_model_platform.schemas.modeling import ModelInfo


MODEL_CODE_PATTERN = re.compile(
    r"^(?:MODEL_[A-Z0-9]+(?:_[A-Z0-9]+)*|WINTER_MODEL)_V[1-9]\d*\.\d+$"
)
SEMANTIC_VERSION_PATTERN = re.compile(r"^\d+\.\d+\.\d+$")
REQUIRED_CAPABILITIES = frozenset({"train", "backtest", "predict"})


class ModelContractError(ValueError):
    """模型未满足平台接入契约。"""


def validate_model_handler(model_code: str, handler: Any) -> None:
    """校验一个即将注册的模型 Handler。"""
    info = getattr(handler, "info", None)
    if not isinstance(info, ModelInfo):
        raise ModelContractError(f"{model_code} 的 handler.info 必须是 ModelInfo")
    if model_code != info.model_code:
        raise ModelContractError(
            "registry model_code does not match handler info: "
            f"{model_code} != {info.model_code}"
        )
    if not MODEL_CODE_PATTERN.fullmatch(model_code):
        raise ModelContractError(
            f"model_code 格式不正确: {model_code}; "
            "模型必须使用 <模型名称>_V<主版本>.<次版本>"
        )
    if not SEMANTIC_VERSION_PATTERN.fullmatch(info.model_version):
        raise ModelContractError(
            f"{model_code} 的 model_version 必须使用 x.y.z 格式"
        )
    capabilities = set(info.capabilities)
    if len(capabilities) != len(info.capabilities):
        raise ModelContractError(f"{model_code} 的 capabilities 不能重复")
    if capabilities != REQUIRED_CAPABILITIES:
        missing = sorted(REQUIRED_CAPABILITIES - capabilities)
        extra = sorted(capabilities - REQUIRED_CAPABILITIES)
        raise ModelContractError(
            f"{model_code} 的 capabilities 不完整; missing={missing}, extra={extra}"
        )
    for method_name in REQUIRED_CAPABILITIES:
        if not callable(getattr(handler, method_name, None)):
            raise ModelContractError(
                f"{model_code} 的 handler 缺少可调用方法: {method_name}"
            )
