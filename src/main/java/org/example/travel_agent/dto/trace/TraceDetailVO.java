package org.example.travel_agent.dto.trace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TraceDetailVO {

    private String conversationId;
    private List<NodeTraceStep> steps;
}
