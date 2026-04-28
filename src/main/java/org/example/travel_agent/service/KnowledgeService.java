package org.example.travel_agent.service;

import io.minio.errors.*;
import org.apache.tika.exception.TikaException;
import org.example.travel_agent.Exceptions.ClientException;
import org.example.travel_agent.dto.knowledge.ChuckRequest;
import org.example.travel_agent.dto.knowledge.CreateKnowledgeBaseRequest;
import org.example.travel_agent.dto.knowledge.ParseDocumentRequest;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public interface KnowledgeService {
    void createKb(CreateKnowledgeBaseRequest request) throws ClientException;

    void uploadDoc(MultipartFile file, long kbId) throws ClientException;

    void parseDoc(ParseDocumentRequest request) throws TikaException, IOException, ClientException, ServerException, InsufficientDataException, ErrorResponseException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException;

    void chuckDoc(ChuckRequest request) throws IOException;
}
