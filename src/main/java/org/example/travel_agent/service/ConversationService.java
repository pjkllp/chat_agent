package org.example.travel_agent.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.travel_agent.dao.entity.AiChatMemoryEntity;
import org.example.travel_agent.dto.ConversationVO;

import java.util.List;

public interface ConversationService {

    Page<ConversationVO> listConversations(Long userId, Long beforeTimestamp, int current, int size);

    List<AiChatMemoryEntity> getMessages(Long userId, String conversationId);

    void deleteConversation(Long userId, String conversationId);
}
