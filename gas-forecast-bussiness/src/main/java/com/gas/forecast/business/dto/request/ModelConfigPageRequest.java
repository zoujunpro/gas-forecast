package com.gas.forecast.business.dto.request;

import com.gas.forecast.common.core.dto.BasePageRequest;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型配置分页查询请求参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelConfigPageRequest extends BasePageRequest {

    /**
     * 搜索关键字，匹配配置编码、名称、智能体或场景。
     */
    @Size(max = 128, message = "搜索关键字长度不能超过128个字符")
    private String keyword;
}
