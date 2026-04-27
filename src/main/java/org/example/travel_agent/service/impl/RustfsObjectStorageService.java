package org.example.travel_agent.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
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
    public KnowledgeSpace createKnowledgeSpace(String kbId) {
        if (StrUtil.isBlank(kbId)) {
            throw new IllegalArgumentException("kbId 不能为空");
        }
        String trimmedKbId = kbId.trim();
        String bucket = buildKbBucketName(trimmedKbId);
        try {
            ensureBucketExists(bucket);
            return new KnowledgeSpace(trimmedKbId, bucket);
        } catch (Exception e) {
            log.error("RustFS create knowledge space failed, kbId={}, msg={}", kbId, e.getMessage(), e);
            throw new RuntimeException("创建知识库存储空间失败");
        }
    }

    @Override
    public UploadResult upload(MultipartFile file, String kbId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        if (StrUtil.isBlank(kbId)) {
            throw new IllegalArgumentException("kbId 不能为空");
        }
        String bucket = buildKbBucketName(kbId.trim());
        verifyBucketExists(bucket);
        String originalName = StrUtil.blankToDefault(file.getOriginalFilename(), "unknown.bin");
        String ext = "";
        int idx = originalName.lastIndexOf('.');
        if (idx >= 0 && idx < originalName.length() - 1) {
            ext = originalName.substring(idx);
        }
        String objectKey = IdUtil.getSnowflakeNextIdStr() + ext;
        try (InputStream in = file.getInputStream()) {
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

    @Override
    public InputStream getObjectStream(String bucket, String objectKey) {
        if (StrUtil.isBlank(bucket)) {
            throw new IllegalArgumentException("bucket 不能为空");
        }
        if (StrUtil.isBlank(objectKey)) {
            throw new IllegalArgumentException("objectKey 不能为空");
        }
        try {
            return rustfsMinioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );
        } catch (Exception e) {
            log.error("RustFS get object failed, objectKey={}, msg={}", objectKey, e.getMessage(), e);
            throw new RuntimeException("拉取 RustFS 文档失败");
        }
    }

    @Override
    public void deleteObject(String bucket, String objectKey) {
        if (StrUtil.isBlank(bucket)) {
            throw new IllegalArgumentException("bucket 不能为空");
        }
        if (StrUtil.isBlank(objectKey)) {
            return;
        }
        try {
            rustfsMinioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );
        } catch (Exception e) {
            log.error("RustFS delete object failed, objectKey={}, msg={}", objectKey, e.getMessage(), e);
            throw new RuntimeException("删除 RustFS 文档失败");
        }
    }

    private void ensureBucketExists(String bucket) throws Exception {
        boolean exists = rustfsMinioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            rustfsMinioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    private void verifyBucketExists(String bucket) {
        try {
            boolean exists = rustfsMinioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                throw new IllegalArgumentException("知识库存储空间不存在，请先创建知识库");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("RustFS bucket exists check failed, bucket={}, msg={}", bucket, e.getMessage(), e);
            throw new RuntimeException("校验知识库存储空间失败");
        }
    }

    private String buildKbBucketName(String kbId) {
        String baseBucket = rustfsProperties.getBucket();
        if (StrUtil.isBlank(baseBucket)) {
            throw new IllegalStateException("rustfs.bucket 未配置");
        }
        return (baseBucket.trim() + "-" + kbId).toLowerCase();
    }
}
