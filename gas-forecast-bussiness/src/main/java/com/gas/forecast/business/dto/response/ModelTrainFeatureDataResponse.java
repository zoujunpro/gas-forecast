package com.gas.forecast.business.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 训练特征数据应答参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelTrainFeatureDataResponse {
    /**
     * ID。
     */
    private Long id;

    /**
     * 统计日期。
     */
    private String statDate;

    /**
     * 时间粒度。
     */
    private String timeGranularity;

    /**
     * 区域编码。
     */
    private String regionCode;

    /**
     * 区域名称。
     */
    private String regionName;

    /**
     * 客户编码。
     */
    private String customerCode;

    /**
     * 客户名称。
     */
    private String customerName;

    /**
     * 行业编码。
     */
    private String industryCode;

    /**
     * 行业名称。
     */
    private String industryName;

    /**
     * 天然气销量。
     */
    private BigDecimal gasSales;

    /**
     * 第1个扩展特征值。
     */
    private Double feature001;

    /**
     * 第2个扩展特征值。
     */
    private Double feature002;

    /**
     * 第3个扩展特征值。
     */
    private Double feature003;

    /**
     * 第4个扩展特征值。
     */
    private Double feature004;

    /**
     * 第5个扩展特征值。
     */
    private Double feature005;

    /**
     * 第6个扩展特征值。
     */
    private Double feature006;

    /**
     * 第7个扩展特征值。
     */
    private Double feature007;

    /**
     * 第8个扩展特征值。
     */
    private Double feature008;

    /**
     * 第9个扩展特征值。
     */
    private Double feature009;

    /**
     * 第10个扩展特征值。
     */
    private Double feature010;

    /**
     * 特征值映射。
     */
    private Map<String, Double> featureValues;

    /**
     * 特征明细列表。
     */
    private List<ModelTrainFeatureValueResponse> featureDetails;

    /**
     * 创建时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    /**
     * 更新时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;
}
