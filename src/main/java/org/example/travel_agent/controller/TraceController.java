package org.example.travel_agent.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.example.travel_agent.common.UserContext;
import org.example.travel_agent.dto.Result;
import org.example.travel_agent.dto.trace.ConversationTraceVO;
import org.example.travel_agent.dto.trace.TraceDetailVO;
import org.example.travel_agent.service.AgentTraceService;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/api/admin/trace")
@RestController
@RequiredArgsConstructor
public class TraceController {

    private final AgentTraceService agentTraceService;

    @GetMapping("/conversations")
    public Result<Page<ConversationTraceVO>> listConversations(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = UserContext.get().getId();
        Page<ConversationTraceVO> page = agentTraceService.listConversations(userId, current, size);
        return Result.success("查询成功", page);
    }

    @GetMapping("/{conversationId}")
    public Result<TraceDetailVO> getTraceDetail(@PathVariable String conversationId) {
        TraceDetailVO detail = agentTraceService.getTraceDetail(conversationId);
        return Result.success("查询成功", detail);
    }
}
