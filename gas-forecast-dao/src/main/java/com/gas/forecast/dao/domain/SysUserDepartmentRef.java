package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@TableName("sys_user_department_ref")
@Data
public class SysUserDepartmentRef {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long departmentId;
}
