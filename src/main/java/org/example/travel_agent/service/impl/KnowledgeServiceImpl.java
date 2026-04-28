package org.example.travel_agent.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.exception.TikaException;
import org.example.travel_agent.Exceptions.ClientException;
import org.example.travel_agent.constant.DocumentStatusEnum;
import org.example.travel_agent.dao.entity.KbDocumentEntity;
import org.example.travel_agent.dao.entity.KnowledgeBaseEntity;
import org.example.travel_agent.dao.mapper.KbChunkMapper;
import org.example.travel_agent.dao.mapper.KbDocumentMapper;
import org.example.travel_agent.dao.mapper.KnowledgeBaseMapper;
import org.example.travel_agent.dto.knowledge.ChuckRequest;
import org.example.travel_agent.dto.knowledge.CreateKnowledgeBaseRequest;
import org.example.travel_agent.dto.knowledge.ParseDocumentRequest;
import org.example.travel_agent.knowledge.chunk.ChunkStrategy;
import org.example.travel_agent.knowledge.parser.TikaDocumentParser;
import org.example.travel_agent.service.KnowledgeService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
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

    private final MinioClient rustfsMinioClient;


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
        //再导入pgsql存储辕信息
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
    public void uploadDoc(MultipartFile file, long kbId) throws ClientException {
        if (kbId==0){
            throw new ClientException("请选择一个想要存入的知识库");
        }
        KnowledgeBaseEntity knowledgeBaseEntity = knowledgeBaseMapper.selectById(kbId);

        if (knowledgeBaseEntity==null){
            throw new ClientException("没有该知识库");
        }
        String kbName = knowledgeBaseEntity.getKbName();
        rustfsObjectStorageService.upload(file,kbName);
        String objectKey = getObjectKey(Objects.requireNonNull(file.getOriginalFilename()));
        kbDocumentMapper.insert(
                KbDocumentEntity.builder()
                        .bucket(kbName)
                        .kbId(kbId)
                        .fileName(file.getName())
                        .updatedAt(OffsetDateTime.now())
                        .objectKey(objectKey)
                        .createdAt(OffsetDateTime.now())
                        .status(DocumentStatusEnum.INIT)
                        .build()
        );
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
    public void parseDoc(ParseDocumentRequest request) throws TikaException, IOException, ClientException, ServerException, InsufficientDataException, ErrorResponseException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        long docId = request.getDocId();
        KbDocumentEntity kbDocumentEntity = kbDocumentMapper.selectById(docId);
        if (kbDocumentEntity == null) {
            throw new ClientException("文档不存在");
        }
        String bucket = kbDocumentEntity.getBucket();
        String objectKey = kbDocumentEntity.getObjectKey();
        String fileName = kbDocumentEntity.getFileName();

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
        String parsedObjectKey = objectKey + ".parsed.txt";
        byte[] parsedBytes = (parsedText == null ? "" : parsedText).getBytes(StandardCharsets.UTF_8);
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

        kbDocumentMapper.update(
                null,
                Wrappers.lambdaUpdate(KbDocumentEntity.class)
                        .eq(KbDocumentEntity::getId, id)
                        .set(KbDocumentEntity::getStatus, DocumentStatusEnum.PARSED)
                        .set(KbDocumentEntity::getUpdatedAt, OffsetDateTime.now())
        );
    }

    @Override
    public void chuckDoc(ChuckRequest request) throws IOException {
        int chuckStrategy = request.getChuckStrategy();
        long docId = request.getDoc_id();
        KbDocumentEntity kbDocumentEntity = kbDocumentMapper.selectById(docId);
        String bucket = kbDocumentEntity.getBucket();
        String objectKey = kbDocumentEntity.getObjectKey();
        InputStream is = rustfsObjectStorageService.getObjectStream(bucket, objectKey);
        StringBuilder sb=new StringBuilder();
        char[] buffer=new char[1024*1024];
        try(InputStreamReader isr = new InputStreamReader(is)){
            int n;
            while((n=isr.read(buffer))!=-1){
                sb.append(buffer,0,n);
            }
        }
        List<String> splits = fixedTokenChunkStrategy.split(sb.toString());

        for (String split :splits){
            kbChunkMapper.insert(
                    Wrappers.lambdaUpdate(KbDocumentEntity.class)
                            .eq()
            )
        }

    }


}
