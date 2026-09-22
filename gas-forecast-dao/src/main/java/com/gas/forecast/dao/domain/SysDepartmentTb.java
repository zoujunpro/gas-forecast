package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

@TableName("sys_department_tb")
@Data
public class SysDepartmentTb {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parentId;
    private String departmentName;
    private String orgCode;
    private Integer sortNo;
    private Integer status;
    private Date createdAt;
    private Date updatedAt;
}
