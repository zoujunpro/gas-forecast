package com.gas.forecast.business.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Python返回Java和Java返回Vue统一使用的会话错误信息。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiChatErrorInfo {

    /** 稳定错误码。 */
    private String code;
}
