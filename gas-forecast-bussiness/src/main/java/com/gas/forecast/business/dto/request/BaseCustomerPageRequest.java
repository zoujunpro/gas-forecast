package com.gas.forecast.business.dto.request;

import com.gas.forecast.common.core.dto.BasePageRequest;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 客户分页查询请求参数。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class BaseCustomerPageRequest extends BasePageRequest {

    /**
     * 搜索关键字，匹配客户编码、客户名称、区域名称或行业名称。
     */
    @Size(max = 128, message = "搜索关键字长度不能超过128个字符")
    private String keyword;
}
