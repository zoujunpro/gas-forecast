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
    /** GENERAL为普通对话，FORECAST_ANALYSIS为预测详情页固定会话。 */
    private String scene;
    /** 绑定业务类型，当前预测分析使用FORECAST。 */
    private String businessType;
    /** 绑定业务编号，预测分析场景保存forecastBatchNo。 */
    private String businessNo;
    private String status;
    private String pendingIntentJson;
    private String memoryJson;
    private String lastTrainBatchNo;
    private String lastForecastBatchNo;
    private String createdBy;
    private Date createdAt;
    private Date updatedAt;
}
