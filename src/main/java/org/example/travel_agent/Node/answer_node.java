package org.example.travel_agent.Node;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.travel_agent.Exceptions.CancelException;
import org.example.travel_agent.advisor.PersistMemoryAdvisor;
import org.example.travel_agent.common.SseEventUtil;
import org.example.travel_agent.dto.ChatAttachmentDTO;
import org.example.travel_agent.service.ChatAttachmentSupport;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class answer_node implements NodeAction {

    private final ChatClient deepThinkChatClient;

    private final PersistMemoryAdvisor persistMemoryAdvisor;

    private final SseEventUtil sseEventUtil;

    private final ChatAttachmentSupport chatAttachmentSupport;

    @Value("${app.chat.vision-model:qwen-vl-max}")
    private String visionModel;

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        if (!sseEventUtil.hasEmitter(state)) {
            log.warn("SSE emitter is missing, skip answer streaming");
            return Map.of();
        }

        sseEventUtil.sendNodeStatus(state, "answer_node", "start", "开始生成最终答案");

        try {
            String summaryPrompt = state.value("summary_prompt", "");

            String originalQuestion = state.value("original_question", "");

            String rewriteQuestion = state.value("rewrite_question", "");
            if (summaryPrompt == null || summaryPrompt.isBlank()) {
                summaryPrompt = state.value("rewrite_question", state.value("original_question", ""));
            }

            ClassPathResource classPathResource = new ClassPathResource("prompt/answer.st");

            String conversationId = state.value("conversationId", "");

            Long userId = state.value("userId", Long.class).orElse(null);

            Long messageId = state.value("messageId", Long.class).orElse(null);

            @SuppressWarnings("unchecked")
            List<ChatAttachmentDTO> attachments = state.value("attachments")
                    .filter(List.class::isInstance)
                    .map(v -> (List<ChatAttachmentDTO>) v)
                    .orElse(List.of());

            AtomicBoolean hasStreamChunk = new AtomicBoolean(false);
            ChatClient.ChatClientRequestSpec streamSpec = deepThinkChatClient.prompt()
                    .advisors(persistMemoryAdvisor)
                    .advisors(advisorSpec -> advisorSpec.params(
                            Map.of(
                                    "conversationId", conversationId,
                                    "userId", userId,
                                    "messageId", messageId
                            )
                    ))
                    .system(classPathResource)
                    .system(summaryPrompt)
                    .messages(chatAttachmentSupport.buildUserMessage(originalQuestion, attachments));
            if (chatAttachmentSupport.hasImage(attachments)) {
                // multiModel=true 才会走多模态端点，否则图片被发往文本端点并报 url error
                streamSpec.options(DashScopeChatOptions.builder().model(visionModel).temperature(0.1).multiModel(true).build());
            }
            streamSpec.stream()
                    .content()
                    .doOnNext(data -> {
                        hasStreamChunk.set(true);
                        // 取消必须抛在 ClientDisconnectedException 包装之外，否则会被当成客户端断开吞掉
                        if (sseEventUtil.isCancelled(state)) {
                            log.info("answer_node canceled for conversationId={}", conversationId);
                            try {
                                sseEventUtil.sendNodeStatus(state, "answer_node", "cancel", "用户取消了本轮对话");
                            } catch (IOException e) {
                                log.warn("answer_node send cancel status failed", e);
                            }
                            throw new CancelException("用户取消了本轮对话");
                        }
                        try {
                            sseEventUtil.sendAnswerChunk(state, data);
                        } catch (Exception e) {
                            // 用户切换会话/新建会话会主动中止前端连接，这里不应按系统错误处理
                            throw new ClientDisconnectedException("SSE client disconnected", e);
                        }
                    })
                    .blockLast();

            if (!hasStreamChunk.get()) {
                log.warn("answer_node stream produced no chunk, fallback to call().content()");
                ChatClient.ChatClientRequestSpec fallbackSpec = deepThinkChatClient.prompt()
                        .advisors(persistMemoryAdvisor)
                        .advisors(advisorSpec -> advisorSpec.params(
                                Map.of(
                                        "conversationId", conversationId,
                                        "userId", userId,
                                        "messageId", messageId
                                )
                        ))
                        .system(classPathResource)
                        .messages(chatAttachmentSupport.buildUserMessage(summaryPrompt, attachments));
                if (chatAttachmentSupport.hasImage(attachments)) {
                    fallbackSpec.options(DashScopeChatOptions.builder().model(visionModel).temperature(0.1).multiModel(true).build());
                }
                String fullAnswer = fallbackSpec.call().content();
                // 兜底调用是阻塞的，取消可能在它返回前就发生了，这里补一次检查
                if (sseEventUtil.isCancelled(state)) {
                    log.info("answer_node canceled after fallback call, conversationId={}", conversationId);
                    sseEventUtil.sendNodeStatus(state, "answer_node", "cancel", "用户取消了本轮对话");
                    throw new CancelException("用户取消了本轮对话");
                }
                if (fullAnswer != null && !fullAnswer.isBlank()) {
                    sseEventUtil.sendAnswerChunk(state, fullAnswer);
                }
            }

            sseEventUtil.sendNodeStatus(state, "answer_node", "finish", "最终答案已生成");
            sseEventUtil.sendNodeStatus(state, "answer_node", "done", "工作流执行完成");
            return Map.of();
        } catch (CancelException e) {
            // 用户主动取消：cancel trace 已在节点内落库，不记 error，直接结束本轮
            log.info("answer_node canceled, stop streaming gracefully");
            return Map.of();
        } catch (Exception e) {
            if (isClientDisconnected(e)) {
                // 前端主动断开属于正常中止，不算系统错误，也不向上抛
                log.info("SSE client disconnected, stop streaming answer gracefully");
                return Map.of();
            }
            log.error("Generate answer failed", e);
            sseEventUtil.markNodeError(state, "answer_node", e);
            throw e;
        } finally {
            sseEventUtil.complete(state);
        }
    }

    private boolean isClientDisconnected(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            if (cur instanceof ClientDisconnectedException) {
                return true;
            }
            String msg = cur.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase();
                if (lower.contains("clientabortexception")
                        || lower.contains("asyncrequestnotusableexception")
                        || lower.contains("broken pipe")
                        || lower.contains("connection reset")
                        || msg.contains("已建立的连接")) {
                    return true;
                }
            }
            cur = cur.getCause();
        }
        return false;
    }

    private static class ClientDisconnectedException extends RuntimeException {
        public ClientDisconnectedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
