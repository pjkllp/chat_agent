package org.example.travel_agent.dto.knowledge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChuckRequest {

    //分块策略
    private int chuckStrategy;

    //文档所属知识库id
    private long kbId;

    //需要分块的文档id
    private long doc_id;

}
