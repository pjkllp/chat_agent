package org.example.travel_agent.dto.knowledge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class KbDocumentPageResponse {

    private Long id;

    private Long kbId;

    private String fileName;

    private String bucket;

    private String objectKey;

    private Integer status;

    private String statusDesc;

    private Boolean enabled;

    private Integer version;

    private String errorMessage;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
