package org.example.travel_agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CancelRequest {

    /** 要取消的那一轮对话的 messageId，由 SSE 的 turn 事件下发。 */
    private Long messageId;

}
