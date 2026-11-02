package com.gas.forecast.business.service;

import java.util.Map;

/** 执行任务查询接口，对应任务过程、步骤和结果查询 Controller。 */
public interface ExecutionTaskQueryService {
    Map<String, Object> overview(String taskNo);

    Map<String, Object> details(String taskNo, long pageNo, long pageSize, String status);

    Map<String, Object> events(String detailNo);

    Map<String, Object> result(String detailNo);
}
