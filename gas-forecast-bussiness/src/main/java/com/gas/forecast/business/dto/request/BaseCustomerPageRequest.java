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

    /**
     * 所属区域编码，精确匹配。
     */
    @Size(max = 64, message = "区域编码长度不能超过64个字符")
    private String regionCode;

    /**
     * 所属行业编码，精确匹配。
     */
    @Size(max = 64, message = "行业编码长度不能超过64个字符")
    private String industryCode;
}
