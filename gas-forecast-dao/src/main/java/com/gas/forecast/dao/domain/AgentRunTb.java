package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/** 一次由用户消息触发的Agent执行记录。 */
@Data
@TableName("agent_run_tb")
public class AgentRunTb {
    /** 主键ID。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** Agent执行唯一编号。 */
    private String runNo;

    /** 前端提交幂等号，用于防止重复发起同一次 Agent 请求。 */
    private String requestId;

    /** 所属会话ID，关联ai_conversation_tb.id。 */
    private Long conversationId;

    /** 触发本次执行的用户消息ID，关联ai_conversation_message_tb.id。 */
    private Long messageId;

    /** 执行智能体编码。 */
    private String agentCode;

    /** 执行状态：PENDING、RUNNING、WAITING、SUCCESS、FAILED、CANCELLED。 */
    private String status;

    /** 主意图编码：CHAT、QUERY、TRAIN、FORECAST、COMPOSITE。 */
    private String intentCode;

    /** 结构化意图JSON，允许包含多个操作及业务参数。 */
    private String intentJson;

    /** 本次执行匹配的Skill编码。 */
    private String skillCode;

    /** Agent请求参数快照JSON，包括用户消息及本轮页面提供的当前业务焦点。 */
    private String requestJson;

    /** Agent执行结果摘要JSON。 */
    private String resultJson;

    /** 执行失败错误编码。 */
    private String errorCode;

    /** 执行失败原因。 */
    private String errorMessage;

    /** 开始执行时间。 */
    private Date startedAt;

    /** 执行结束时间。 */
    private Date finishedAt;

    /** 创建时间。 */
    private Date createdAt;

    /** 更新时间。 */
    private Date updatedAt;
}
