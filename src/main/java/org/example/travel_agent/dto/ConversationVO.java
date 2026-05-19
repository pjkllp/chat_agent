package org.example.travel_agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConversationVO {

    private String conversationId;
    private String title;
    private Long messagesCount;
    private OffsetDateTime startTime;
    private OffsetDateTime lastTime;
}
