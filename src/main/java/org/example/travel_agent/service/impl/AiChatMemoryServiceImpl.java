package org.example.travel_agent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.example.travel_agent.dao.entity.AiChatMemoryEntity;
import org.example.travel_agent.dao.mapper.AiChatMemoryMapper;
import org.example.travel_agent.service.AiChatMemoryService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiChatMemoryServiceImpl extends ServiceImpl<AiChatMemoryMapper, AiChatMemoryEntity> implements AiChatMemoryService {

    @Override
    public void saveMessage(AiChatMemoryEntity message) {
        this.save(message);
    }

    @Override
    public List<AiChatMemoryEntity> listByUserAndConversation(Long userId, String conversationId) {
        return this.list(
                Wrappers.<AiChatMemoryEntity>lambdaQuery()
                        .eq(AiChatMemoryEntity::getUserId, userId)
                        .eq(AiChatMemoryEntity::getConversationId, conversationId)
                        .orderByAsc(AiChatMemoryEntity::getCreateTime)
        );
    }
}
