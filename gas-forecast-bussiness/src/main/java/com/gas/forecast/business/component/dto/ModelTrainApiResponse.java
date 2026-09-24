package com.gas.forecast.business.component.dto;

import lombok.Data;

/** 模型平台训练接口标准响应。 */
@Data
public class ModelTrainApiResponse {
    private Integer code;
    private String message;
    private ModelTrainResultApiResponse data;
}
