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
    public void createKnowledgeSpace(String kbName) {
        if (kbName == null||kbName.isBlank()) {
            throw new IllegalArgumentException("kbId 不能为空");
        }
        try {
            ensureBucketExists(kbName);
        } catch (Exception e) {
            log.error("RustFS create knowledge space failed,  msg={}", e.getMessage(), e);
            throw new RuntimeException("创建知识库存储空间失败");
        }
    }

    @Override
    public void upload(MultipartFile file, String kbName) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        if (kbName == null||kbName.isBlank()) {
            throw new IllegalArgumentException("kbId 不能为空");
        }
        verifyBucketExists(kbName);
        String objectKey = KnowledgeServiceImpl.getObjectKey(StrUtil.blankToDefault(file.getOriginalFilename(), "unknown.bin"));
        try (InputStream in = file.getInputStream()) {
            rustfsMinioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(kbName)
                            .object(objectKey)
                            .stream(in, file.getSize(), -1)
                            .contentType(StrUtil.blankToDefault(file.getContentType(), "application/octet-stream"))
                            .build()
            );
        } catch (Exception e) {
            log.error("RustFS upload failed, file={}, msg={}", file.getOriginalFilename(), e.getMessage(), e);
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

}
