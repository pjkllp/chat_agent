package org.example.travel_agent.dto.trace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TraceStatsVO {

    private long totalConversations;
    private long errorConversations;
    private String errorRate;
    private Double avgDuration;
    private long totalNodes;
    private long errorNodes;
}
