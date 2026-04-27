package org.example.travel_agent.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

public interface ObjectStorageService {

    KnowledgeSpace createKnowledgeSpace(String kbId);

    UploadResult upload(MultipartFile file, String kbId);

    InputStream getObjectStream(String bucket, String objectKey);

    void deleteObject(String bucket, String objectKey);

    record KnowledgeSpace(String kbId, String bucket) {
    }

    record UploadResult(String objectKey, String bucket) {
    }
}
