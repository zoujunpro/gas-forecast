package com.gas.forecast.business.controller;

import com.gas.forecast.business.dto.AiChatRequest;
import com.gas.forecast.business.dto.AiChatResponse;
import com.gas.forecast.business.dto.AiConversationResponse;
import com.gas.forecast.business.dto.AiConversationRenameRequest;
import com.gas.forecast.business.service.AiChatService;
import com.gas.forecast.business.service.AiChatStreamService;
import com.gas.forecast.business.service.AiConversationService;
import com.gas.forecast.common.core.ResponseResult;
import com.gas.forecast.common.security.annotation.RequirePermission;
import com.gas.forecast.common.web.WebLog;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 会话及会话关联任务查询接口。
 *
 * <p>Vue 只调用本 Controller，不直接调用 Python Agent。Java 负责用户权限校验、
 * 会话和消息持久化、Agent Run 创建以及业务对象引用保存；Python 仅负责 Agent 编排。</p>
 *
 * <p>会话接口中的 status 表示 Agent 本轮处理状态。训练、预测任务的真实执行状态，
 * 应通过训练进度、预测进度或后续统一执行任务接口查询。</p>
 */
@RestController
@RequestMapping("/ai/conversations")
@RequiredArgsConstructor
public class AiConversationController {
    private final AiConversationService conversationService;
    private final AiChatService chatService;
    private final AiChatStreamService chatStreamService;

    /**
     * 发送一条智能体会话消息。
     *
     * <p>首次请求不传 conversationNo，由 Java 创建会话；继续已有会话时传入
     * conversationNo。turnId 和 runNo 始终由 Java 生成，并通过响应返回前端。
     * 页面恢复时通过 conversationNo 加载历史消息、Agent Run 和关联任务，不重复提交聊天请求。</p>
     *
     * @param request 用户消息、可选会话编号及业务上下文
     * @return Agent 本轮回复、服务端生成的会话标识以及关联的业务对象引用
     */
    @PostMapping("/chat")
    @WebLog("发送智能体会话消息")
    @RequirePermission("ai:assistant:use")
    public ResponseResult<AiChatResponse> chat(@Valid @RequestBody AiChatRequest request) {
        return ResponseResult.success(chatService.chat(request));
    }

    /** SSE 展示接口：最终结果先完整落库，再按短句推送，避免持久化半截回复。 */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequirePermission("ai:assistant:use")
    public SseEmitter chatStream(@Valid @RequestBody AiChatRequest request) {
        return chatStreamService.chat(request);
    }

    /**
     * 查询当前登录用户最近的会话列表。
     *
     * <p>列表只返回会话摘要，不加载历史消息正文；需要查看消息时调用会话详情接口。</p>
     *
     * @return 当前用户的会话摘要列表，按最近消息时间倒序排列
     */
    @GetMapping
    @RequirePermission("ai:assistant:use")
    public ResponseResult<List<AiConversationResponse>> list() {
        return ResponseResult.success(conversationService.list());
    }

    /**
     * 查询一个会话的完整详情。
     *
     * <p>返回历史消息、每轮 Agent Run 状态以及每条消息关联的执行任务、
     * 预测结果引用。</p>
     *
     * @param conversationNo 会话唯一编号
     * @return 当前用户有权访问的会话详情
     */
    @GetMapping("/detail")
    @RequirePermission("ai:assistant:use")
    public ResponseResult<AiConversationResponse> detail(@RequestParam String conversationNo,
            @RequestParam(required = false) Long beforeMessageId,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseResult.success(conversationService.detail(conversationNo, beforeMessageId, pageSize));
    }

    /**
     * 修改会话标题。
     *
     * @param request 会话编号和新的会话标题
     * @return 成功时返回空数据
     */
    @PostMapping("/rename")
    @RequirePermission("ai:assistant:use")
    public ResponseResult<Void> rename(@Valid @RequestBody AiConversationRenameRequest request) {
        conversationService.rename(request.getConversationNo(), request.getTitle());
        return ResponseResult.success(null);
    }

    /**
     * 删除当前用户的一个会话。
     *
     * <p>逻辑删除会话，后续正常查询及对话不再使用该会话；消息、消息业务引用和
     * Agent Run 会保留。不会删除已经创建的训练、预测或统一执行任务及其业务结果。</p>
     *
     * @param conversationNo 要删除的会话唯一编号
     * @return 成功时返回空数据
     */
    @GetMapping("/delete")
    @RequirePermission("ai:assistant:use")
    public ResponseResult<Void> delete(@RequestParam String conversationNo) {
        conversationService.delete(conversationNo);
        return ResponseResult.success(null);
    }

}
