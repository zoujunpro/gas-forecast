"""按工具维护展示字段白名单；原始记录保持不变。"""

PRESENTATION_FIELDS = {
    "customer_list": [
        {"field": "customerName", "title": "客户名称"},
        {"field": "regionName", "title": "地区名称"},
        {"field": "industryName", "title": "行业名称"},
    ],
    "region_list": [{"field": "regionName", "title": "地区名称"}],
    "industry_list": [{"field": "industryName", "title": "行业名称"}],
}


def presentation_columns(tool_name, fields):
    if tool_name in PRESENTATION_FIELDS:
        return [dict(column) for column in PRESENTATION_FIELDS[tool_name] if column["field"] in fields]
    return [{"field": field, "title": field} for field in fields]
