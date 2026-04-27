package org.example.travel_agent.service.impl;

import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import org.example.travel_agent.Exceptions.ClientException;
import org.example.travel_agent.dao.entity.KnowledgeBaseEntity;
import org.example.travel_agent.dao.mapper.KnowledgeBaseMapper;
import org.example.travel_agent.dto.knowledge.CreateKnowledgeBaseRequest;
import org.example.travel_agent.knowledge.chunk.ChunkStrategy;
import org.example.travel_agent.knowledge.parser.TikaDocumentParser;
import org.example.travel_agent.service.KnowledgeService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl implements KnowledgeService {

    private final RustfsObjectStorageService rustfsObjectStorageService;

    private final TikaDocumentParser tikaDocumentParser;

    private final ChunkStrategy fixedTokenChunkStrategy;

    private final KnowledgeBaseMapper knowledgeBaseMapper;


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
    public void uploadDoc(MultipartFile file, String kbId) throws ClientException {
        if (kbId==null||kbId.isBlank()){
            throw new ClientException("请选择一个想要存入的知识库");
        }
        rustfsObjectStorageService.upload(file,kbId);
    }


}
