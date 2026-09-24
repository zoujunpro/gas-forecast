package com.gas.forecast.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gas.forecast.business.dto.request.DataFileInfoCreateRequest;
import com.gas.forecast.business.dto.request.DataFileInfoPageRequest;
import com.gas.forecast.business.dto.request.DataFileInfoUpdateRequest;
import com.gas.forecast.business.dto.response.DataFileInfoResponse;
import com.gas.forecast.business.enums.BaseCodeType;
import com.gas.forecast.business.service.BaseCodeGenerateService;
import com.gas.forecast.business.service.DataFileInfoService;
import com.gas.forecast.business.util.PageUtils;
import com.gas.forecast.common.core.PageInfoDTO;
import com.gas.forecast.common.security.context.SecurityContextHolder;
import com.gas.forecast.common.util.TextUtils;
import com.gas.forecast.dao.domain.DataFileInfoTb;
import com.gas.forecast.dao.mapper.DataFileInfoTbMapper;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 原始数据文件信息业务服务实现。
 */
@Service
@RequiredArgsConstructor
public class DataFileInfoServiceImpl implements DataFileInfoService {

    private final DataFileInfoTbMapper dataFileInfoTbMapper;
    private final BaseCodeGenerateService baseCodeGenerateService;

    /**
     * 分页查询原始数据文件列表。
     */
    @Override
    public PageInfoDTO<DataFileInfoResponse> listPage(DataFileInfoPageRequest reqDTO) {
        LambdaQueryWrapper<DataFileInfoTb> query = Wrappers.lambdaQuery();
        String keyword = reqDTO.keyword();
        if (TextUtils.hasText(keyword)) {
            query.and(wrapper -> wrapper.like(DataFileInfoTb::getFileCode, keyword).or().like(DataFileInfoTb::getFileName, keyword).or().like(DataFileInfoTb::getObjectKey, keyword).or()
                    .like(DataFileInfoTb::getFileHash, keyword).or().like(DataFileInfoTb::getStatus, keyword).or().like(DataFileInfoTb::getCreatedByName, keyword));
        }
        query.orderByDesc(DataFileInfoTb::getUpdatedAt).orderByDesc(DataFileInfoTb::getId);
        int page = reqDTO.page() == null ? 1 : reqDTO.page();
        int size = reqDTO.size() == null ? 10 : reqDTO.size();
        IPage<DataFileInfoTb> result = dataFileInfoTbMapper.selectPage(PageUtils.pageRequest(page, size), query);
        return PageUtils.toPage(result, result.getRecords().stream().map(this::toResp).toList());
    }

    /**
     * 新增原始数据文件信息。
     */
    @Override
    @Transactional
    public DataFileInfoResponse create(DataFileInfoCreateRequest reqDTO) {
        DataFileInfoTb fileInfo = toEntity(reqDTO);
        Date now = new Date();
        fileInfo.setId(null);
        fileInfo.setFileCode(baseCodeGenerateService.nextCode(BaseCodeType.FILE));
        fileInfo.setCreatedAt(now);
        fileInfo.setUpdatedAt(now);
        fileInfo.setCreatedBy(String.valueOf(SecurityContextHolder.getUserId()));
        fileInfo.setCreatedByName(SecurityContextHolder.getUserName());
        dataFileInfoTbMapper.insert(fileInfo);
        return toResp(fileInfo);
    }

    /**
     * 更新原始数据文件信息。
     */
    @Override
    public DataFileInfoResponse update(DataFileInfoUpdateRequest reqDTO) {
        DataFileInfoTb fileInfo = toEntity(reqDTO);
        Long id = reqDTO.id();
        fileInfo.setId(id);
        fileInfo.setUpdatedAt(new Date());
        dataFileInfoTbMapper.updateById(fileInfo);
        return toResp(dataFileInfoTbMapper.selectById(id));
    }

    /**
     * 删除原始数据文件信息。
     */
    @Override
    public void delete(Long id) {
        dataFileInfoTbMapper.deleteById(id);
    }

    private DataFileInfoResponse toResp(DataFileInfoTb fileInfo) {
        if (fileInfo == null) {
            return null;
        }
        return new DataFileInfoResponse(fileInfo.getId(), fileInfo.getFileCode(), fileInfo.getFileName(), fileInfo.getObjectKey(), fileInfo.getFileHash(), fileInfo.getStatus(),
                fileInfo.getTotalCount(), fileInfo.getErrorMessage(), fileInfo.getCreatedAt(), fileInfo.getUpdatedAt(), fileInfo.getCreatedBy(), fileInfo.getCreatedByName());
    }

    private DataFileInfoTb toEntity(DataFileInfoCreateRequest reqDTO) {
        DataFileInfoTb fileInfo = new DataFileInfoTb();
        fileInfo.setFileName(reqDTO.fileName());
        fileInfo.setObjectKey(reqDTO.objectKey());
        fileInfo.setFileHash(reqDTO.fileHash());
        fileInfo.setStatus(reqDTO.status());
        fileInfo.setTotalCount(reqDTO.totalCount());
        fileInfo.setErrorMessage(reqDTO.errorMessage());
        return fileInfo;
    }

    private DataFileInfoTb toEntity(DataFileInfoUpdateRequest reqDTO) {
        DataFileInfoTb fileInfo = new DataFileInfoTb();
        fileInfo.setFileName(reqDTO.fileName());
        fileInfo.setObjectKey(reqDTO.objectKey());
        fileInfo.setFileHash(reqDTO.fileHash());
        fileInfo.setStatus(reqDTO.status());
        fileInfo.setTotalCount(reqDTO.totalCount());
        fileInfo.setErrorMessage(reqDTO.errorMessage());
        return fileInfo;
    }
}
