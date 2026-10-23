package com.gas.forecast.business.controller;

import com.gas.forecast.business.service.ExecutionTaskQueryService;
import com.gas.forecast.common.core.ResponseResult;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 执行任务过程、步骤与结果的 HTTP 查询入口。 */
@RestController
@RequestMapping("/execution-tasks")
@RequiredArgsConstructor
public class ExecutionTaskQueryController {
    private final ExecutionTaskQueryService executionTaskQueryService;

    /** 页面刷新先读取快照；执行中再通过 SSE 接收步骤更新。 */
    @GetMapping(value = "/{taskNo}/events", produces = "text/event-stream")
    public SseEmitter events(@PathVariable String taskNo) {
        return executionTaskQueryService.subscribe(taskNo);
    }

    @GetMapping("/{taskNo}")
    public ResponseResult<Map<String, Object>> overview(@PathVariable String taskNo) {
        return ResponseResult.success(executionTaskQueryService.overview(taskNo));
    }

    @GetMapping("/{taskNo}/details")
    public ResponseResult<Map<String, Object>> details(@PathVariable String taskNo, @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize, @RequestParam(required = false) String status) {
        return ResponseResult.success(executionTaskQueryService.details(taskNo, pageNo, pageSize, status));
    }

    @GetMapping("/details/{detailNo}/steps")
    public ResponseResult<Map<String, Object>> steps(@PathVariable String detailNo) {
        return ResponseResult.success(executionTaskQueryService.steps(detailNo));
    }

    @GetMapping("/details/{detailNo}/result")
    public ResponseResult<Map<String, Object>> result(@PathVariable String detailNo) {
        return ResponseResult.success(executionTaskQueryService.result(detailNo));
    }
}
