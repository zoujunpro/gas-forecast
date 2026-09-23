package com.gas.forecast.business.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型配置新增请求参数。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelConfigCreateRequest {
    @NotBlank(message = "配置编码不能为空")
    private @Size(max = 64, message = "配置编码长度不能超过64个字符") String configCode;

    @NotBlank(message = "配置名称不能为空")
    private @Size(max = 128, message = "配置名称长度不能超过128个字符") String configName;

    @Size(max = 32, message = "模型版本长度不能超过32个字符")
    private String modelVersion;

    @NotBlank(message = "智能体编码不能为空")
    private @Size(max = 64, message = "智能体编码长度不能超过64个字符") String agentCode;

    @NotBlank(message = "场景编码不能为空")
    private @Size(max = 32, message = "场景编码长度不能超过32个字符") String sceneCode;

    @Size(max = 32, message = "区域编码长度不能超过32个字符")
    private String regionCode;

    @Size(max = 32, message = "行业编码长度不能超过32个字符")
    private String industryCode;

    @Size(max = 64, message = "客户编码长度不能超过64个字符")
    private String customerCode;

    private List<@Size(max = 32, message = "区域编码长度不能超过32个字符") String> regionCodes;

    private List<@Size(max = 32, message = "行业编码长度不能超过32个字符") String> industryCodes;

    private List<@Size(max = 64, message = "客户编码长度不能超过64个字符") String> customerCodes;

    @Size(max = 500, message = "描述长度不能超过500个字符")
    private String description;

}
