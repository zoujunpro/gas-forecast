from __future__ import annotations

from dataclasses import dataclass
from importlib.resources import files
from typing import Any

import yaml

from ..profiles.contracts import MethodSpec


@dataclass(frozen=True)
class ResolvedFeaturePlan:
    """一次预测实际启用的特征方法及可审计的解析信息。"""

    policy_code: str
    policy_name: str
    target_type: str
    specs: tuple[MethodSpec, ...]
    selected_features: tuple[dict[str, Any], ...]
    skipped_features: tuple[dict[str, str], ...]


class FeaturePolicyResolver:
    """从全量特征目录和场景 Policy 解析本次实际特征清单。"""

    def __init__(self, catalog: dict | None = None, policies: dict[str, dict] | None = None):
        self.catalog = catalog or self._load_catalog()
        self.policies = policies or self._load_policies()
        self._definitions = {item["code"]: item for item in self.catalog["features"]}
        self._validate()

    def resolve(
        self,
        policy_code: str,
        metadata: dict[str, Any],
        history_columns,
        future_columns,
    ) -> ResolvedFeaturePlan:
        try:
            policy = self.policies[policy_code]
        except KeyError as exc:
            raise KeyError(f"特征配置不存在: {policy_code}") from exc

        missing_context = [
            key for key in policy.get("required_context", []) if not metadata.get(key)
        ]
        if missing_context:
            raise ValueError(f"特征配置 {policy_code} 缺少上下文: {', '.join(missing_context)}")

        entries = list(policy.get("features", []))
        for rule in policy.get("rules", []):
            if self._matches(rule.get("match", {}), metadata):
                entries.extend(rule.get("features", []))

        available = set(history_columns) & set(future_columns)
        selected: list[dict[str, Any]] = []
        skipped: list[dict[str, str]] = []
        merged: dict[str, dict[str, Any]] = {}
        for entry in entries:
            definition = self._definitions[entry["code"]]
            required = bool(entry.get("required", definition.get("default_required", False)))
            if not definition.get("enabled", False):
                skipped.append({"code": entry["code"], "reason": "特征实现尚未启用(enabled=false)"})
                continue
            required_columns = set(definition.get("required_columns", ()))
            required_columns.update(entry.get("params", {}).get("required_columns", ()))
            missing_columns = sorted(required_columns - available)
            if missing_columns:
                if required:
                    raise ValueError(
                        f"必需特征 {entry['code']} 缺少历史和未来共有字段: "
                        f"{', '.join(missing_columns)}"
                    )
                skipped.append(
                    {"code": entry["code"], "reason": f"缺少字段: {', '.join(missing_columns)}"}
                )
                continue

            method_code = definition["method_code"]
            params = dict(entry.get("params", {}))
            if required_columns:
                params["required_columns"] = sorted(required_columns)
            current = merged.setdefault(
                method_code, {"params": {}, "required": False, "feature_codes": []}
            )
            previous_required_columns = set(current["params"].get("required_columns", ()))
            current["params"].update(params)
            if "required_columns" in params:
                current["params"]["required_columns"] = sorted(
                    previous_required_columns | set(params["required_columns"])
                )
            current["required"] = current["required"] or required
            current["feature_codes"].append(entry["code"])
            selected.append(
                {
                    "code": entry["code"],
                    "name": definition["name"],
                    "category": definition["category"],
                    "required": required,
                    "method_code": method_code,
                }
            )

        specs = tuple(
            MethodSpec(code, value["params"], value["required"]) for code, value in merged.items()
        )
        return ResolvedFeaturePlan(
            policy_code=policy_code,
            policy_name=policy["name"],
            target_type=policy["target_type"],
            specs=specs,
            selected_features=tuple(selected),
            skipped_features=tuple(skipped),
        )

    def _validate(self) -> None:
        if len(self._definitions) != len(self.catalog.get("features", [])):
            raise ValueError("特征目录存在重复编码")
        for policy in self.policies.values():
            entries = [*policy.get("features", []), *policy.get("planned_features", [])]
            for rule in policy.get("rules", []):
                entries.extend(rule.get("features", []))
            unknown = [entry["code"] for entry in entries if entry["code"] not in self._definitions]
            if unknown:
                raise ValueError(f"特征配置 {policy['code']} 引用了未知特征: {', '.join(unknown)}")

    @staticmethod
    def _matches(match: dict[str, Any], metadata: dict[str, Any]) -> bool:
        return all(metadata.get(key) == value for key, value in match.items())

    @staticmethod
    def _load_catalog() -> dict:
        path = files("gas_model_engine").joinpath("config/features/catalog.yaml")
        return yaml.safe_load(path.read_text(encoding="utf-8"))

    @staticmethod
    def _load_policies() -> dict[str, dict]:
        directory = files("gas_model_engine").joinpath("config/features/policies")
        loaded = {}
        for path in directory.iterdir():
            if path.name.endswith(".yaml"):
                policy = yaml.safe_load(path.read_text(encoding="utf-8"))
                loaded[policy["code"]] = policy
        return loaded
