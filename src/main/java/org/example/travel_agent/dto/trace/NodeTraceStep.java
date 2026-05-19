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
public class NodeTraceStep {

    private String nodeName;
    private String status;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private Long duration;
    private String resultData;
    private String errorMessage;
}
