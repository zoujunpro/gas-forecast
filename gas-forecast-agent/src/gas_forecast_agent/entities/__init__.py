"""能力驱动的标准实体解析。"""

from .models import EntityResolutionOutcome, EntityResolutionStatus
from .resolver import EntityResolver

__all__ = ["EntityResolutionOutcome", "EntityResolutionStatus", "EntityResolver"]
