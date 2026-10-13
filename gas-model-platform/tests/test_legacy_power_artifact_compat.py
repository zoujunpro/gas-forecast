from gas_model_platform.models.short_agent.model_jiangshu_dianli_v1 import (
    JiangshuDianliV1Artifact,
    JiangshuDianliV1TrainingOutput,
    ModelJiangshuDianliV1Handler,
)
from gas_model_platform.models.short_agent.model_power_generation_v1 import (
    ModelPowerGenerationV1Handler,
    PowerGenerationV1Artifact,
    PowerGenerationV1TrainingOutput,
)


def test_legacy_jiangsu_power_pickle_types_map_to_current_types() -> None:
    assert JiangshuDianliV1Artifact is PowerGenerationV1Artifact
    assert JiangshuDianliV1TrainingOutput is PowerGenerationV1TrainingOutput
    assert ModelJiangshuDianliV1Handler is ModelPowerGenerationV1Handler
