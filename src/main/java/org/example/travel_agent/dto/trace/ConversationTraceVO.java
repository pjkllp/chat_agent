package org.example.travel_agent.dto.trace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConversationTraceVO {

    private String conversationId;
    private Long userId;
    private Long nodeCount;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private Long totalDuration;
}
