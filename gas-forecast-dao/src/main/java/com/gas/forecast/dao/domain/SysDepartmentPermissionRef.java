package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@TableName("sys_department_permission_ref")
@Data
public class SysDepartmentPermissionRef {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long departmentId;
    private Long permissionId;
}
