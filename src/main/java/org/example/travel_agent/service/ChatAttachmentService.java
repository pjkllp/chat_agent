package org.example.travel_agent.service;

import org.example.travel_agent.Exceptions.ClientException;
import org.example.travel_agent.dto.ChatAttachmentDTO;
import org.springframework.web.multipart.MultipartFile;

public interface ChatAttachmentService {

    ChatAttachmentDTO upload(MultipartFile file, String conversationId) throws ClientException;
}
