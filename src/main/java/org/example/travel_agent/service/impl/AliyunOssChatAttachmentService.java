package org.example.travel_agent.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.example.travel_agent.config.AliyunOssProperties;
import org.example.travel_agent.dto.ChatAttachmentDTO;
import org.example.travel_agent.service.ChatAttachmentService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class AliyunOssChatAttachmentService implements ChatAttachmentService {

    private static final long MAX_SIZE = 20L * 1024 * 1024;

    private final ObjectProvider<OSS> aliyunOssClientProvider;
    private final AliyunOssProperties properties;
    private final Tika tika = new Tika();

    @Override
    public ChatAttachmentDTO upload(MultipartFile file, String conversationId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new IllegalArgumentException("文件大小超过 20MB 限制");
        }
        if (StrUtil.isBlank(conversationId)) {
            throw new IllegalArgumentException("conversationId 不能为空");
        }

        String detected;
        try (InputStream in = file.getInputStream()) {
            detected = tika.detect(in, file.getOriginalFilename());
        } catch (Exception e) {
            throw new RuntimeException("文件类型检测失败", e);
        }
        String type = ChatAttachmentDTO.resolveType(detected);
        if (type == null) {
            throw new IllegalArgumentException("不支持的文件类型: " + detected);
        }

        String ext = extOf(file.getOriginalFilename(), detected);
        String objectKey = "chat/" + conversationId + "/" + IdUtil.getSnowflakeNextIdStr() + "." + ext;
        OSS aliyunOssClient = aliyunOssClientProvider.getObject();
        try (InputStream in = file.getInputStream()) {
            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentType(detected);
            meta.setContentLength(file.getSize());
            aliyunOssClient.putObject(properties.getBucket(), objectKey, in, meta);
        } catch (Exception e) {
            log.error("OSS upload failed, file={}, msg={}", file.getOriginalFilename(), e.getMessage(), e);
            throw new RuntimeException("上传到 OSS 失败");
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

    private String extOf(String fileName, String mimeType) {
        if (fileName != null && fileName.lastIndexOf('.') >= 0) {
            return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        }
        return mimeType.substring(mimeType.indexOf('/') + 1);
    }
}
