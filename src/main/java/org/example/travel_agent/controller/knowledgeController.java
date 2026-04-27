package org.example.travel_agent.controller;

import org.example.travel_agent.dto.Result;
import org.example.travel_agent.service.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class knowledgeController {

    private final ObjectStorageService objectStorageService;

    @PostMapping("/uploadDoc")
    public Result<ObjectStorageService.UploadResult> uploadDoc(@RequestParam("file") MultipartFile file,
                                                               @RequestParam(value = "dir", required = false) String dir) {
        ObjectStorageService.UploadResult result = objectStorageService.upload(file, dir);
        return Result.success("上传成功", result);
    }
}
