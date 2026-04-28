package org.example.travel_agent.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.minio.errors.*;
import lombok.SneakyThrows;
import org.apache.tika.exception.TikaException;
import org.example.travel_agent.Exceptions.ClientException;
import org.example.travel_agent.dao.entity.KbDocumentEntity;
import org.example.travel_agent.dto.Result;
import org.example.travel_agent.dto.knowledge.ChuckRequest;
import org.example.travel_agent.dto.knowledge.CreateKnowledgeBaseRequest;
import org.example.travel_agent.dto.knowledge.KbChunkPageRequest;
import org.example.travel_agent.dto.knowledge.KbChunkPageResponse;
import org.example.travel_agent.dto.knowledge.KbDocumentPageRequest;
import org.example.travel_agent.dto.knowledge.KbDocumentPageResponse;
import org.example.travel_agent.dto.knowledge.KnowledgeBasePageRequest;
import org.example.travel_agent.dto.knowledge.KnowledgeBasePageResponse;
import org.example.travel_agent.dto.knowledge.ParseDocumentRequest;
import org.example.travel_agent.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class knowledgeController {

    private final KnowledgeService knowledgeService;

    private final Executor knowledgeExecutor;

    /**
     * 创建知识库
     * @param request
     * @return
     * @throws ClientException
     */
    @PostMapping("/createKb")
    public Result<Void> createKnowledgeBase(@RequestBody CreateKnowledgeBaseRequest request) throws ClientException {
        knowledgeService.createKb(request);
        return Result.success("知识库创建成功");
    }

    /**
     * 将文档上传到知识库
     * @param file
     * @param kbId
     * @return
     * @throws ClientException
     */
    @PostMapping("/uploadDoc")
    public Result<Void> uploadDoc(@RequestParam("file") MultipartFile file,
                                  @RequestParam("kbId") long kbId) throws ClientException {
        knowledgeService.uploadDoc(file,kbId);
        return Result.success("上传中，请稍后刷新!");
    }

    /**
     * 解析文档
     * @param request
     * @return
     * @throws TikaException
     * @throws IOException
     * @throws ClientException
     * @throws ServerException
     * @throws InsufficientDataException
     * @throws ErrorResponseException
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException
     * @throws InvalidResponseException
     * @throws XmlParserException
     * @throws InternalException
     */
    @PostMapping("/parseDoc")
    public Result<Void> parseDoc(@RequestBody ParseDocumentRequest request) throws TikaException, IOException, ClientException, ServerException, InsufficientDataException, ErrorResponseException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        knowledgeService.parseDoc(request);
        return Result.success("解析中，请稍后刷新");
    }

    /**
     * 将文档分块
     * @param request
     * @return
     */
    @SneakyThrows
    @PostMapping("/chuckDoc")
    public Result<Void> chuckDoc(@RequestBody ChuckRequest request){
        knowledgeService.chuckDoc(request);
        return Result.success("分块中，请稍后刷新!");
    }

    @GetMapping("/kb/page")
    public Result<Page<KnowledgeBasePageResponse>> kb_page(KnowledgeBasePageRequest request){
        Page<KnowledgeBasePageResponse> page = knowledgeService.kbPage(request);
        return Result.success("查询成功", page);
    }

    @GetMapping("/doc/page")
    public Result<Page<KbDocumentPageResponse>> doc_page(KbDocumentPageRequest request){
        Page<KbDocumentPageResponse> page = knowledgeService.docPage(request);
        return Result.success("查询成功", page);
    }

    @GetMapping("/chunk/page")
    public Result<Page<KbChunkPageResponse>> chunk_page(KbChunkPageRequest request){
        Page<KbChunkPageResponse> page = knowledgeService.chunkPage(request);
        return Result.success("查询成功", page);
    }

}
