"""
Tool 系统 v3.0 — 三层工具抽象

参考 Grok Build 的 xai-tool-runtime 设计，实现 Python 版三层抽象：
- 第1层：BaseTool 强类型基类（pydantic schema、同步/异步双模式、上下文可见性）
- 第2层：ToolDyn 类型擦除（dict 输入输出、自动 schema 校验）
- 第3层：流式输出协议（Progress* + Terminal(Result) 不变式）

同时保留 v2.0 的 build_tool / Tool / ToolDef / ToolRegistry / ToolResult
以保证完全向后兼容。
"""

from __future__ import annotations

import inspect
import json
import re
from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from enum import Enum
from typing import Any, Callable, Optional, Union, AsyncGenerator, Type


# ============================================
# v2.0 工具分类（保留，向后兼容）
# ============================================

class ToolCategory(str, Enum):
    """
    工具分类枚举

    v3.0 扩展了分类，同时保持对 v2.0 分类名的兼容：
    - v2.0 的 DATA 等同 v3.0 的 DATA_FETCH
    - v2.0 的 FEATURE 等同 v3.0 的 FEATURE_ENG
    - v2.0 的 MODEL / OPTIMIZE / EVAL 归入 v3.0 的 ANALYSIS
    """
    # --- v3.0 标准分类 ---
    DATA_FETCH = "data_fetch"
    FEATURE_ENG = "feature_eng"
    BACKTEST = "backtest"
    RISK = "risk"
    TRADING = "trading"
    ANALYSIS = "analysis"
    VISUALIZATION = "visualization"
    REPORT = "report"
    SYSTEM = "system"

    # --- v2.0 兼容别名（value 与 v2.0 一致） ---
    DATA = "data"
    FEATURE = "feature"
    MODEL = "model"
    OPTIMIZE = "optimize"
    EVAL = "eval"
    VIS = "visualization"
    UTIL = "utility"
    OTHER = "other"


# v2 分类到 v3 分类的映射，用于桥接
_V2_TO_V3_CATEGORY = {
    ToolCategory.DATA: ToolCategory.DATA_FETCH,
    ToolCategory.FEATURE: ToolCategory.FEATURE_ENG,
    ToolCategory.MODEL: ToolCategory.ANALYSIS,
    ToolCategory.OPTIMIZE: ToolCategory.ANALYSIS,
    ToolCategory.EVAL: ToolCategory.ANALYSIS,
    ToolCategory.VIS: ToolCategory.VISUALIZATION,
    ToolCategory.REPORT: ToolCategory.REPORT,
    ToolCategory.UTIL: ToolCategory.SYSTEM,
    ToolCategory.OTHER: ToolCategory.SYSTEM,
}


def normalize_category(cat: Union[str, ToolCategory, None]) -> ToolCategory:
    """将各种形式的分类值统一为 ToolCategory 枚举"""
    if cat is None:
        return ToolCategory.OTHER
    if isinstance(cat, ToolCategory):
        return cat
    if isinstance(cat, str):
        try:
            return ToolCategory(cat)
        except ValueError:
            # 尝试模糊匹配
            cat_lower = cat.lower().replace("-", "_").replace(" ", "_")
            for tc in ToolCategory:
                if tc.value == cat_lower or tc.name.lower() == cat_lower:
                    return tc
            return ToolCategory.OTHER
    return ToolCategory.OTHER


# ============================================
# v2.0 工具进度类型（保留）
# ============================================

class ToolProgressType(str, Enum):
    NORMAL = "normal"
    DETAILED = "detailed"
    MINIMAL = "minimal"


# ============================================
# v2.0 工具执行结果（保留，向后兼容）
# ============================================

class ToolResult:
    """
    工具执行结果

    工具函数可以直接返回任意值，会被自动包装成 ToolResult。
    也可以显式返回 ToolResult 以携带更多信息。
    """

    def __init__(
        self,
        data: Any,
        new_messages: Optional[list[Union[str, dict]]] = None,
        success: bool = True,
        error: Optional[str] = None,
        metadata: Optional[dict] = None,
    ):
        self.data = data
        self.new_messages = new_messages or []
        self.success = success
        self.error = error
        self.metadata = metadata or {}

    def __str__(self):
        return str(self.data)

    def __repr__(self):
        status = "OK" if self.success else "FAIL"
        return f"<ToolResult {status}: {type(self.data).__name__}>"

    def to_dict(self) -> dict:
        """序列化为字典"""
        return {
            "success": self.success,
            "data": self._json_safe(self.data),
            "error": self.error,
            "metadata": self.metadata,
        }

    @classmethod
    def from_dict(cls, d: dict) -> "ToolResult":
        """从字典反序列化"""
        return cls(
            data=d.get("data"),
            success=d.get("success", True),
            error=d.get("error"),
            metadata=d.get("metadata", {}),
        )

    @staticmethod
    def _json_safe(value: Any) -> Any:
        """转换为 JSON 安全的类型"""
        if value is None or isinstance(value, (bool, int, float, str)):
            return value
        if isinstance(value, (list, tuple)):
            return [ToolResult._json_safe(v) for v in value]
        if isinstance(value, dict):
            return {str(k): ToolResult._json_safe(v) for k, v in value.items()}
        try:
            return json.loads(json.dumps(value, default=str))
        except Exception:
            return str(value)


# ============================================
# v2.0 工具定义（保留）
# ============================================

@dataclass
class ToolDef:
    """
    声明式工具定义

    所有工具通过 ToolDef + build_tool() 创建，
    不通过继承 Tool 类。
    """
    name: str
    description: str
    call: Callable

    # 参数 schema（JSON Schema 格式，可选）
    input_schema: Optional[dict] = None

    # 安全标记
    is_read_only: bool = False
    is_destructive: bool = False
    is_concurrency_safe: bool = False

    # 分类与元数据
    category: ToolCategory = ToolCategory.OTHER
    tags: list[str] = field(default_factory=list)

    # 别名（搜索用）
    aliases: list[str] = field(default_factory=list)
    search_hint: str = ""
    when_to_use: str = ""

    # 验证与权限
    validate_input: Optional[Callable] = None
    check_permissions: Optional[Callable] = None

    # 启用控制
    is_enabled: Callable[..., bool] = field(default=lambda: True)

    # 输出控制
    max_result_size_chars: int = 50000

    # 超时（秒，0 表示不限制）
    timeout: float = 0.0


# ============================================
# v2.0 工具实例（保留）
# ============================================

class Tool:
    """
    工具实例 — 基于 ToolDef 构建

    通过 __getattr__ 代理 ToolDef 的属性，
    这样 ToolDef 加字段不需要改 Tool 类。
    """

    def __init__(self, tool_def: ToolDef):
        self._def = tool_def

    def __getattr__(self, name):
        if name.startswith("_"):
            raise AttributeError(name)
        if hasattr(self._def, name):
            return getattr(self._def, name)
        raise AttributeError(f"'Tool' object has no attribute '{name}'")

    def __call__(self, *args, **kwargs) -> ToolResult:
        """调用工具，自动包装结果"""
        # 输入验证
        if self._def.validate_input:
            try:
                self._def.validate_input(*args, **kwargs)
            except Exception as e:
                return ToolResult(
                    data=None,
                    success=False,
                    error=f"Input validation failed: {e}",
                )

        # 执行
        try:
            result = self._def.call(*args, **kwargs)
        except Exception as e:
            return ToolResult(
                data=None,
                success=False,
                error=str(e),
            )

        # 自动包装
        if not isinstance(result, ToolResult):
            result = ToolResult(data=result)

        return result

    def matches_name(self, name: str) -> bool:
        """检查工具名或别名是否匹配"""
        name_lower = name.lower()
        if self.name.lower() == name_lower:
            return True
        return any(a.lower() == name_lower for a in self.aliases)

    def to_openai_schema(self) -> dict:
        """
        转换为 OpenAI 函数调用 schema 格式
        用于给 LLM 做 tool calling
        """
        properties = {}
        required = []

        if self.input_schema and "properties" in self.input_schema:
            properties = self.input_schema["properties"]
            required = self.input_schema.get("required", [])
        else:
            # 从函数签名自动推断（简单版）
            sig = inspect.signature(self._def.call)
            for param_name, param in sig.parameters.items():
                if param_name in ("self", "cls"):
                    continue
                param_type = "string"
                if param.annotation in (int,):
                    param_type = "integer"
                elif param.annotation in (float,):
                    param_type = "number"
                elif param.annotation in (bool,):
                    param_type = "boolean"
                elif param.annotation in (list, dict):
                    param_type = "object"
                properties[param_name] = {
                    "type": param_type,
                    "description": "",
                }
                if param.default is inspect.Parameter.empty:
                    required.append(param_name)

        return {
            "type": "function",
            "function": {
                "name": self.name,
                "description": self.description,
                "parameters": {
                    "type": "object",
                    "properties": properties,
                    "required": required,
                },
            },
        }

    def __repr__(self):
        return f"<Tool '{self.name}' [{self.category.value}]>"


# ============================================
# v2.0 默认值与工厂（保留）
# ============================================

TOOL_DEFAULTS = {
    "input_schema": None,
    "is_read_only": False,
    "is_destructive": False,
    "is_concurrency_safe": False,
    "category": ToolCategory.OTHER,
    "tags": [],
    "aliases": [],
    "search_hint": "",
    "when_to_use": "",
    "validate_input": None,
    "check_permissions": None,
    "is_enabled": lambda: True,
    "max_result_size_chars": 50000,
    "timeout": 0.0,
}


def build_tool(definition: dict) -> Tool:
    """
    工具工厂函数 — 统一的工具创建入口

    自动填充默认值，标准化创建流程。
    """
    merged = {**TOOL_DEFAULTS, **definition}

    # category 可以传字符串，自动转 enum
    merged["category"] = normalize_category(merged.get("category"))

    tool_def = ToolDef(
        name=merged["name"],
        description=merged["description"],
        call=merged["call"],
        input_schema=merged["input_schema"],
        is_read_only=merged["is_read_only"],
        is_destructive=merged["is_destructive"],
        is_concurrency_safe=merged["is_concurrency_safe"],
        category=merged["category"],
        tags=list(merged["tags"]),
        aliases=list(merged["aliases"]),
        search_hint=merged["search_hint"],
        when_to_use=merged["when_to_use"],
        validate_input=merged["validate_input"],
        check_permissions=merged["check_permissions"],
        is_enabled=merged["is_enabled"],
        max_result_size_chars=merged["max_result_size_chars"],
        timeout=merged["timeout"],
    )

    return Tool(tool_def)


# ============================================
# v2.0 工具注册表（保留，向后兼容）
# ============================================

class ToolRegistry:
    """
    工具注册中心（v2.0 版本，向后兼容）

    统一管理所有工具，支持：
    - 注册 / 注销
    - 按名称获取
    - 按分类筛选
    - 搜索
    - 批量注册
    - 导出 LLM schema

    注：v3.0 推荐使用 tools.registry.ToolRegistryV3
    """

    def __init__(self):
        self._tools: dict[str, Tool] = {}

    # ---- 注册 ----

    def register(self, tool: Tool) -> Tool:
        """注册一个工具，返回工具本身（链式调用）"""
        self._tools[tool.name] = tool
        for alias in tool.aliases:
            # 别名不覆盖主名
            if alias not in self._tools:
                self._tools[alias] = tool
        return tool

    def register_from_def(self, definition: dict) -> Tool:
        """从定义字典注册工具"""
        tool = build_tool(definition)
        return self.register(tool)

    def register_many(self, tools: list[Tool]) -> None:
        """批量注册"""
        for t in tools:
            self.register(t)

    def unregister(self, name: str) -> bool:
        """注销工具（同时注销别名）"""
        tool = self._tools.get(name)
        if not tool:
            return False
        keys_to_remove = [
            k for k, v in self._tools.items() if v is tool
        ]
        for k in keys_to_remove:
            del self._tools[k]
        return True

    # ---- 查询 ----

    def get(self, name: str) -> Optional[Tool]:
        """按名称获取工具"""
        return self._tools.get(name)

    def __getitem__(self, name: str) -> Tool:
        tool = self.get(name)
        if tool is None:
            raise KeyError(f"Tool not found: {name}")
        return tool

    def __contains__(self, name: str) -> bool:
        return name in self._tools

    def list_all(self) -> list[Tool]:
        """列出所有工具（去重）"""
        seen: set[str] = set()
        tools: list[Tool] = []
        for name, tool in self._tools.items():
            if tool.name not in seen:
                seen.add(tool.name)
                tools.append(tool)
        return tools

    def list_tools(self, agent_role: Optional[str] = None, category: Optional[ToolCategory] = None):
        """
        兼容 v3 ToolRegistryV3.list_tools 的查询接口。

        v2 注册表没有角色可见性，agent_role 会被忽略。
        """
        tools = self.list_all()
        if category is not None:
            tools = [t for t in tools if t.category == category]
        return tools

    def list_by_category(self, category: ToolCategory) -> list[Tool]:
        """按分类列出工具"""
        return [t for t in self.list_all() if t.category == category]

    def search(self, query: str) -> list[Tool]:
        """
        搜索工具

        匹配：名称、描述、别名、标签、搜索提示、使用建议
        """
        query_lower = query.lower()
        results: list[tuple[Tool, int]] = []

        for tool in self.list_all():
            score = 0
            if query_lower == tool.name.lower():
                score += 100
            elif query_lower in tool.name.lower():
                score += 50

            if query_lower in tool.description.lower():
                score += 20

            for alias in tool.aliases:
                if query_lower in alias.lower():
                    score += 30

            for tag in tool.tags:
                if query_lower in tag.lower():
                    score += 15

            if query_lower in tool.search_hint.lower():
                score += 25

            if query_lower in tool.when_to_use.lower():
                score += 15

            if score > 0:
                results.append((tool, score))

        # 按分数降序
        results.sort(key=lambda x: x[1], reverse=True)
        return [t for t, _ in results]

    # ---- 导出 ----

    def to_openai_schemas(self, category: Optional[ToolCategory] = None) -> list[dict]:
        """导出所有工具的 OpenAI function calling schema"""
        tools = self.list_by_category(category) if category else self.list_all()
        return [t.to_openai_schema() for t in tools if t.is_enabled()]

    # ---- 统计 ----

    @property
    def count(self) -> int:
        """工具总数（去重）"""
        return len(self.list_all())

    def summary(self) -> dict:
        """工具分类统计"""
        result = {}
        for tool in self.list_all():
            cat = tool.category.value
            result[cat] = result.get(cat, 0) + 1
        return result

    def __repr__(self):
        return f"<ToolRegistry {self.count} tools>"


# ============================================================================
# v3.0 三层工具抽象
# ============================================================================

# ============================================
# 第1层：BaseTool 强类型基类
# ============================================

class ToolProgressKind(str, Enum):
    """进度事件类型"""
    TEXT = "text"          # 文本进度
    CONTENT = "content"    # 结构化内容进度
    CUSTOM = "custom"      # 自定义进度事件


class ToolProgress:
    """
    工具执行进度事件

    支持三种类型：
    - text: 纯文本进度信息
    - content: 结构化内容（dict）
    - custom: 自定义事件，由工具自行定义 payload
    """

    def __init__(
        self,
        kind: ToolProgressKind = ToolProgressKind.TEXT,
        text: Optional[str] = None,
        content: Optional[Any] = None,
        custom: Optional[dict] = None,
        percent: Optional[float] = None,
        step: Optional[int] = None,
        total_steps: Optional[int] = None,
    ):
        self.kind = kind
        self.text = text
        self.content = content
        self.custom = custom
        self.percent = percent
        self.step = step
        self.total_steps = total_steps

    def to_dict(self) -> dict:
        """序列化为字典"""
        result: dict[str, Any] = {"type": "progress", "kind": self.kind.value}
        if self.text is not None:
            result["text"] = self.text
        if self.content is not None:
            result["content"] = ToolResult._json_safe(self.content)
        if self.custom is not None:
            result["custom"] = ToolResult._json_safe(self.custom)
        if self.percent is not None:
            result["percent"] = self.percent
        if self.step is not None:
            result["step"] = self.step
        if self.total_steps is not None:
            result["total_steps"] = self.total_steps
        return result

    @classmethod
    def from_dict(cls, d: dict) -> "ToolProgress":
        """从字典反序列化"""
        return cls(
            kind=ToolProgressKind(d.get("kind", "text")),
            text=d.get("text"),
            content=d.get("content"),
            custom=d.get("custom"),
            percent=d.get("percent"),
            step=d.get("step"),
            total_steps=d.get("total_steps"),
        )

    def __repr__(self):
        return f"<ToolProgress {self.kind.value} text={self.text!r}>"


class BaseTool(ABC):
    """
    v3.0 强类型工具基类

    所有 v3 工具都继承此类，实现 run() 方法即可获得：
    - 自动 schema 校验（args_schema 为 pydantic model 或 dict）
    - 同步/异步双模式（execute 异步生成器默认包装 run）
    - 上下文感知可见性（should_list）
    - 丰富的元数据（category / aliases / search_hint / when_to_use）
    - 流式输出协议（Progress* + Terminal(Result)）

    子类需实现：
        run(**kwargs) -> ToolResult

    可选重写：
        execute(**kwargs) -> AsyncGenerator[ToolProgress | ToolResult, None]
            提供流式进度，最后 yield ToolResult

        should_list(agent_role: str) -> bool
            根据 agent 角色决定工具是否可见
    """

    # ---- 类属性（子类直接声明） ----
    name: str = ""
    description: str = ""
    category: ToolCategory = ToolCategory.OTHER
    is_read_only: bool = False
    is_destructive: bool = False
    args_schema: Optional[Union[Type[Any], dict]] = None
    output_schema: Optional[Union[Type[Any], dict]] = None
    when_to_use: str = ""
    aliases: list[str] = []
    search_hint: str = ""

    # ---- 公共方法 ----

    @abstractmethod
    def run(self, **kwargs) -> ToolResult:
        """
        同步执行工具

        参数由 args_schema 定义，返回 ToolResult。
        子类必须实现此方法。
        """
        ...

    async def execute(self, **kwargs) -> AsyncGenerator[Union[ToolProgress, ToolResult], None]:
        """
        异步执行工具，流式输出

        默认实现：发送一个 "开始" 进度事件，然后调用 run()，最后返回结果。
        子类可以重写此方法以提供细粒度的进度反馈。

        不变式：yield N 个 ToolProgress，最后 yield 一个 ToolResult
        """
        yield ToolProgress(
            kind=ToolProgressKind.TEXT,
            text=f"开始执行 {self.name}...",
        )
        # 在协程中执行同步方法（避免阻塞事件循环，用同步直接调用）
        result = self.run(**kwargs)
        yield result

    def should_list(self, agent_role: str = "") -> bool:
        """
        上下文感知可见性 — 根据 agent 角色决定工具是否列出

        默认全部可见，子类可按 agent_role 过滤。
        """
        return True

    # ---- Schema 辅助 ----

    def args_schema_dict(self) -> Optional[dict]:
        """获取 args_schema 的 JSON Schema dict 形式"""
        return _schema_to_dict(self.args_schema)

    def output_schema_dict(self) -> Optional[dict]:
        """获取 output_schema 的 JSON Schema dict 形式"""
        return _schema_to_dict(self.output_schema)

    def validate_args(self, kwargs: dict) -> tuple[bool, Optional[str]]:
        """
        校验输入参数

        返回 (是否通过, 错误信息)
        """
        if self.args_schema is None:
            return True, None
        return _validate_dict_against_schema(kwargs, self.args_schema)

    def to_openai_schema(self) -> dict:
        """转换为 OpenAI function calling 格式"""
        schema_dict = self.args_schema_dict() or {"type": "object", "properties": {}, "required": []}
        properties = schema_dict.get("properties", {})
        required = schema_dict.get("required", [])
        return {
            "type": "function",
            "function": {
                "name": self.name,
                "description": self.description,
                "parameters": {
                    "type": "object",
                    "properties": properties,
                    "required": required,
                },
            },
        }

    def __repr__(self):
        return f"<BaseTool '{self.name}' [{self.category.value}]>"


def _schema_to_dict(schema: Optional[Union[Type[Any], dict]]) -> Optional[dict]:
    """将 schema（pydantic model 或 dict）转换为 JSON Schema dict"""
    if schema is None:
        return None
    if isinstance(schema, dict):
        return schema
    # pydantic v2 / v1 model schema
    try:
        if hasattr(schema, "model_json_schema"):
            return schema.model_json_schema()
        if hasattr(schema, "schema"):
            return schema.schema()
    except Exception:
        pass
    return None


def _validate_dict_against_schema(data: dict, schema: Union[Type[Any], dict]) -> tuple[bool, Optional[str]]:
    """
    用 schema 校验 dict 数据

    支持 pydantic model 和 JSON Schema dict。
    返回 (是否通过, 错误信息)
    """
    if isinstance(schema, dict):
        # 简单校验：检查必填字段和类型（轻量级，不引入 jsonschema 依赖）
        required = schema.get("required", [])
        properties = schema.get("properties", {})
        for field_name in required:
            if field_name not in data:
                return False, f"Missing required field: {field_name}"
        for field_name, value in data.items():
            if field_name in properties:
                prop = properties[field_name]
                expected_type = prop.get("type")
                if expected_type and not _check_json_type(value, expected_type):
                    return False, f"Field '{field_name}' expected type {expected_type}, got {type(value).__name__}"
        return True, None

    # pydantic model
    try:
        if hasattr(schema, "model_validate"):
            schema.model_validate(data)
            return True, None
        if hasattr(schema, "parse_obj"):
            schema.parse_obj(data)
            return True, None
    except Exception as e:
        return False, str(e)

    return True, None


def _check_json_type(value: Any, type_name: str) -> bool:
    """检查值是否符合 JSON Schema 类型"""
    if type_name == "string":
        return isinstance(value, str)
    if type_name == "integer":
        return isinstance(value, int) and not isinstance(value, bool)
    if type_name == "number":
        return isinstance(value, (int, float)) and not isinstance(value, bool)
    if type_name == "boolean":
        return isinstance(value, bool)
    if type_name == "array":
        return isinstance(value, list)
    if type_name == "object":
        return isinstance(value, dict)
    if type_name == "null":
        return value is None
    return True  # 未知类型放过


# ============================================
# 第2层：ToolDyn 类型擦除包装器
# ============================================

class ToolDyn:
    """
    类型擦除的工具包装器

    将 BaseTool（或 v2 Tool、任意可调用对象）包装为统一的 dict 接口：
    - run_json(args: dict) -> dict
    - execute_json(args: dict) -> AsyncGenerator[dict, None]

    内部自动做 schema 校验和结果序列化，
    是注册表和 Agent 调用层的标准接口。
    """

    def __init__(self, tool: Union[BaseTool, Tool, Callable]):
        self._tool = tool
        self._kind = self._detect_kind(tool)

    @staticmethod
    def _detect_kind(tool: Any) -> str:
        if isinstance(tool, BaseTool):
            return "basetool"
        if isinstance(tool, Tool):
            return "v2tool"
        if callable(tool):
            return "callable"
        raise TypeError(f"Unsupported tool type: {type(tool)}")

    # ---- 属性代理 ----

    @property
    def name(self) -> str:
        if self._kind == "basetool":
            return self._tool.name
        if self._kind == "v2tool":
            return self._tool.name
        if self._kind == "callable":
            return getattr(self._tool, "__name__", "anonymous_tool")
        return ""

    @property
    def description(self) -> str:
        if self._kind == "basetool":
            return self._tool.description
        if self._kind == "v2tool":
            return self._tool.description
        if self._kind == "callable":
            return getattr(self._tool, "__doc__", "") or ""
        return ""

    @property
    def category(self) -> ToolCategory:
        if self._kind == "basetool":
            return normalize_category(self._tool.category)
        if self._kind == "v2tool":
            return normalize_category(self._tool.category)
        return ToolCategory.OTHER

    @property
    def is_read_only(self) -> bool:
        if self._kind in ("basetool", "v2tool"):
            return self._tool.is_read_only
        return False

    @property
    def is_destructive(self) -> bool:
        if self._kind in ("basetool", "v2tool"):
            return self._tool.is_destructive
        return False

    @property
    def aliases(self) -> list[str]:
        if self._kind in ("basetool", "v2tool"):
            return self._tool.aliases
        return []

    @property
    def search_hint(self) -> str:
        if self._kind in ("basetool", "v2tool"):
            return self._tool.search_hint
        return ""

    @property
    def when_to_use(self) -> str:
        if self._kind in ("basetool", "v2tool"):
            return self._tool.when_to_use
        return ""

    # ---- 核心方法 ----

    def run_json(self, args: dict) -> dict:
        """
        用 dict 输入输出同步调用工具

        自动做输入 schema 校验、执行、结果序列化。
        """
        # 输入校验
        if self._kind == "basetool":
            ok, err = self._tool.validate_args(args)
            if not ok:
                return ToolResult(
                    data=None,
                    success=False,
                    error=f"Args validation failed: {err}",
                ).to_dict()
            result = self._tool.run(**args)
        elif self._kind == "v2tool":
            result = self._tool(**args)
        else:  # callable
            try:
                raw = self._tool(**args)
                result = raw if isinstance(raw, ToolResult) else ToolResult(data=raw)
            except Exception as e:
                result = ToolResult(data=None, success=False, error=str(e))

        if not isinstance(result, ToolResult):
            result = ToolResult(data=result)
        return result.to_dict()

    async def execute_json(self, args: dict) -> AsyncGenerator[dict, None]:
        """
        异步生成器版本，json 格式的进度和结果

        不变式：N 个 progress dict + 1 个 result dict
        """
        if self._kind == "basetool":
            ok, err = self._tool.validate_args(args)
            if not ok:
                yield ToolResult(
                    data=None,
                    success=False,
                    error=f"Args validation failed: {err}",
                ).to_dict()
                return
            async for event in self._tool.execute(**args):
                if isinstance(event, ToolProgress):
                    yield event.to_dict()
                elif isinstance(event, ToolResult):
                    yield event.to_dict()
                else:
                    yield ToolResult(data=event).to_dict()
        elif self._kind == "v2tool":
            # v2 工具没有原生异步，包装为 terminal_only
            result = self._tool(**args)
            if not isinstance(result, ToolResult):
                result = ToolResult(data=result)
            yield result.to_dict()
        else:  # callable
            try:
                raw = self._tool(**args)
                result = raw if isinstance(raw, ToolResult) else ToolResult(data=raw)
            except Exception as e:
                result = ToolResult(data=None, success=False, error=str(e))
            yield result.to_dict()

    def __call__(self, *args, **kwargs) -> "ToolResult":
        """
        让 ToolDyn 可以像 v2 Tool 一样直接调用，返回 ToolResult。

        工作流引擎和 ForecastAgent 都使用 tool(**params) 形式。
        """
        if args:
            raise TypeError("ToolDyn 请使用关键字参数调用，例如 tool(x=1)")
        if self._kind == "v2tool":
            result = self._tool(**kwargs)
        elif self._kind == "basetool":
            result = self._tool.run(**kwargs)
        else:
            try:
                result = self._tool(**kwargs)
            except Exception as e:
                result = ToolResult(data=None, success=False, error=str(e))
        if not isinstance(result, ToolResult):
            result = ToolResult(data=result)
        return result

    def should_list(self, agent_role: str = "") -> bool:
        """上下文感知可见性"""
        if self._kind == "basetool":
            return self._tool.should_list(agent_role)
        if self._kind == "v2tool":
            is_enabled = getattr(self._tool, "is_enabled", None)
            if callable(is_enabled):
                try:
                    return is_enabled()
                except Exception:
                    return True
            return True
        return True

    def to_openai_schema(self) -> dict:
        """导出 OpenAI function calling schema"""
        if self._kind == "basetool":
            return self._tool.to_openai_schema()
        if self._kind == "v2tool":
            return self._tool.to_openai_schema()
        # callable: 从签名推断
        sig = inspect.signature(self._tool)
        properties = {}
        required = []
        for param_name, param in sig.parameters.items():
            if param_name in ("self", "cls"):
                continue
            param_type = "string"
            if param.annotation in (int,):
                param_type = "integer"
            elif param.annotation in (float,):
                param_type = "number"
            elif param.annotation in (bool,):
                param_type = "boolean"
            elif param.annotation in (list, dict):
                param_type = "object"
            properties[param_name] = {"type": param_type, "description": ""}
            if param.default is inspect.Parameter.empty:
                required.append(param_name)
        return {
            "type": "function",
            "function": {
                "name": self.name,
                "description": self.description,
                "parameters": {
                    "type": "object",
                    "properties": properties,
                    "required": required,
                },
            },
        }

    def matches_name(self, name: str) -> bool:
        """检查名称或别名是否匹配"""
        name_lower = name.lower()
        if self.name.lower() == name_lower:
            return True
        return any(a.lower() == name_lower for a in self.aliases)

    @property
    def raw(self) -> Any:
        """获取原始工具对象（用于桥接）"""
        return self._tool

    def __repr__(self):
        return f"<ToolDyn '{self.name}' [{self.category.value}]>"


# ============================================
# 第3层：流式输出协议工具函数
# ============================================

async def terminal_only(result: Union[ToolResult, Any]) -> AsyncGenerator[dict, None]:
    """
    构造只有 terminal（最终结果）的流

    对于快速工具或不需要进度反馈的场景，
    直接 yield 一个 result dict。
    """
    if not isinstance(result, ToolResult):
        result = ToolResult(data=result)
    yield result.to_dict()


def is_terminal_event(event: dict) -> bool:
    """判断事件是否为 terminal（最终结果）"""
    return event.get("type") != "progress"


def is_progress_event(event: dict) -> bool:
    """判断事件是否为进度事件"""
    return event.get("type") == "progress"


async def collect_stream(
    stream: AsyncGenerator[dict, None],
) -> tuple[list[dict], dict]:
    """
    收集完整流，返回 (progress_events, terminal_result)

    辅助函数：用于测试或需要完整结果的场景。
    """
    progress_events: list[dict] = []
    terminal: dict = {}
    async for event in stream:
        if is_progress_event(event):
            progress_events.append(event)
        else:
            terminal = event
    return progress_events, terminal


# ============================================
# ToolInfo — 工具列表元信息
# ============================================

@dataclass
class ToolInfo:
    """工具列表信息（不含实现，用于 list_tools 返回）"""
    name: str
    description: str
    category: ToolCategory
    is_read_only: bool = False
    is_destructive: bool = False
    when_to_use: str = ""
    aliases: list[str] = field(default_factory=list)
    search_hint: str = ""

    def to_dict(self) -> dict:
        return {
            "name": self.name,
            "description": self.description,
            "category": self.category.value,
            "is_read_only": self.is_read_only,
            "is_destructive": self.is_destructive,
            "when_to_use": self.when_to_use,
            "aliases": list(self.aliases),
            "search_hint": self.search_hint,
        }
