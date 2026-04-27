package org.example.travel_agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "rustfs")
public class RustfsProperties {

    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucket;
    private boolean secure = false;
}
