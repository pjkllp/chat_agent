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
    private Integer totalTurns;
    /** 按轮次分组，轮次之间按首次节点开始时间升序；每轮内部节点按开始时间升序。 */
    private List<TraceTurnVO> turns;
}
