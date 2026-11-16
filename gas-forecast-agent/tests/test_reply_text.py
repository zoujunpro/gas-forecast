import json

import pytest

from gas_forecast_agent.reply_text import FALLBACK_REPLY, parse_reply, user_reply
from gas_forecast_agent.skills.agent import ActionExecutor
from gas_forecast_agent.presentation import build_content_blocks


@pytest.mark.parametrize("value", [
    '正常说明。"}}',
    '{"status":"COMPLETED","message":"说明。"',
    '{"status":"OTHER","message":"说明。"}',
    '{"status":"COMPLETED","message":{"content":"说明。"}}',
    '{"status":"COMPLETED","message":["说明。"]}',
    '状态：RUNNING，等待中。',
    '说明：{"resultType":"TASK","success":true}',
    'Traceback (most recent call last): exception',
    '{"status":["COMPLETED"],"message":"说明。"}',
])
def test_protocol_never_leaks(value):
    assert user_reply(value) == FALLBACK_REPLY
    assert ActionExecutor._message_decision(value)["status"] == "FAILED"
    assert build_content_blocks(value, {}, {})[0].data["content"] == FALLBACK_REPLY


def test_valid_reply_extracts_text_and_keeps_status_separate():
    value = json.dumps({"status": "NEED_CLARIFICATION", "message": "请确认预测日期。"})
    assert parse_reply(value) == ("请确认预测日期。", "NEED_CLARIFICATION")
    assert ActionExecutor._message_decision(value) == {
        "type": "message", "status": "NEED_CLARIFICATION", "message": "请确认预测日期。"
    }


def test_normal_markdown_and_business_numbers_remain_unchanged():
    value = "预测区间较宽。\n\n**数据依据**\n- 平均相对误差为11.17%。\n- 温度在[5, 10]之间。"
    assert user_reply(value) == value


def test_nested_json_extracts_only_text():
    value = json.dumps({"status": "COMPLETED", "message": json.dumps({"message": "说明。"})})
    assert user_reply(value) == "说明。"


def test_prose_escaped_paragraphs_normalize_but_code_is_preserved():
    assert user_reply(r"结论。\n\n1. 数据依据") == "结论。\n\n1. 数据依据"
    code = r"示例：`\n`。\n\n路径"
    assert user_reply(code) == code
