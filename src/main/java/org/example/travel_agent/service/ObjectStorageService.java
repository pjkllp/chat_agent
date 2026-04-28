package org.example.travel_agent.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

public interface ObjectStorageService {

    void createKnowledgeSpace(String kbName);

    String upload(MultipartFile file, String kbName);

    InputStream getObjectStream(String bucket, String objectKey);

    void deleteObject(String bucket, String objectKey);
}
