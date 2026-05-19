package org.example.travel_agent.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.travel_agent.dao.entity.AiChatMemoryEntity;
import org.example.travel_agent.dto.ConversationSummary;
import org.example.travel_agent.dto.ConversationVO;
import org.example.travel_agent.memory.LLMMemory;
import org.example.travel_agent.service.ConversationService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private final LLMMemory llmMemory;

    @Override
    public Page<ConversationVO> listConversations(Long userId, Long beforeTimestamp, int current, int size) {
        OffsetDateTime endTime = beforeTimestamp != null
                ? OffsetDateTime.ofInstant(Instant.ofEpochMilli(beforeTimestamp), ZoneId.systemDefault())
                : OffsetDateTime.now();
        OffsetDateTime startTime = endTime.minusDays(7);

        List<ConversationSummary> summaries = llmMemory.listConversationsByUser(userId, startTime, endTime);

        List<ConversationVO> vos = new ArrayList<>();
        for (ConversationSummary summary : summaries) {
            String title = summary.getTitle();
            if (title == null || title.isBlank()) {
                List<AiChatMemoryEntity> msgs = getMessages(userId, summary.getConversationId());
                title = msgs.stream()
                        .filter(m -> "USER".equals(m.getMessageType()))
                        .map(AiChatMemoryEntity::getContent)
                        .findFirst()
                        .orElse("未命名对话");
                if (title.length() > 48) {
                    title = title.substring(0, 48);
                }
            }
            vos.add(ConversationVO.builder()
                    .conversationId(summary.getConversationId())
                    .title(title)
                    .messagesCount(summary.getMsgCount())
                    .startTime(summary.getFirstMsgTime())
                    .lastTime(summary.getLastMsgTime())
                    .build());
        }

        Page<ConversationVO> page = new Page<>(current, size);
        page.setTotal(vos.size());
        int fromIndex = (current - 1) * size;
        int toIndex = Math.min(fromIndex + size, vos.size());
        page.setRecords(fromIndex >= vos.size() ? List.of() : vos.subList(fromIndex, toIndex));
        page.setPages((long) Math.ceil((double) vos.size() / size));
        return page;
    }

    @Override
    public List<AiChatMemoryEntity> getMessages(Long userId, String conversationId) {
        return llmMemory.getMemory(userId, conversationId, -1);
    }

    @Override
    public void deleteConversation(Long userId, String conversationId) {
        llmMemory.delete(userId, conversationId);
    }
}
