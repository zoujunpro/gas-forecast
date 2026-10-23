package com.gas.forecast.business.service;

import java.util.Map;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 执行任务查询接口，对应任务过程、步骤和结果查询 Controller。 */
public interface ExecutionTaskQueryService {
    SseEmitter subscribe(String taskNo);

    Map<String, Object> overview(String taskNo);

    Map<String, Object> details(String taskNo, long pageNo, long pageSize, String status);

    Map<String, Object> steps(String detailNo);

    Map<String, Object> result(String detailNo);
}
