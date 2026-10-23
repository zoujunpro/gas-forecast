package com.gas.forecast.business.dto.request;

import com.gas.forecast.business.enums.ExecutionDimensionTypeEnum;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 页面或智能体按已确认范围发起预测。
 *
 * <p>该请求直接进入统一预测业务流程；系统会在同一任务内完成训练、回测和未来预测。</p>
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

    /** 预测最小执行维度：CUSTOMER、INDUSTRY、REGION。 */
    @NotNull(message = "预测维度不能为空")
    private ExecutionDimensionTypeEnum dimensionType;

    /**
     * 已消歧的预测范围组合；不同对象独立展开，不能相互做笛卡尔积。
     */
    @NotNull(message = "预测对象不能为空")
    @Size(min = 1, message = "至少需要一个预测对象")
    @Valid
    private List<ExecutionTarget> targets = new ArrayList<>();

    /**
     * 预测起始日期；不传时执行层按预测单位补齐：day 为当天、month 为当月首日、tenday 为当旬首日。
     */
    private LocalDate forecastStartDate;

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
     * 预测时间单位：{@code day}、{@code tenday}、{@code month}。
     */
    @Size(max = 16)
    private String forecastFrequency;

    /**
     * 模型训练和回测使用的历史数据范围。
     * 开始、结束日期均为空时，执行层使用该目标全部可用历史数据。
     */
    @NotNull(message = "训练数据范围不能为空")
    @Valid
    private TrainingDataRange trainingRange;

    @Data
    public static class TrainingDataRange {
        private LocalDate historyStartDate;
        private LocalDate historyEndDate;
    }

}
