"""校验 AI 动态生成的问题；异常时保留规则层的可回答问题。"""

from typing import Any

from .forecast_question_selector import SelectedForecastQuestion


def validated_questions(values: list[Any], selected: list[SelectedForecastQuestion]) -> list[dict[str, str]]:
    fallback = [item.__dict__ for item in selected]
    normalized: list[dict[str, str]] = []
    for value in values:
        payload = value.model_dump() if hasattr(value, "model_dump") else value
        if not isinstance(payload, dict):
            return fallback
        code = str(payload.get("code") or "").strip().upper()
        category = str(payload.get("category") or "").strip().upper()
        text = " ".join(str(payload.get("text") or "").split()).strip()
        if not code or not category or not text or len(text) > 30 or not text.endswith(("？", "?")):
            return fallback
        normalized.append({"code": code, "category": category, "text": text})
    if (len(normalized) != 3
            or len({item["code"] for item in normalized}) != 3
            or len({item["text"] for item in normalized}) != 3):
        return fallback
    return normalized
