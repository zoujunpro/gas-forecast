package com.gas.forecast.dao.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/** AI消息与执行任务、预测记录的多值引用关系。 */
@Data
@TableName("ai_message_ref_tb")
public class AiMessageRefTb {
    /** 主键ID。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联消息ID，关联ai_conversation_message_tb.id。 */
    private Long messageId;

    /** 引用类型：EXECUTION_TASK、FORECAST。 */
    private String refType;

    /** 引用对象编号。 */
    private String refNo;

    /** 关联类型：CREATED、REFERENCED、DISPLAYED。 */
    private String relationType;

    /** 创建时间。 */
    private Date createdAt;
}
