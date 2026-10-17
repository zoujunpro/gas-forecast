package com.gas.forecast.business.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 统一自动训练入口参数。 */
@Data
public class AutoTrainRequest {

    /**
     * 已有训练配置ID。页面从训练配置发起时传入。
     * <p>
     * 传入后直接使用该配置；未传时才根据智能体和业务范围自动解析。
     * </p>
     */
    private Long trainConfigId;

    /**
     * 智能体编码。
     * <p>
     * 支持：short-term（短期客户）、monthly-sales（月度消费）、winter-supply（冬季保供）。
     * </p>
     */
    @Size(max = 64, message = "智能体编码长度不能超过64个字符")
    private String agentCode;

    /** 失败批次重试时传入原批次号，仅与 trainConfigId 一起使用。 */
    @Size(max = 64, message = "重试批次号长度不能超过64个字符")
    private String retryBatchNo;

    /**
     * 地区编码。冬季保供智能体必填；月度消费智能体需与行业编码同时填写。
     * <p>
     * 指定客户时可不传，系统会从客户档案自动补全。
     * </p>
     */
    @Size(max = 64, message = "地区编码长度不能超过64个字符")
    private String regionCode;

    /**
     * 行业编码。月度消费智能体需与地区编码同时填写。
     * <p>
     * 指定客户时可不传，系统会从客户档案自动补全。
     * </p>
     */
    @Size(max = 64, message = "行业编码长度不能超过64个字符")
    private String industryCode;

    /** 短期客户预测的客户编码；传入后地区和行业以客户档案为准。 */
    @Size(max = 64, message = "客户编码长度不能超过64个字符")
    private String customerCode;

    /**
     * 自定义训练开始日期，格式为 yyyy-MM-dd。
     * <p>
     * 与 trainEndDate 同时为空时，由模型的推荐训练周期数自动确定数据范围。
     * </p>
     */
    @Size(max = 32, message = "训练开始日期长度不能超过32个字符")
    private String trainStartDate;

    /** 自定义训练结束日期，格式为 yyyy-MM-dd，必须与 trainStartDate 同时传入。 */
    @Size(max = 32, message = "训练结束日期长度不能超过32个字符")
    private String trainEndDate;

    /** 训练方式：ALL、RECENT、RANGE；为空时使用模型推荐周期。 */
    @Size(max = 16, message = "训练方式长度不能超过16个字符")
    private String trainMode;

    /** RECENT 模式的最近训练周期数。 */
    private Integer recentPeriods;

    /** 触发来源：MANUAL、AI、MCP、SCHEDULE 或 DATA_EVENT，默认为 MANUAL。 */
    @Size(max = 32, message = "触发来源长度不能超过32个字符")
    private String triggerSource = "MANUAL";

    /** 是否忽略正在执行的同条件任务并强制创建新批次，默认为 false。 */
    private Boolean forceTrain = false;
}
