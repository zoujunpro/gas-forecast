package com.gas.forecast.business.dto.req;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 原始数据文件更新请求参数。
 */
public record DataFileInfoUpdateReqDTO(
        /**
         * 文件记录ID。
         */
        @NotNull(message = "文件记录ID不能为空")
        Long id,

        /**
         * 原始文件名称。
         */
        @NotBlank(message = "文件名称不能为空")
        @Size(max = 255, message = "文件名称长度不能超过255个字符")
        String fileName,

        /**
         * 文件存储路径或对象存储Object Key。
         */
        @NotBlank(message = "文件存储路径不能为空")
        @Size(max = 512, message = "文件存储路径长度不能超过512个字符")
        String objectKey,

        /**
         * 文件Hash值。
         */
        @Size(max = 128, message = "文件Hash长度不能超过128个字符")
        String fileHash,

        /**
         * 处理状态。
         */
        @NotBlank(message = "处理状态不能为空")
        @Size(max = 32, message = "处理状态长度不能超过32个字符")
        String status,

        /**
         * 数据总条数。
         */
        @Min(value = 0, message = "数据总条数不能小于0")
        Integer totalCount,

        /**
         * 处理异常信息。
         */
        @Size(max = 2000, message = "处理异常信息长度不能超过2000个字符")
        String errorMessage,

        /**
         * 创建人。
         */
        @Size(max = 64, message = "创建人长度不能超过64个字符")
        String createdBy,

        /**
         * 创建人名字。
         */
        @Size(max = 128, message = "创建人名字长度不能超过128个字符")
        String createdByName
) {
}
