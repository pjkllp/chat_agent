package org.example.travel_agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "aliyun.oss")
public class AliyunOssProperties {

    private String endpoint;
    private String bucket;
    private String accessKeyId;
    private String accessKeySecret;
    /** 公网访问前缀，例如 https://mybucket.oss-cn-hangzhou.aliyuncs.com（不含结尾斜杠） */
    private String publicHost;
}
