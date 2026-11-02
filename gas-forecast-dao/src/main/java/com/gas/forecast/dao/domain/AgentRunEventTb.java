package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/** 智能体一次运行中的真实过程事件，按主键 id 还原时间顺序。 */
@Data
@TableName("agent_run_event_tb")
public class AgentRunEventTb {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String runNo;
    private String eventCode;
    private String message;
    private String eventDataJson;
    private String status;
    private String forecastNo;
    private String taskDetailNo;
    private String errorCode;
    private String errorMessage;
    private Date createdAt;
    private Date updatedAt;
}
