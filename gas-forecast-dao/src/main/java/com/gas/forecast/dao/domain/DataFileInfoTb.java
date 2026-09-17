package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 原始数据文件信息表
 * @TableName data_file_info_tb
 */
@TableName(value ="data_file_info_tb")
@Data
public class DataFileInfoTb {
    /**
     * 主键ID
     */
    @TableId
    private Long id;

    /**
     * 文件编码
     */
    private String fileCode;

    /**
     * 原始文件名称
     */
    private String fileName;

    /**
     * 文件存储路径或对象存储Object Key
     */
    private String objectKey;

    /**
     * 文件Hash值
     */
    private String fileHash;

    /**
     * 处理状态：UPLOADED    已上传，等待处理    ；PROCESSING  正在处理；SUCCESS  全部处理成功；FAILED  处理失败
     */
    private String status;

    /**
     * 数据总条数
     */
    private Integer totalCount;

    /**
     * 处理异常信息
     */
    private String errorMessage;

    /**
     * 创建时间
     */
    private Date createdAt;

    /**
     * 更新时间
     */
    private Date updatedAt;

    /**
     * 创建人
     */
    private String createdBy;

    /**
     * 创建人名字
     */
    private String createdByName;

}
