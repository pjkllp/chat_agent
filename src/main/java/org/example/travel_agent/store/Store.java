package org.example.travel_agent.store;

import org.example.travel_agent.dao.entity.AiChatMemoryEntity;

import java.util.List;

public interface Store {

    void append(List<AiChatMemoryEntity> messages);

    List<AiChatMemoryEntity> load();
}
