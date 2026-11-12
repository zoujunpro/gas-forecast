package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/** 一次由助手提问、用户回答组成的澄清或确认交互。 */
@Data
@TableName("ai_conversation_interaction_tb")
public class AiConversationInteractionTb {
    /** 主键ID。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 交互唯一编号。 */
    private String interactionId;

    /** 所属会话ID。 */
    private Long conversationId;

    /** 最初用户业务问题所在轮次；同一业务请求的连续澄清保持不变。 */
    private String sourceTurnId;

    /** 助手提出本次问题的轮次。 */
    private String questionTurnId;

    /** 用户回答本次问题的轮次。 */
    private String answerTurnId;

    /** 提出问题的助手消息ID。 */
    private Long questionMessageId;

    /** 用户回答消息ID。 */
    private Long answerMessageId;

    /** 交互类型：CLARIFICATION、CONFIRMATION。 */
    private String interactionType;

    /** 后端确定的主要交互原因编码。 */
    private String reasonCode;

    /** 返回前端的完整结构化问题JSON。 */
    private String questionJson;

    /** Python恢复当前未完成操作所需的结构化快照。 */
    private String operationJson;

    /** 解析并校验后的结构化回答JSON。 */
    private String answerJson;

    /** 回答校验结果编码。 */
    private String validationCode;

    /** 回答校验结果说明。 */
    private String validationMessage;

    /** 状态：WAITING、ANSWERED、INVALID、CANCELLED、EXPIRED。 */
    private String status;

    /** 用户回答时间。 */
    private Date answeredAt;

    /** 交互过期时间。 */
    private Date expiredAt;

    /** 创建时间。 */
    private Date createdAt;

    /** 更新时间。 */
    private Date updatedAt;
}
