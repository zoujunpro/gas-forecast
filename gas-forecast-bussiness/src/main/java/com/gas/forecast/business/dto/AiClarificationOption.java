package com.gas.forecast.business.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Python、Java和Vue统一使用的澄清候选项。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiClarificationOption {

    /** 提交给后端的稳定值。 */
    private String value;

    /** 展示给用户的候选项名称。 */
    private String label;
}
