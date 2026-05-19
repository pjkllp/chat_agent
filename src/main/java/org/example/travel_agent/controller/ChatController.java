package org.example.travel_agent.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.example.travel_agent.common.UserContext;
import org.example.travel_agent.dao.entity.AiChatMemoryEntity;
import org.example.travel_agent.dto.ChatRequest;
import org.example.travel_agent.dto.ConversationVO;
import org.example.travel_agent.dto.Result;
import org.example.travel_agent.service.ChatService;
import org.example.travel_agent.service.ConversationService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RequestMapping("/api/chat")
@RestController
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ConversationService conversationService;

    @PostMapping("/chat")
    public SseEmitter deepThink(@RequestBody ChatRequest requestParam) {
        SseEmitter sse = new SseEmitter(0L);
        chatService.chatStream(requestParam, sse);
        return sse;
    }

    @GetMapping("/conversations")
    public Result<Page<ConversationVO>> listConversations(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long before) {
        Long userId = UserContext.get().getId();
        Page<ConversationVO> page = conversationService.listConversations(userId, before, current, size);
        return Result.success("查询成功", page);
    }

    @GetMapping("/messages/{conversationId}")
    public Result<List<AiChatMemoryEntity>> getMessages(@PathVariable String conversationId) {
        Long userId = UserContext.get().getId();
        List<AiChatMemoryEntity> messages = conversationService.getMessages(userId, conversationId);
        return Result.success("查询成功", messages);
    }

    @DeleteMapping("/conversations/{conversationId}")
    public Result<Void> deleteConversation(@PathVariable String conversationId) {
        Long userId = UserContext.get().getId();
        conversationService.deleteConversation(userId, conversationId);
        return Result.success("删除成功");
    }
}
