package org.example.travel_agent.dto.trace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

/** 一个轮次的链路：同一次提问触发的全部节点执行。 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TraceTurnVO {

    /** 同轮所有节点行共用的 message_id，也等于该轮 AI 回复在 t_ai_chat_memory 的主键。 */
    private Long messageId;

    /** FINISH：全部节点正常结束；ERROR：存在失败节点；RUNNING：存在未结束的节点。 */
    private String status;

    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private Long totalDuration;
    private Integer nodeCount;
    private List<NodeTraceStep> steps;
}
