package org.example.travel_agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatRequest {

    private String question;

    private String conversationId;

    private int isDeepThink;

    private List<ChatAttachmentDTO> attachments;

}
