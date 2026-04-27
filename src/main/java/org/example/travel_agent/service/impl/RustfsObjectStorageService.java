package org.example.travel_agent.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.travel_agent.config.RustfsProperties;
import org.example.travel_agent.service.ObjectStorageService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class RustfsObjectStorageService implements ObjectStorageService {

    private final MinioClient rustfsMinioClient;
    private final RustfsProperties rustfsProperties;

    @Override
    public UploadResult upload(MultipartFile file, String dir) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        String bucket = rustfsProperties.getBucket();
        if (StrUtil.isBlank(bucket)) {
            throw new IllegalStateException("rustfs.bucket 未配置");
        }
        String safeDir = StrUtil.blankToDefault(dir, "knowledge");
        String originalName = StrUtil.blankToDefault(file.getOriginalFilename(), "unknown.bin");
        String ext = "";
        int idx = originalName.lastIndexOf('.');
        if (idx >= 0 && idx < originalName.length() - 1) {
            ext = originalName.substring(idx);
        }
        String objectKey = safeDir + "/" + IdUtil.getSnowflakeNextIdStr() + ext;
        try (InputStream in = file.getInputStream()) {
            ensureBucketExists(bucket);
            rustfsMinioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .stream(in, file.getSize(), -1)
                            .contentType(StrUtil.blankToDefault(file.getContentType(), "application/octet-stream"))
                            .build()
            );
            return new UploadResult(objectKey, bucket);
        } catch (Exception e) {
            log.error("RustFS upload failed, file={}, msg={}", originalName, e.getMessage(), e);
            throw new RuntimeException("上传 RustFS 失败");
        }
    }

    private void ensureBucketExists(String bucket) throws Exception {
        boolean exists = rustfsMinioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            rustfsMinioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }
}
