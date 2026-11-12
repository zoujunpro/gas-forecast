"""校验AI改写结果；失败时保留规则层生成的可回答问题。"""

from typing import Any

from .forecast_question_selector import SelectedForecastQuestion


def validated_questions(values: list[Any], selected: list[SelectedForecastQuestion]) -> list[dict[str, str]]:
    expected = {item.code: item for item in selected}
    normalized: list[dict[str, str]] = []
    for value in values:
        payload = value.model_dump() if hasattr(value, "model_dump") else value
        if not isinstance(payload, dict):
            return [item.__dict__ for item in selected]
        code = str(payload.get("code") or "").strip()
        text = " ".join(str(payload.get("text") or "").split()).strip()
        template = expected.get(code)
        if template is None or not text or len(text) > 30:
            return [item.__dict__ for item in selected]
        normalized.append({"code": code, "category": template.category, "text": text})
    if len(normalized) != 3 or len({item["code"] for item in normalized}) != 3:
        return [item.__dict__ for item in selected]
    return normalized
