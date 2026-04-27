package org.example.travel_agent.controller;

import org.example.travel_agent.Exceptions.ClientException;
import org.example.travel_agent.dto.Result;
import org.example.travel_agent.dto.knowledge.CreateKnowledgeBaseRequest;
import org.example.travel_agent.service.KnowledgeService;
import org.example.travel_agent.service.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class knowledgeController {

    private final KnowledgeService knowledgeService;

    @PostMapping("/createKb")
    public Result<Void> createKnowledgeBase(@RequestBody CreateKnowledgeBaseRequest request) throws ClientException {
        knowledgeService.createKb(request);
        return Result.success("知识库创建成功");
    }

    @PostMapping("/uploadDoc")
    public Result<Void> uploadDoc(@RequestParam("file") MultipartFile file,
                                  @RequestParam("kbId") String kbId) throws ClientException {
        knowledgeService.uploadDoc(file,kbId);
        return Result.success("上传成功");
    }

    @PostMapping("/parseDoc")
    public Result<ParseDocumentResponse> parseDoc(@RequestBody ParseDocumentRequest request){
        ParseDocumentResponse response = knowledgeService.parseDoc(request);
        return Result.success("解析文档成功",response);
    }
}
