package org.example.travel_agent.dto.knowledge;

import lombok.Data;

@Data
public class CreateKnowledgeBaseRequest {

    private String kbName;

    private String description;
}
