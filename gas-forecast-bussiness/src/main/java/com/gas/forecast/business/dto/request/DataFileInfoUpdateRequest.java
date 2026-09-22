package com.gas.forecast.business.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 原始数据文件更新请求参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataFileInfoUpdateRequest {
    /**
     * 文件记录ID。
     */
    @NotNull(message = "文件记录ID不能为空")
    private Long id;

    /**
     * 原始文件名称。
     */
    @NotBlank(message = "文件名称不能为空")
    @Size(max = 255, message = "文件名称长度不能超过255个字符")
    private String fileName;

    /**
     * 文件存储路径或对象存储Object Key。
     */
    @NotBlank(message = "文件存储路径不能为空")
    @Size(max = 512, message = "文件存储路径长度不能超过512个字符")
    private String objectKey;

    /**
     * 文件Hash值。
     */
    @Size(max = 128, message = "文件Hash长度不能超过128个字符")
    private String fileHash;

    /**
     * 处理状态。
     */
    @NotBlank(message = "处理状态不能为空")
    @Size(max = 32, message = "处理状态长度不能超过32个字符")
    private String status;

    /**
     * 数据总条数。
     */
    @Min(value = 0, message = "数据总条数不能小于0")
    private Integer totalCount;

    /**
     * 处理异常信息。
     */
    @Size(max = 2000, message = "处理异常信息长度不能超过2000个字符")
    private String errorMessage;

    /**
     * 创建人。
     */
    @Size(max = 64, message = "创建人长度不能超过64个字符")
    private String createdBy;

    /**
     * 创建人名字。
     */
    @Size(max = 128, message = "创建人名字长度不能超过128个字符")
    private String createdByName;

    public Long id() {
        return id;
    }

    public String fileName() {
        return fileName;
    }

    public String objectKey() {
        return objectKey;
    }

    public String fileHash() {
        return fileHash;
    }

    public String status() {
        return status;
    }

    public Integer totalCount() {
        return totalCount;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public String createdBy() {
        return createdBy;
    }

    public String createdByName() {
        return createdByName;
    }
}
