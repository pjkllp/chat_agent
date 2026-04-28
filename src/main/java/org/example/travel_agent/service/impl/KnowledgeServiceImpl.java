package org.example.travel_agent.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.exception.TikaException;
import org.example.travel_agent.Exceptions.ClientException;
import org.example.travel_agent.constant.DocumentStatusEnum;
import org.example.travel_agent.dao.entity.KbChunkEntity;
import org.example.travel_agent.dao.entity.KbDocumentEntity;
import org.example.travel_agent.dao.entity.KbVectorEntity;
import org.example.travel_agent.dao.entity.KnowledgeBaseEntity;
import org.example.travel_agent.dao.mapper.KbChunkMapper;
import org.example.travel_agent.dao.mapper.KbDocumentMapper;
import org.example.travel_agent.dao.mapper.KbVectorMapper;
import org.example.travel_agent.dao.mapper.KnowledgeBaseMapper;
import org.example.travel_agent.dto.knowledge.*;
import org.example.travel_agent.knowledge.chunk.ChunkStrategy;
import org.example.travel_agent.knowledge.parser.TikaDocumentParser;
import org.example.travel_agent.service.KnowledgeService;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.sql.Wrapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.apache.tika.Tika;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl implements KnowledgeService {

    private final RustfsObjectStorageService rustfsObjectStorageService;

    private final TikaDocumentParser tikaDocumentParser;

    private final ChunkStrategy fixedTokenChunkStrategy;

    private final KnowledgeBaseMapper knowledgeBaseMapper;

    private final KbDocumentMapper kbDocumentMapper;

    private final KbChunkMapper kbChunkMapper;

    private final KbVectorMapper kbVectorMapper;

    private final MinioClient rustfsMinioClient;

    private final EmbeddingModel embeddingModel;


    @Override
    public void createKb(CreateKnowledgeBaseRequest request) throws ClientException {
        String kbName = request.getKbName();
        String description = request.getDescription();
        if (kbName==null||kbName.isBlank()){
            throw new ClientException("知识库名不能为空");
        }
        long id = IdUtil.getSnowflakeNextId();
        //先创建rustfs的知识库
        rustfsObjectStorageService.createKnowledgeSpace(kbName);
        //再导入pgsql存储信息
        knowledgeBaseMapper.insert(
                KnowledgeBaseEntity.builder()
                        .createdAt(OffsetDateTime.now())
                        .updatedAt(OffsetDateTime.now())
                        .description(description)
                        .kbName(kbName)
                        .enabled(true)
                        .build()
        );
    }

    @Override
    @Async(value = "knowledgeExecutor")
    public void uploadDoc(MultipartFile file, String kbId) throws ClientException {
        Long kbIdLong = parseKbId(kbId);
        if (kbIdLong == null){
            throw new ClientException("请选择一个想要存入的知识库");
        }
        KnowledgeBaseEntity knowledgeBaseEntity = knowledgeBaseMapper.selectById(kbIdLong);

        if (knowledgeBaseEntity==null){
            throw new ClientException("没有该知识库");
        }
        String kbName = knowledgeBaseEntity.getKbName();
        String objectKey = rustfsObjectStorageService.upload(file,kbName);
        try {
            kbDocumentMapper.insert(
                    KbDocumentEntity.builder()
                            .bucket(kbName)
                            .kbId(kbIdLong)
                            .fileName(file.getOriginalFilename())
                            .updatedAt(OffsetDateTime.now())
                            .objectKey(objectKey)
                            .createdAt(OffsetDateTime.now())
                            .status(DocumentStatusEnum.INIT.getValue())
                            .build()
            );
        } catch (DuplicateKeyException e) {
            throw new ClientException("数据库中已经有数据");
        }
    }

    public static String getObjectKey(String originalName){
        String ext = "";
        int idx = originalName.lastIndexOf('.');
        if (idx >= 0 && idx < originalName.length() - 1) {
            ext = originalName.substring(idx);
        }
        return IdUtil.getSnowflakeNextIdStr()+ext;
    }

    //这里先将文件数据拉入内存中，然后用多个流消费，很耗内存，之后得优化
    @Override
    @Async(value = "knowledgeExecutor")
    public void parseDoc(ParseDocumentRequest request) throws TikaException, IOException, ClientException, ServerException, InsufficientDataException, ErrorResponseException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        long docId = request.getDocId();
        KbDocumentEntity kbDocumentEntity = kbDocumentMapper.selectById(docId);
        if (kbDocumentEntity == null) {
            throw new ClientException("文档不存在");
        }
        validateNextStatus(kbDocumentEntity.getStatus(), DocumentStatusEnum.PARSED, "解析");
        String bucket = kbDocumentEntity.getBucket();
        String objectKey = kbDocumentEntity.getObjectKey();
        String fileName = kbDocumentEntity.getFileName();

        try {
            // 只读一次 RustFS，避免「解析消费流 + put 再读」两次网络 IO 且流不可复用
            final byte[] raw;
            try (InputStream in = rustfsObjectStorageService.getObjectStream(bucket, objectKey)) {
                raw = in.readAllBytes();
            }
            if (raw.length == 0) {
                throw new ClientException("对象为空或无法读取");
            }

            // 无 MultipartFile 时：用 Tika 根据「文件名 + 魔数」推断原始 MIME，仅作元数据/回传用
            String originalMime = StrUtil.blankToDefault(new Tika().detect(raw, fileName), "application/octet-stream");

            String parsedText = tikaDocumentParser.parse(fileName, new ByteArrayInputStream(raw));
            log.info("{} 解析完成，原始 MIME={}，文本长度={}", fileName, originalMime, parsedText == null ? 0 : parsedText.length());

            // 解析结果单独对象键，避免用纯文本覆盖原始二进制 objectKey
            String parsedObjectKey = getParsedObjectKey(objectKey);
            String parsedFileName = getParsedFileName(fileName);

            byte[] parsedBytes = (parsedText == null ? "" : parsedText).getBytes(StandardCharsets.UTF_8);
            //将解析的文本放入rustfs
            try (ByteArrayInputStream uploadIn = new ByteArrayInputStream(parsedBytes)) {
                rustfsMinioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucket)
                                .object(parsedObjectKey)
                                .stream(uploadIn, parsedBytes.length, -1)
                                .contentType("text/plain; charset=utf-8")
                                .build()
                );
            }
            //直接修改数据库中原来的文档，将其状态变成已解析
            //以后要是想拿到原文件就得拼接一下fileName
            kbDocumentMapper.update(
                    Wrappers.lambdaUpdate(KbDocumentEntity.class)
                            .eq(KbDocumentEntity::getId,docId)
                            .set(true,KbDocumentEntity::getStatus,DocumentStatusEnum.PARSED.getValue())
                            .set(true,KbDocumentEntity::getUpdatedAt,OffsetDateTime.now())
                            .set(true,KbDocumentEntity::getErrorMessage, null)
                            .set(true,KbDocumentEntity::getFileName,parsedFileName)
                            .set(true,KbDocumentEntity::getObjectKey,parsedObjectKey)
            );
        } catch (Exception e) {
            if (e instanceof ClientException ce) {
                throw ce;
            }
            markDocFailed(docId, e);
            throw e;
        }
    }

    public static String getParsedObjectKey(String objectKey){
        return objectKey + ".parsed.txt";
    }

    public static String getParsedFileName(String fileName){
        return fileName+".parsed.txt";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Async(value = "knowledgeExecutor")
    public void chuckDoc(ChuckRequest request) throws IOException, ClientException {
        int chuckStrategy = request.getChuckStrategy();
        long docId = request.getDoc_id();
        Long kbId = parseKbId(request.getKbId());
        if (kbId == null) {
            throw new ClientException("kbId不能为空");
        }
        KbDocumentEntity kbDocumentEntity = kbDocumentMapper.selectById(docId);

        if (kbDocumentEntity==null){
            throw new ClientException("文档不存在");
        }
        if (!Objects.equals(kbDocumentEntity.getKbId(), kbId)) {
            throw new ClientException("文档不属于当前知识库");
        }
        validateNextStatus(kbDocumentEntity.getStatus(), DocumentStatusEnum.CHUNKING, "分块");

        kbDocumentMapper.update(
                Wrappers.lambdaUpdate(KbDocumentEntity.class)
                        .eq(KbDocumentEntity::getId,docId)
                        .set(true,KbDocumentEntity::getStatus,DocumentStatusEnum.CHUNKING.getValue())
                        .set(true,KbDocumentEntity::getUpdatedAt,OffsetDateTime.now())
        );

        String bucket = kbDocumentEntity.getBucket();
        String objectKey = kbDocumentEntity.getObjectKey();
        InputStream is = rustfsObjectStorageService.getObjectStream(bucket, objectKey);
        StringBuilder sb=new StringBuilder();
        char[] buffer=new char[1024*8];
        try(InputStreamReader isr = new InputStreamReader(is)){
            int n;
            while((n=isr.read(buffer))!=-1){
                sb.append(buffer,0,n);
            }
        }
        List<String> splits = fixedTokenChunkStrategy.split(sb.toString());
        int i=0;

        kbChunkMapper.delete(
                Wrappers.lambdaUpdate(KbChunkEntity.class)
                        .eq(KbChunkEntity::getDocId,docId)
        );

        for (String split :splits){
            kbChunkMapper.insert(
                    KbChunkEntity.builder()
                            .docId(docId)
                            .kbId(kbId)
                            .content(split)
                            .createdAt(OffsetDateTime.now())
                            .updatedAt(OffsetDateTime.now())
                            .chunkNo(i++)
                            .build()
            );
        }

        kbDocumentMapper.update(
                Wrappers.lambdaUpdate(KbDocumentEntity.class)
                        .eq(KbDocumentEntity::getId,docId)
                        .set(true,KbDocumentEntity::getStatus,DocumentStatusEnum.CHUNKED.getValue())
                        .set(true,KbDocumentEntity::getUpdatedAt,OffsetDateTime.now())
        );

    }

    @Override
    @Async("knowledgeExecutor")
    public void chuckEmbedding(ChuckEmbeddingRequest request) throws ClientException {
        Long kbId = parseKbId(request.getKbId());
        Long docId = parseKbId(request.getDocId());
        if (kbId == null || docId == null) {
            throw new ClientException("请填写完整参数");
        }
        KbDocumentEntity doc = kbDocumentMapper.selectById(docId);
        if (doc == null) {
            throw new ClientException("文档不存在");
        }
        if (!Objects.equals(doc.getKbId(), kbId)) {
            throw new ClientException("文档不属于当前知识库");
        }
        validateNextStatus(doc.getStatus(), DocumentStatusEnum.EMBEDDING, "向量化");
        kbDocumentMapper.update(
                Wrappers.lambdaUpdate(KbDocumentEntity.class)
                        .eq(KbDocumentEntity::getId, docId)
                        .set(true, KbDocumentEntity::getStatus, DocumentStatusEnum.EMBEDDING.getValue())
                        .set(true, KbDocumentEntity::getUpdatedAt, OffsetDateTime.now())
        );

        List<KbChunkEntity> kbChunkEntities = kbChunkMapper.selectList(
                Wrappers.lambdaQuery(KbChunkEntity.class)
                        .eq(KbChunkEntity::getKbId, kbId)
                        .eq(KbChunkEntity::getDocId, docId)
        );
        if (kbChunkEntities == null || kbChunkEntities.isEmpty()) {
            throw new ClientException("当前文档没有可向量化的分块");
        }
        List<String> list = kbChunkEntities.parallelStream().map(KbChunkEntity::getContent).toList();
        List<float[]> embeds = embedInBatches(list, 10);
        if (embeds == null || embeds.size() != kbChunkEntities.size()) {
            throw new ClientException("向量化结果数量异常");
        }

        kbVectorMapper.delete(
                Wrappers.lambdaQuery(KbVectorEntity.class)
                        .eq(KbVectorEntity::getKbId, kbId)
                        .eq(KbVectorEntity::getDocId, docId)
        );

        for (int i = 0; i < kbChunkEntities.size(); i++) {
            KbChunkEntity chunk = kbChunkEntities.get(i);
            float[] embedding = embeds.get(i);
            kbVectorMapper.insert(
                    KbVectorEntity.builder()
                            .kbId(kbId)
                            .docId(docId)
                            .chunkId(chunk.getId())
                            .content(chunk.getContent())
                            .embedding(embedding)
                            .enabled(true)
                            .createdAt(OffsetDateTime.now())
                            .updatedAt(OffsetDateTime.now())
                            .build()
            );
        }

        kbDocumentMapper.update(
                Wrappers.lambdaUpdate(KbDocumentEntity.class)
                        .eq(KbDocumentEntity::getId, docId)
                        .set(true, KbDocumentEntity::getStatus, DocumentStatusEnum.VECTORIZED.getValue())
                        .set(true, KbDocumentEntity::getUpdatedAt, OffsetDateTime.now())
                        .set(true, KbDocumentEntity::getErrorMessage, null)
        );

    }

    @Override
    public Page<KnowledgeBasePageResponse> kbPage(KnowledgeBasePageRequest request) {
        if (request == null) {
            request = new KnowledgeBasePageRequest();
        }
        long current = request.getCurrent() <= 0 ? 1 : request.getCurrent();
        long size = request.getSize() <= 0 ? 10 : request.getSize();

        Page<KnowledgeBaseEntity> pageReq = new Page<>(current, size);
        Page<KnowledgeBaseEntity> entityPage = knowledgeBaseMapper.selectPage(
                pageReq,
                Wrappers.lambdaQuery(KnowledgeBaseEntity.class)
                        .like(StrUtil.isNotBlank(request.getKbName()), KnowledgeBaseEntity::getKbName, request.getKbName())
                        .eq(request.getEnabled() != null, KnowledgeBaseEntity::getEnabled, request.getEnabled())
                        .orderByDesc(KnowledgeBaseEntity::getUpdatedAt)
                        .orderByDesc(KnowledgeBaseEntity::getId)
        );
        return (Page<KnowledgeBasePageResponse>) entityPage.convert(this::toKbPageResp);
    }

    @Override
    public Page<KbDocumentPageResponse> docPage(KbDocumentPageRequest request) {
        if (request == null) {
            request = new KbDocumentPageRequest();
        }
        long current = request.getCurrent() <= 0 ? 1 : request.getCurrent();
        long size = request.getSize() <= 0 ? 10 : request.getSize();

        Long kbId = parseKbId(request.getKbId());
        Page<KbDocumentEntity> pageReq = new Page<>(current, size);
        Page<KbDocumentEntity> entityPage = kbDocumentMapper.selectPage(
                pageReq,
                Wrappers.lambdaQuery(KbDocumentEntity.class)
                        .eq(kbId != null, KbDocumentEntity::getKbId, kbId)
                        .eq(request.getStatus() != null, KbDocumentEntity::getStatus, request.getStatus())
                        .like(StrUtil.isNotBlank(request.getFileName()), KbDocumentEntity::getFileName, request.getFileName())
                        .orderByDesc(KbDocumentEntity::getUpdatedAt)
                        .orderByDesc(KbDocumentEntity::getId)
        );

        return (Page<KbDocumentPageResponse>) entityPage.convert(this::toKbDocPageResp);
    }

    @Override
    public Page<KbChunkPageResponse> chunkPage(KbChunkPageRequest request) {
        if (request == null) {
            request = new KbChunkPageRequest();
        }
        long current = request.getCurrent() <= 0 ? 1 : request.getCurrent();
        long size = request.getSize() <= 0 ? 10 : request.getSize();

        Long kbId = parseKbId(request.getKbId());
        Page<KbChunkEntity> pageReq = new Page<>(current, size);
        Page<KbChunkEntity> entityPage = kbChunkMapper.selectPage(
                pageReq,
                Wrappers.lambdaQuery(KbChunkEntity.class)
                        .eq(kbId != null, KbChunkEntity::getKbId, kbId)
                        .eq(request.getDocId() != null && request.getDocId() > 0, KbChunkEntity::getDocId, request.getDocId())
                        .eq(request.getEnabled() != null, KbChunkEntity::getEnabled, request.getEnabled())
                        .like(StrUtil.isNotBlank(request.getContent()), KbChunkEntity::getContent, request.getContent())
                        .orderByAsc(KbChunkEntity::getChunkNo)
                        .orderByDesc(KbChunkEntity::getId)
        );

        return (Page<KbChunkPageResponse>) entityPage.convert(this::toKbChunkPageResp);
    }

    private KnowledgeBasePageResponse toKbPageResp(KnowledgeBaseEntity entity) {
        return KnowledgeBasePageResponse.builder()
                .id(entity.getId() == null ? null : String.valueOf(entity.getId()))
                .kbName(entity.getKbName())
                .description(entity.getDescription())
                .enabled(entity.getEnabled())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private KbDocumentPageResponse toKbDocPageResp(KbDocumentEntity entity) {
        return KbDocumentPageResponse.builder()
                .id(entity.getId() == null ? null : String.valueOf(entity.getId()))
                .kbId(entity.getKbId() == null ? null : String.valueOf(entity.getKbId()))
                .fileName(entity.getFileName())
                .bucket(entity.getBucket())
                .objectKey(entity.getObjectKey())
                .status(entity.getStatus())
                .statusDesc(resolveStatusDesc(entity.getStatus()))
                .enabled(entity.getEnabled())
                .version(entity.getVersion())
                .errorMessage(entity.getErrorMessage())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private KbChunkPageResponse toKbChunkPageResp(KbChunkEntity entity) {
        return KbChunkPageResponse.builder()
                .id(entity.getId() == null ? null : String.valueOf(entity.getId()))
                .kbId(entity.getKbId() == null ? null : String.valueOf(entity.getKbId()))
                .docId(entity.getDocId() == null ? null : String.valueOf(entity.getDocId()))
                .chunkNo(entity.getChunkNo())
                .content(entity.getContent())
                .enabled(entity.getEnabled())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private String resolveStatusDesc(Integer status) {
        if (status == null) {
            return "未知";
        }
        for (DocumentStatusEnum value : DocumentStatusEnum.values()) {
            if (Objects.equals(value.getValue(), status)) {
                return value.getDesc();
            }
        }
        return "未知";
    }

    private void validateNextStatus(Integer currentStatus, DocumentStatusEnum targetStatus, String actionName) throws ClientException {
        if (currentStatus == null) {
            throw new ClientException(actionName + "失败：文档状态为空");
        }
        int expected = targetStatus.getValue() - 1;
        if (currentStatus != expected) {
            String currentDesc = resolveStatusDesc(currentStatus);
            throw new ClientException(actionName + "失败：当前状态为[" + currentDesc + "]，仅允许从上一步推进到[" + targetStatus.getDesc() + "]");
        }
    }

    private void markDocFailed(long docId, Exception e) {
        String message = StrUtil.blankToDefault(e == null ? null : e.getMessage(), "解析失败");
        if (message.length() > 512) {
            message = message.substring(0, 512);
        }
        kbDocumentMapper.update(
                Wrappers.lambdaUpdate(KbDocumentEntity.class)
                        .eq(KbDocumentEntity::getId, docId)
                        .set(true, KbDocumentEntity::getStatus, DocumentStatusEnum.FAILED.getValue())
                        .set(true, KbDocumentEntity::getErrorMessage, message)
                        .set(true, KbDocumentEntity::getUpdatedAt, OffsetDateTime.now())
        );
    }

    private List<float[]> embedInBatches(List<String> inputs, int maxBatchSize) {
        List<float[]> result = new ArrayList<>(inputs.size());
        if (inputs.isEmpty()) {
            return result;
        }
        int batchSize = Math.max(1, maxBatchSize);
        for (int start = 0; start < inputs.size(); start += batchSize) {
            int end = Math.min(start + batchSize, inputs.size());
            List<String> batch = inputs.subList(start, end);
            List<float[]> embeds = embeddingModel.embed(batch);
            if (embeds == null || embeds.size() != batch.size()) {
                throw new RuntimeException("向量化批次结果数量异常");
            }
            result.addAll(embeds);
        }
        return result;
    }

    private Long parseKbId(String kbId) {
        if (StrUtil.isBlank(kbId)) {
            return null;
        }
        String value = kbId.trim();
        if (!value.matches("\\d+")) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }


}
