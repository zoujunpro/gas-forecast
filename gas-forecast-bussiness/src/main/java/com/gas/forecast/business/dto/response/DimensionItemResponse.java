package com.gas.forecast.business.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 预测维度选项应答参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DimensionItemResponse {
    /**
     * 编码。
     */
    private String code;

    /**
     * 名称。
     */
    private String name;

    /**
     * 上级编码。
     */
    private String parentCode;

    /**
     * 类型。
     */
    private String type;
}
