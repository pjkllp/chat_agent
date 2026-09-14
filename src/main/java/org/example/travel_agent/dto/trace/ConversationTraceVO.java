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
    /** 该会话包含的轮次数量（按 message_id 去重）。 */
    private Long turnCount;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private Long totalDuration;
}
