package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/** AI会话消息；业务对象通过ai_message_ref_tb实现一条消息关联多个任务或批次。 */
@Data
@TableName("ai_conversation_message_tb")
public class AiConversationMessageTb {
    /** 主键ID。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属会话ID，关联ai_conversation_tb.id。 */
    private Long conversationId;

    /** 消息角色：USER、ASSISTANT、SYSTEM、TOOL。 */
    private String role;

    /** 对话轮次ID，同一轮用户与助手消息使用相同值。 */
    private String turnId;

    /** 消息正文。 */
    private String content;

    /** 消息分类或来源Skill编码，与业务绑定类型无关。 */
    @TableField("message_type")
    private String messageType;

    /** 消息附带的结构化数据JSON。 */
    private String structuredJson;

    /** 消息创建时间。 */
    private Date createdAt;
}
