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
public class KbChunkPageResponse {

    private Long id;

    private Long kbId;

    private Long docId;

    private Integer chunkNo;

    private String content;

    private Boolean enabled;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
