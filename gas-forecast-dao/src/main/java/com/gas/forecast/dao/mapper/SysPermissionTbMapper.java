package com.gas.forecast.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gas.forecast.dao.domain.SysPermissionTb;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SysPermissionTbMapper extends BaseMapper<SysPermissionTb> {
    List<SysPermissionTb> selectByUsername(@Param("username") String username);
}
