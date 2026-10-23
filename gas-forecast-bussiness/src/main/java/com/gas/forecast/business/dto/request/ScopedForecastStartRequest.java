package com.gas.forecast.business.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 页面按已选择的客户和预测场景发起预测。
 *
 * <p>该请求是明确的业务命令，直接进入统一预测业务流程。页面“开始预测”需要在缺少可用模型时
 * 自动训练并继续预测的，应传入 {@code trainingPreference=AUTO} 和 {@code confirmed=true}。</p>
 */
@Data
public class ScopedForecastStartRequest {
    /**
     * 预测场景编码。
     *
     * <p>支持 {@code short-term}（短期客户预测）、{@code monthly-sales}（月度销量预测）和
     * {@code winter-supply}（冬季保供预测）。</p>
     */
    @NotBlank(message = "智能体类型不能为空")
    @Size(max = 64)
    private String agentCode;

    /** 需要预测的标准客户编码。 */
    @NotBlank(message = "客户编码不能为空")
    @Size(max = 64)
    private String customerCode;

    /** 客户所属地区编码，用于记录和校验本次预测的业务范围。 */
    @Size(max = 64)
    private String regionCode;

    /** 客户所属行业编码，用于记录和校验本次预测的业务范围。 */
    @Size(max = 64)
    private String industryCode;

    /**
     * 预测周期数。
     *
     * <p>未传时由预测场景和模型配置决定默认值；短期预测通常表示天数，月度预测表示月数，
     * 冬季保供预测表示旬数。</p>
     */
    @Min(value = 1, message = "预测周期必须大于0")
    @Max(value = 365, message = "预测周期不能超过365期")
    private Integer forecastHorizon;

    /**
     * 预测时间粒度。
     *
     * <p>支持 {@code DAILY}、{@code TENDAY} 和 {@code MONTHLY}，必须与预测场景及所用模型的
     * 训练粒度一致；未传时由业务服务自动确定。</p>
     */
    @Size(max = 16)
    private String forecastFrequency;

    /**
     * 缺少可用模型时的处理偏好。
     *
     * <p>{@code UNSPECIFIED} 表示未指定；{@code AUTO} 表示已授权自动训练；
     * {@code USE_EXISTING_ONLY} 表示只允许使用已有成功模型。</p>
     */
    @Pattern(regexp = "UNSPECIFIED|AUTO|USE_EXISTING_ONLY", message = "训练偏好不正确")
    private String trainingPreference = "UNSPECIFIED";

    /** 用户对本次自动训练或其他有副作用操作的明确确认。 */
    private Boolean confirmed = false;

    /**
     * 本次预测请求的幂等键。
     *
     * <p>调用方应为一次业务操作生成唯一值；同一次网络重试必须复用原值，避免重复创建预测任务。</p>
     */
    @Size(max = 36)
    private String requestId;
}
