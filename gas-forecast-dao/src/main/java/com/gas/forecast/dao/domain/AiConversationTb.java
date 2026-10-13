package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

@Data
@TableName("ai_conversation_tb")
public class AiConversationTb {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String conversationNo;
    private String title;
    private String agentCode;
    private String status;
    private String pendingIntentJson;
    private String memoryJson;
    private String lastTrainBatchNo;
    private String lastForecastBatchNo;
    private String createdBy;
    private Date createdAt;
    private Date updatedAt;
}
