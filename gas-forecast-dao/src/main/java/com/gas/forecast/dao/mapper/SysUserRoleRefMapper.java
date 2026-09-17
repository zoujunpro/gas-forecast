package com.gas.forecast.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gas.forecast.dao.domain.SysUserRoleRef;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SysUserRoleRefMapper extends BaseMapper<SysUserRoleRef> {
    @Select("""
            select r.role_code
            from sys_user_tb u
            join sys_user_role_ref ur on ur.user_id = u.id
            join sys_role_tb r on r.id = ur.role_id
            where u.username = #{username}
            order by r.role_code
            """)
    List<String> selectRoleCodesByUsername(@Param("username") String username);
}
