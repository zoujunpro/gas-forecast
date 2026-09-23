package com.gas.forecast.business.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 模型训练结果查询请求。
 */
@Data
public class ModelTrainResultRequest {

    @Size(max = 64, message = "训练批次号长度不能超过64个字符")
    private String batchNo;

    @Size(max = 64, message = "智能体编码长度不能超过64个字符")
    private String agentCode;

    @Size(max = 64, message = "区域编码长度不能超过64个字符")
    private String regionCode;

    @Size(max = 64, message = "行业编码长度不能超过64个字符")
    private String industryCode;

    @Size(max = 64, message = "客户编码长度不能超过64个字符")
    private String customerCode;

    @Size(max = 32, message = "训练开始日期长度不能超过32个字符")
    private String trainStartDate;

    @Size(max = 32, message = "训练结束日期长度不能超过32个字符")
    private String trainEndDate;
}
