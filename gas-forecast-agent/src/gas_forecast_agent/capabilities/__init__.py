"""业务能力目录及加载器。"""

from .models import CapabilityDefinition, CapabilityStatus
from .registry import CapabilityRegistry

__all__ = ["CapabilityDefinition", "CapabilityRegistry", "CapabilityStatus"]
