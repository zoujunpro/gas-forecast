"""短期预测智能体模型集合。"""

from .model_city_gas_v1 import ModelCityGasV1Handler
from .model_power_generation_v1 import ModelPowerGenerationV1Handler
from .model_sector_gas_xgb_v1 import (
    ModelChemicalGasV1Handler,
    ModelCngV1Handler,
    ModelIndustrialFuelV1Handler,
)

__all__ = [
    "ModelCityGasV1Handler",
    "ModelChemicalGasV1Handler",
    "ModelCngV1Handler",
    "ModelIndustrialFuelV1Handler",
    "ModelPowerGenerationV1Handler",
]
