package com.gas.forecast.business.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 原始数据文件应答参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataFileInfoResponse {
    /**
     * 文件记录ID。
     */
    private Long id;

    /**
     * 文件编码。
     */
    private String fileCode;

    /**
     * 原始文件名称。
     */
    private String fileName;

    /**
     * 文件存储路径或对象存储Object Key。
     */
    private String objectKey;

    /**
     * 文件Hash值。
     */
    private String fileHash;

    /**
     * 处理状态。
     */
    private String status;

    /**
     * 数据总条数。
     */
    private Integer totalCount;

    /**
     * 处理异常信息。
     */
    private String errorMessage;

    /**
     * 创建时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;

    /**
     * 更新时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updatedAt;

    /**
     * 创建人。
     */
    private String createdBy;

    /**
     * 创建人名字。
     */
    private String createdByName;
}
