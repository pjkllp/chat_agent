package org.example.travel_agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatAttachmentDTO {

    public static final String TYPE_IMAGE = "IMAGE";
    public static final String TYPE_AUDIO = "AUDIO";

    private static final Map<String, String> ALLOWED = Map.ofEntries(
            Map.entry("image/jpeg", TYPE_IMAGE),
            Map.entry("image/png", TYPE_IMAGE),
            Map.entry("image/webp", TYPE_IMAGE),
            Map.entry("image/gif", TYPE_IMAGE),
            Map.entry("audio/mpeg", TYPE_AUDIO),
            Map.entry("audio/wav", TYPE_AUDIO),
            Map.entry("audio/x-wav", TYPE_AUDIO),
            Map.entry("audio/mp4", TYPE_AUDIO),
            Map.entry("audio/webm", TYPE_AUDIO),
            Map.entry("audio/ogg", TYPE_AUDIO)
    );

    private String url;
    private String type;      // IMAGE | AUDIO
    private String fileName;
    private String mimeType;
    private Long size;

    /** 返回 IMAGE / AUDIO；不支持的类型返回 null。 */
    public static String resolveType(String mimeType) {
        if (mimeType == null) {
            return null;
        }
        return ALLOWED.get(mimeType.toLowerCase());
    }
}
