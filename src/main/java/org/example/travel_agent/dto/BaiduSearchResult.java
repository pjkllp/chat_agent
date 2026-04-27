package org.example.travel_agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BaiduSearchResult {

    private String title;

    private String url;

    private String snippet;
}
