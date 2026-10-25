"""主数据实体解析结果，可在调试器直接查看编码、候选和问题。"""

from enum import StrEnum
from pydantic import Field

from .operation_models import EntityParameters, RuntimeModel
from .schemas import ResolutionIssue


class EntityType(StrEnum):
    CUSTOMER = "CUSTOMER"
    REGION = "REGION"
    INDUSTRY = "INDUSTRY"


class EntityMatchStatus(StrEnum):
    UNIQUE = "UNIQUE"
    MULTIPLE = "MULTIPLE"
    NOT_FOUND = "NOT_FOUND"
    AMBIGUOUS = "AMBIGUOUS"


class EntityCandidate(RuntimeModel):
    type: EntityType | None = None
    code: str
    name: str
    description: str | None = None
    region_code: str | None = None
    region_name: str | None = None
    industry_code: str | None = None
    industry_name: str | None = None

    @property
    def display_name(self) -> str:
        scope = " / ".join(item for item in (self.region_name, self.industry_name) if item)
        return f"{scope} / {self.name}" if scope else (
            f"{self.name}（{self.description}）" if self.description else self.name
        )

    def parameters(self, entity_type: EntityType) -> EntityParameters:
        if entity_type == EntityType.CUSTOMER:
            return EntityParameters(customer_codes=[self.code], customer_names=[self.name])
        if entity_type == EntityType.REGION:
            return EntityParameters(region_codes=[self.code], region_names=[self.name])
        return EntityParameters(industry_codes=[self.code], industry_names=[self.name])


class EntityResolutionItem(RuntimeModel):
    entity_type: EntityType
    status: EntityMatchStatus
    input_name: str | None = None
    message: str | None = None
    candidates: list[EntityCandidate] = Field(default_factory=list)


class EntityLookupResponse(RuntimeModel):
    """Java 实体解析内部接口的响应结构。"""

    status: EntityMatchStatus
    message: str | None = None
    proposed_parameters: EntityParameters | None = None
    items: list[EntityResolutionItem] = Field(default_factory=list)


class EntityResolution(RuntimeModel):
    """本轮已验证的实体、待选候选及仍然存在的问题。"""

    parameters: EntityParameters = Field(default_factory=EntityParameters)
    items: list[EntityResolutionItem] = Field(default_factory=list)
    issues: list[ResolutionIssue] = Field(default_factory=list)
