package org.example.travel_agent.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IntentDTO {

    private String searchIntent;

    private String retrieveIntent;

    private String toolIntent;

}
