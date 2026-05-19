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
public class ConversationSummary {

    private String conversationId;
    private Long userId;
    private OffsetDateTime firstMsgTime;
    private OffsetDateTime lastMsgTime;
    private Long msgCount;
    private String title;
}
