package org.example.travel_agent.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.travel_agent.dao.entity.AgentTraceEntity;
import org.example.travel_agent.dto.trace.ConversationTraceVO;
import org.example.travel_agent.dto.trace.TraceDetailVO;
import org.example.travel_agent.dto.trace.TraceStatsVO;

public interface AgentTraceService {

    void recordStart(String conversationId, Long userId, String nodeName);

    void recordFinish(String conversationId, String nodeName, String resultData);

    void recordError(String conversationId, String nodeName, String errorMessage);

    Page<ConversationTraceVO> listConversations(Long userId, int current, int size);

    TraceDetailVO getTraceDetail(String conversationId);

    TraceStatsVO getTraceStats(Long userId);
}
