package com.gas.forecast.business.service.impl;

import com.gas.forecast.business.enums.BaseCodeType;
import com.gas.forecast.business.service.BaseCodeGenerateService;
import com.gas.forecast.dao.domain.SysCodeSequenceTb;
import com.gas.forecast.dao.mapper.SysCodeSequenceTbMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
public class BaseCodeGenerateServiceImpl implements BaseCodeGenerateService {
    private final SysCodeSequenceTbMapper sysCodeSequenceTbMapper;

    public BaseCodeGenerateServiceImpl(SysCodeSequenceTbMapper sysCodeSequenceTbMapper) {
        this.sysCodeSequenceTbMapper = sysCodeSequenceTbMapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public String nextCode(BaseCodeType codeType) {
        SysCodeSequenceTb sequence = sysCodeSequenceTbMapper.selectForUpdate(codeType.prefix());
        if (sequence == null) {
            sequence = new SysCodeSequenceTb();
            sequence.setCodeType(codeType.prefix());
            sequence.setCurrentValue(0L);
            sequence.setUpdatedAt(new Date());
            sysCodeSequenceTbMapper.insert(sequence);
            sequence = sysCodeSequenceTbMapper.selectForUpdate(codeType.prefix());
        }
        long nextValue = sequence.getCurrentValue() + 1;
        sequence.setCurrentValue(nextValue);
        sequence.setUpdatedAt(new Date());
        sysCodeSequenceTbMapper.updateById(sequence);
        return codeType.prefix() + String.format("%0" + codeType.width() + "d", nextValue);
    }
}
