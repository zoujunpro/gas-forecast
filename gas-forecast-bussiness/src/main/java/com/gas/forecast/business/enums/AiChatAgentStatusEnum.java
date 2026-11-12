package com.gas.forecast.business.enums;

import lombok.Getter;

/** AI会话中Agent本轮处理状态，不表示后台预测任务已经完成。 */
@Getter
public enum AiChatAgentStatusEnum {
    PENDING("本轮已受理，等待后台业务任务执行"),
    RUNNING("本轮已受理，后台业务任务正在执行"),
    WAITING("等待用户补充或确认信息"),
    SUCCESS("本轮会话处理成功"),
    FAILED("本轮会话处理失败"),
    CANCELLED("本轮会话处理已取消");

    private final String description;

    AiChatAgentStatusEnum(String description) {
        this.description = description;
    }
}
