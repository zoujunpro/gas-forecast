package com.gas.forecast.business.dto.request;

import com.gas.forecast.common.core.dto.BasePageRequest;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 行业分页查询请求参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BaseIndustryPageRequest extends BasePageRequest {

    /**
     * 搜索关键字，匹配行业编码或行业名称。
     */
    @Size(max = 128, message = "搜索关键字长度不能超过128个字符")
    private String keyword;
}
