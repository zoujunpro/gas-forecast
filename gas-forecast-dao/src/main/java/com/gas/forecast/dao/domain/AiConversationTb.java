package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/** AI智能体会话；会话本身不绑定地区、行业或客户。 */
@Data
@TableName("ai_conversation_tb")
public class AiConversationTb {
    /** 主键ID。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话唯一编号。 */
    private String conversationNo;

    /** 会话标题。 */
    private String title;

    /** 会话内容摘要。 */
    private String summary;

    /** 所属智能体编码。 */
    private String agentCode;

    /** 会话状态：ACTIVE有效、ARCHIVED已归档。 */
    private String status;

    /** 是否已逻辑删除：0否、1是。已删除会话不参与正常会话查询或后续对话。 */
    private Integer deletedFlag;

    /** 逻辑删除时间。 */
    private Date deletedAt;

    /** 可变会话记忆，包括当前业务焦点、已确认参数和待处理动作。 */
    private String memoryJson;

    /** 最近消息时间。 */
    private Date lastMessageAt;

    /** 创建人用户ID。 */
    private String createdBy;

    /** 创建时间。 */
    private Date createdAt;

    /** 最后更新时间。 */
    private Date updatedAt;

    /** 旧业务逻辑临时兼容字段，不再映射数据库。 */
    @Deprecated
    @TableField(exist = false)
    private String scene;

    /** 旧业务逻辑临时兼容字段，业务对象引用已迁移到ai_message_ref_tb。 */
    @Deprecated
    @TableField(exist = false)
    private String businessType;

    /** 旧业务逻辑临时兼容字段，业务对象引用已迁移到ai_message_ref_tb。 */
    @Deprecated
    @TableField(exist = false)
    private String businessNo;

    /** 旧业务逻辑临时兼容字段，待确认参数统一由Agent执行记录保存。 */
    @Deprecated
    @TableField(exist = false)
    private String pendingIntentJson;

    /** 旧业务逻辑临时兼容字段，不再作为会话快捷指针。 */
    @Deprecated
    @TableField(exist = false)
    private String lastTrainBatchNo;

    /** 旧业务逻辑临时兼容字段，不再作为会话快捷指针。 */
    @Deprecated
    @TableField(exist = false)
    private String lastForecastBatchNo;
}
