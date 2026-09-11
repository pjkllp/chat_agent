package org.example.travel_agent.controller;

import lombok.RequiredArgsConstructor;
import org.example.travel_agent.dto.ChatAttachmentDTO;
import org.example.travel_agent.dto.Result;
import org.example.travel_agent.service.ChatAttachmentService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RequestMapping("/api/chat/attachment")
@RestController
@RequiredArgsConstructor
public class ChatAttachmentController {

    private final ChatAttachmentService chatAttachmentService;

    @PostMapping("/upload")
    public Result<ChatAttachmentDTO> upload(@RequestParam("file") MultipartFile file,
                                            @RequestParam("conversationId") String conversationId) {
        ChatAttachmentDTO dto = chatAttachmentService.upload(file, conversationId);
        return Result.success("上传成功", dto);
    }
}
