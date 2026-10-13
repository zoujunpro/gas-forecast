"""旧江苏电力模型产物的反序列化兼容层。

发电模型通用化后，历史 ``joblib`` 产物仍会按 pickle 协议导入本模块中的
类名。保留这些别名可让旧产物加载为当前通用发电模型的数据类型，而无需
修改或重新训练已经生成的模型产物。
"""

from .model_power_generation_v1 import (
    ModelPowerGenerationV1Handler,
    PowerGenerationV1Artifact,
    PowerGenerationV1TrainingOutput,
)


JiangshuDianliV1Artifact = PowerGenerationV1Artifact
JiangshuDianliV1TrainingOutput = PowerGenerationV1TrainingOutput
ModelJiangshuDianliV1Handler = ModelPowerGenerationV1Handler


__all__ = [
    "JiangshuDianliV1Artifact",
    "JiangshuDianliV1TrainingOutput",
    "ModelJiangshuDianliV1Handler",
]
