package com.gas.forecast.business.enums;

import lombok.Getter;

/** AI会话内容块类型，Vue根据该类型选择对应的展示组件。 */
@Getter
public enum AiChatContentTypeEnum {
    MARKDOWN("Markdown文本内容"),
    CHART("图表内容"),
    TABLE("表格内容"),
    TASK_CARD("执行任务卡片"),
    RESULT_LINK("业务结果详情链接");

    private final String description;

    AiChatContentTypeEnum(String description) {
        this.description = description;
    }
}
