package com.gas.forecast.business.dto.resp;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Date;

/**
 * 原始数据文件应答参数。
 */
public record DataFileInfoRespDTO(
        /**
         * 文件记录ID。
         */
        Long id,

        /**
         * 文件编码。
         */
        String fileCode,

        /**
         * 原始文件名称。
         */
        String fileName,

        /**
         * 文件存储路径或对象存储Object Key。
         */
        String objectKey,

        /**
         * 文件Hash值。
         */
        String fileHash,

        /**
         * 处理状态。
         */
        String status,

        /**
         * 数据总条数。
         */
        Integer totalCount,

        /**
         * 处理异常信息。
         */
        String errorMessage,

        /**
         * 创建时间。
         */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        Date createdAt,

        /**
         * 更新时间。
         */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        Date updatedAt,

        /**
         * 创建人。
         */
        String createdBy,

        /**
         * 创建人名字。
         */
        String createdByName
) {
}
