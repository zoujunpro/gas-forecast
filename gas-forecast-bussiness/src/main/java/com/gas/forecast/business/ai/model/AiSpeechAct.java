package com.gas.forecast.business.ai.model;

/** 用户这句话的沟通目的，与具体业务动作分离。 */
public enum AiSpeechAct {
    /** 询问信息，不允许产生训练、预测等副作用。 */
    QUESTION,
    /** 明确要求执行某项业务操作。 */
    COMMAND,
    /** 对系统上一轮待执行操作的明确确认。 */
    CONFIRM,
    /** 取消上一轮待执行操作。 */
    CANCEL,
    /** 针对当前训练或预测结果继续分析。 */
    FOLLOW_UP, UNKNOWN
}
