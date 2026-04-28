package org.example.travel_agent.dto.knowledge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeBasePageResponse {

    private Long id;

    private String kbName;

    private String description;

    private Boolean enabled;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
