package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@TableName("sys_code_sequence_tb")
@Data
public class SysCodeSequenceTb {
    @TableId(type = IdType.INPUT)
    private String codeType;
    private Long currentValue;
    private Date updatedAt;
}
