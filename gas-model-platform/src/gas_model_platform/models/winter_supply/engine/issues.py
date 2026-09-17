from __future__ import annotations

import re


def localize_issue_message(category: str, message: str) -> str:
    """把常见第三方模型警告转换为可直接返回业务方的中文信息。"""
    text = str(message).strip()
    lower = text.lower()

    if "objective did not converge" in lower:
        gap = re.search(r"Duality gap:\s*([^,]+)", text, flags=re.IGNORECASE)
        tolerance = re.search(r"tolerance:\s*([^,]+)", text, flags=re.IGNORECASE)
        details = []
        if gap:
            details.append(f"对偶间隙：{gap.group(1).strip()}")
        if tolerance:
            details.append(f"容差：{tolerance.group(1).strip()}")
        suffix = f"（{'，'.join(details)}）" if details else ""
        return (
            "模型优化未收敛。建议增加迭代次数、检查特征量纲，"
            f"或增强正则化{suffix}。"
        )

    if "maximum likelihood optimization failed to converge" in lower:
        return "最大似然优化未能收敛，请检查优化器返回结果。"

    if "lbfgs failed to converge" in lower:
        return "LBFGS 优化器未能收敛，建议增加迭代次数或对特征进行标准化。"

    if category == "ConvergenceWarning" and "did not converge" in lower:
        return "模型训练未能收敛，建议增加迭代次数并检查特征量纲和正则化参数。"

    return text
