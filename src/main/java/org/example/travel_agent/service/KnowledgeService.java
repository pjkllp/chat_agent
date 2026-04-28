package org.example.travel_agent.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.minio.errors.*;
import org.apache.tika.exception.TikaException;
import org.example.travel_agent.Exceptions.ClientException;
import org.example.travel_agent.dto.knowledge.ChuckRequest;
import org.example.travel_agent.dto.knowledge.CreateKnowledgeBaseRequest;
import org.example.travel_agent.dto.knowledge.KbChunkPageRequest;
import org.example.travel_agent.dto.knowledge.KbChunkPageResponse;
import org.example.travel_agent.dto.knowledge.KbDocumentPageRequest;
import org.example.travel_agent.dto.knowledge.KbDocumentPageResponse;
import org.example.travel_agent.dto.knowledge.KnowledgeBasePageRequest;
import org.example.travel_agent.dto.knowledge.KnowledgeBasePageResponse;
import org.example.travel_agent.dto.knowledge.ParseDocumentRequest;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public interface KnowledgeService {
    void createKb(CreateKnowledgeBaseRequest request) throws ClientException;

    void uploadDoc(MultipartFile file, long kbId) throws ClientException;

    void parseDoc(ParseDocumentRequest request) throws TikaException, IOException, ClientException, ServerException, InsufficientDataException, ErrorResponseException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException;

    void chuckDoc(ChuckRequest request) throws IOException, ClientException;

    Page<KnowledgeBasePageResponse> kbPage(KnowledgeBasePageRequest request);

    Page<KbDocumentPageResponse> docPage(KbDocumentPageRequest request);

    Page<KbChunkPageResponse> chunkPage(KbChunkPageRequest request);
}
