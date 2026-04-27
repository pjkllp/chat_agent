package org.example.travel_agent.service;

import org.example.travel_agent.Exceptions.ClientException;
import org.example.travel_agent.dto.knowledge.CreateKnowledgeBaseRequest;
import org.springframework.web.multipart.MultipartFile;

public interface KnowledgeService {
    void createKb(CreateKnowledgeBaseRequest request) throws ClientException;

    void uploadDoc(MultipartFile file, String kbId) throws ClientException;
}
