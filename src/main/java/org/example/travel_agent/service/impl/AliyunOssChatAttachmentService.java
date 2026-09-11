package org.example.travel_agent.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.example.travel_agent.Exceptions.ClientException;
import org.example.travel_agent.config.AliyunOssProperties;
import org.example.travel_agent.dto.ChatAttachmentDTO;
import org.example.travel_agent.service.ChatAttachmentService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AliyunOssChatAttachmentService implements ChatAttachmentService {

    private static final long MAX_SIZE = 20L * 1024 * 1024;

    private static final Map<String, String> EXT_BY_MIME = Map.ofEntries(
            Map.entry("image/jpeg", "jpg"),
            Map.entry("image/png", "png"),
            Map.entry("image/webp", "webp"),
            Map.entry("image/gif", "gif"),
            Map.entry("audio/mpeg", "mp3"),
            Map.entry("audio/mp4", "m4a"),
            // 以下三项是 Tika 内容检测对 wav/ogg/webm 的实际输出
            Map.entry("audio/vnd.wave", "wav"),
            Map.entry("audio/vorbis", "ogg"),
            Map.entry("video/webm", "webm"),
            Map.entry("audio/wav", "wav"),
            Map.entry("audio/x-wav", "wav"),
            Map.entry("audio/webm", "webm"),
            Map.entry("audio/ogg", "ogg")
    );

    private final ObjectProvider<OSS> aliyunOssClientProvider;
    private final AliyunOssProperties properties;
    private final Tika tika = new Tika();

    @Override
    public ChatAttachmentDTO upload(MultipartFile file, String conversationId) throws ClientException {
        if (file == null || file.isEmpty()) {
            throw new ClientException("上传文件不能为空");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new ClientException("文件大小超过 20MB 限制");
        }
        if (StrUtil.isBlank(conversationId)) {
            throw new ClientException("conversationId 不能为空");
        }
        if (StrUtil.isBlank(properties.getEndpoint())
                || StrUtil.isBlank(properties.getBucket())
                || StrUtil.isBlank(properties.getPublicHost())) {
            log.error("OSS 未配置，无法上传附件：请设置 OSS_ENDPOINT / OSS_BUCKET / OSS_PUBLIC_HOST");
            throw new ClientException("附件服务未配置，请联系管理员");
        }

        String detected;
        try (InputStream in = file.getInputStream()) {
            detected = tika.detect(in, file.getOriginalFilename());
        } catch (Exception e) {
            throw new RuntimeException("文件类型检测失败", e);
        }
        String type = ChatAttachmentDTO.resolveType(detected);
        if (type == null) {
            throw new ClientException("不支持的文件类型: " + detected);
        }

        String ext = extOfDetected(detected);
        String objectKey = "chat/" + conversationId + "/" + IdUtil.getSnowflakeNextIdStr() + "." + ext;
        try (InputStream in = file.getInputStream()) {
            OSS aliyunOssClient = aliyunOssClientProvider.getObject();
            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentType(detected);
            meta.setContentLength(file.getSize());
            aliyunOssClient.putObject(properties.getBucket(), objectKey, in, meta);
        } catch (Exception e) {
            log.error("OSS upload failed, file={}, msg={}", file.getOriginalFilename(), e.getMessage(), e);
            throw new RuntimeException("上传到 OSS 失败", e);
        }

        String url = StrUtil.removeSuffix(properties.getPublicHost(), "/") + "/" + objectKey;
        return ChatAttachmentDTO.builder()
                .url(url)
                .type(type)
                .fileName(file.getOriginalFilename())
                .mimeType(detected)
                .size(file.getSize())
                .build();
    }

    private static String extOfDetected(String detected) {
        String ext = EXT_BY_MIME.get(detected.toLowerCase());
        if (ext != null) {
            return ext;
        }
        return detected.substring(detected.indexOf('/') + 1);
    }
}
