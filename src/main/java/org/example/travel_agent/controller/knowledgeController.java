package org.example.travel_agent.controller;

import io.minio.errors.*;
import org.apache.tika.exception.TikaException;
import org.example.travel_agent.Exceptions.ClientException;
import org.example.travel_agent.dto.Result;
import org.example.travel_agent.dto.knowledge.CreateKnowledgeBaseRequest;
import org.example.travel_agent.dto.knowledge.ParseDocumentRequest;
import org.example.travel_agent.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

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
                                  @RequestParam("kbId") long kbId) throws ClientException {
        knowledgeService.uploadDoc(file,kbId);
        return Result.success("上传成功");
    }

    @PostMapping("/parseDoc")
    public Result<Void> parseDoc(@RequestBody ParseDocumentRequest request) throws TikaException, IOException, ClientException, ServerException, InsufficientDataException, ErrorResponseException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        knowledgeService.parseDoc(request);
        return Result.success("解析文档成功");
    }
}
