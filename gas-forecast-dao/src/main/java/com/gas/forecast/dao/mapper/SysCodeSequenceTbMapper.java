package com.gas.forecast.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gas.forecast.dao.domain.SysCodeSequenceTb;
import org.apache.ibatis.annotations.Param;

public interface SysCodeSequenceTbMapper extends BaseMapper<SysCodeSequenceTb> {
    SysCodeSequenceTb selectForUpdate(@Param("codeType") String codeType);
}
