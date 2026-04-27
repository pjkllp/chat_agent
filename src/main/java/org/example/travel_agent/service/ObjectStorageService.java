package org.example.travel_agent.service;

import org.springframework.web.multipart.MultipartFile;

public interface ObjectStorageService {

    UploadResult upload(MultipartFile file, String dir);

    record UploadResult(String objectKey, String bucket) {
    }
}
