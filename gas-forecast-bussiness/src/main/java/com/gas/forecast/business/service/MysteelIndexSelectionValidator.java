package com.gas.forecast.business.service;

import com.gas.forecast.business.component.dto.MysteelIndexCandidate;
import com.gas.forecast.common.core.BusinessException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 校验大模型选择结果，禁止使用候选列表之外的钢联指标编码。 */
@Service
public class MysteelIndexSelectionValidator {

    public List<MysteelIndexCandidate> validate(List<MysteelIndexCandidate> candidates, List<String> selectedCodes) {
        Map<String, MysteelIndexCandidate> candidateByCode = new LinkedHashMap<>();
        candidates.forEach(candidate -> candidateByCode.putIfAbsent(candidate.indexCode(), candidate));
        Map<String, MysteelIndexCandidate> selected = new LinkedHashMap<>();
        for (String code : selectedCodes) {
            MysteelIndexCandidate candidate = candidateByCode.get(code);
            if (candidate == null) {
                throw new BusinessException("大模型选择了候选列表之外的钢联指标：" + code);
            }
            selected.putIfAbsent(code, candidate);
        }
        return List.copyOf(selected.values());
    }
}
