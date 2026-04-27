package org.example.travel_agent.dto.knowledge;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CreateKnowledgeBaseResponse {

    private String kbId;

    private String name;

    private String description;
}
