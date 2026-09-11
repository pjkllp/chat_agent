package org.example.travel_agent.service;

import lombok.extern.slf4j.Slf4j;
import org.example.travel_agent.Exceptions.ClientException;
import org.example.travel_agent.dto.ChatAttachmentDTO;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ChatAttachmentSupport {

    private final TranscriptionModel transcriptionModel;
    private final String ossPublicHost;
    private final int maxAttachments;

    public ChatAttachmentSupport(TranscriptionModel transcriptionModel,
                                 @Value("${aliyun.oss.public-host:}") String ossPublicHost,
                                 @Value("${app.chat.max-attachments:4}") int maxAttachments) {
        this.transcriptionModel = transcriptionModel;
        this.ossPublicHost = ossPublicHost;
        this.maxAttachments = maxAttachments;
    }

    public void validate(List<ChatAttachmentDTO> attachments) throws ClientException {
        if (attachments == null || attachments.isEmpty()) {
            return;
        }
        if (attachments.size() > maxAttachments) {
            throw new ClientException("单条消息最多 " + maxAttachments + " 个附件");
        }
        String expectedHost = hostOf(ossPublicHost);
        if (expectedHost.isEmpty()) {
            throw new ClientException("附件地址不合法");
        }
        for (ChatAttachmentDTO a : attachments) {
            if (a == null || a.getUrl() == null || !isAllowedUrl(a.getUrl(), expectedHost)) {
                throw new ClientException("附件地址不合法");
            }
        }
    }

    /** 仅放行 scheme 为 http/https 且 host 与白名单一致的绝对 URL。 */
    private boolean isAllowedUrl(String url, String expectedHost) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                return false;
            }
            return expectedHost.equalsIgnoreCase(String.valueOf(uri.getHost()));
        } catch (Exception e) {
            return false;
        }
    }

    public boolean hasImage(List<ChatAttachmentDTO> attachments) {
        return attachments != null && attachments.stream()
                .anyMatch(a -> a != null && ChatAttachmentDTO.TYPE_IMAGE.equals(a.getType()));
    }

    /** 把音频附件逐个转写为文本；无音频返回 ""。失败降级为占位文案，不抛出。 */
    public String transcribeAudios(List<ChatAttachmentDTO> attachments) {
        if (attachments == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ChatAttachmentDTO a : attachments) {
            if (a == null || !ChatAttachmentDTO.TYPE_AUDIO.equals(a.getType())) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            try {
                String text = transcriptionModel.transcribe(new UrlResource(URI.create(a.getUrl())));
                sb.append(text == null || text.isBlank() ? "[语音转写为空]" : "[语音转写] " + text);
            } catch (Exception e) {
                log.warn("audio transcription failed, url={}", a.getUrl(), e);
                sb.append("[语音转写失败]");
            }
        }
        return sb.toString();
    }

    /** 构建最终发给模型的 UserMessage：音频转写并入文本，图片作为 Media(URL)。 */
    public UserMessage buildUserMessage(String question, List<ChatAttachmentDTO> attachments) {
        String text = question == null ? "" : question;
        //解析音频
        String transcript = transcribeAudios(attachments);
        if (!transcript.isBlank()) {
            text = text.isBlank() ? transcript : text + "\n" + transcript;
        }

        //解析图片
        List<Media> media = new ArrayList<>();
        if (attachments != null) {
            for (ChatAttachmentDTO a : attachments) {
                if (a != null && ChatAttachmentDTO.TYPE_IMAGE.equals(a.getType())) {
                    media.add(Media.builder()
                            .mimeType(MimeTypeUtils.parseMimeType(a.getMimeType()))
                            .data(a.getUrl())   // 必须是 String，DashScope 会原样透传
                            .build());
                }
            }
        }

        UserMessage.Builder builder = UserMessage.builder().text(text);
        if (!media.isEmpty()) {
            builder.media(media);
        }
        if (attachments != null && !attachments.isEmpty()) {
            builder.metadata(java.util.Map.of("attachments", attachments));
        }
        return builder.build();
    }

    private String hostOf(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            String host = URI.create(url).getHost();
            return host == null ? "" : host;
        } catch (Exception e) {
            return "";
        }
    }
}
