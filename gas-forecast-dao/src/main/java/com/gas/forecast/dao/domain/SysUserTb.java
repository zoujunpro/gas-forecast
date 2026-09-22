package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

@TableName("sys_user_tb")
@Data
public class SysUserTb {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;
    private String realName;
    private String passwordHash;
    private String passwordSalt;
    private String avatar;
    private String email;
    private String phone;
    private String orgCode;
    private Integer status;
    private Integer deleted;
    private Date createdAt;
    private Date updatedAt;
}
