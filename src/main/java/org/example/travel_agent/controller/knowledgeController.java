package org.example.travel_agent.controller;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import org.example.travel_agent.dto.Result;
import org.example.travel_agent.dto.knowledge.CreateKnowledgeBaseRequest;
import org.example.travel_agent.dto.knowledge.CreateKnowledgeBaseResponse;
import org.example.travel_agent.service.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class knowledgeController {

    private final ObjectStorageService objectStorageService;

    @PostMapping("/createKb")
    public Result<CreateKnowledgeBaseResponse> createKnowledgeBase(@RequestBody CreateKnowledgeBaseRequest request) {
        if (request == null || StrUtil.isBlank(request.getKbName())) {
            return Result.fail("知识库名称不能为空");
        }
        String kbId = IdUtil.getSnowflakeNextIdStr();
        objectStorageService.createKnowledgeSpace(kbId);
        CreateKnowledgeBaseResponse response = new CreateKnowledgeBaseResponse(
                kbId,
                request.getKbName().trim(),
                StrUtil.nullToEmpty(request.getDescription()).trim()
        );
        return Result.success("知识库创建成功", response);
    }

    @PostMapping("/uploadDoc")
    public Result<ObjectStorageService.UploadResult> uploadDoc(@RequestParam("file") MultipartFile file,
                                                               @RequestParam("kbId") String kbId) {
        ObjectStorageService.UploadResult result = objectStorageService.upload(file, kbId);
        return Result.success("上传成功", result);
    }
}
