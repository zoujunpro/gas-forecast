package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

@TableName("sys_permission_tb")
@Data
public class SysPermissionTb {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parentId;
    private String permissionName;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String path;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String component;

    private String permissionType;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String perms;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String icon;

    private Integer sortNo;
    private Integer hidden;
    private Integer status;
    private Date createdAt;
    private Date updatedAt;
}
