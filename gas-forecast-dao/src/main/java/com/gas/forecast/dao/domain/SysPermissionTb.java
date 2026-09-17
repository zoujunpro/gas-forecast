package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@TableName("sys_permission_tb")
@Data
public class SysPermissionTb {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentId;
    private String permissionName;
    private String path;
    private String component;
    private String permissionType;
    private String perms;
    private String icon;
    private Integer sortNo;
    private Integer hidden;
    private Integer status;
    private Date createdAt;
    private Date updatedAt;
}
