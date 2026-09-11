package org.example.travel_agent.service;

import org.example.travel_agent.dto.ChatAttachmentDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatAttachmentSupportTest {

    private ChatAttachmentSupport support(String publicHost) {
        return new ChatAttachmentSupport(null, publicHost, 4);
    }

    private ChatAttachmentDTO img(String url) {
        return ChatAttachmentDTO.builder().url(url).type(ChatAttachmentDTO.TYPE_IMAGE)
                .mimeType("image/jpeg").build();
    }

    @Test
    void acceptsCountWithinLimit() {
        var s = support("https://b.oss.example.com");
        s.validate(List.of(
                img("https://b.oss.example.com/chat/c/1.jpg"),
                img("https://b.oss.example.com/chat/c/2.jpg")));
    }

    @Test
    void rejectsTooManyAttachments() {
        var s = support("https://b.oss.example.com");
        var list = List.of(
                img("https://b.oss.example.com/1.jpg"),
                img("https://b.oss.example.com/2.jpg"),
                img("https://b.oss.example.com/3.jpg"),
                img("https://b.oss.example.com/4.jpg"),
                img("https://b.oss.example.com/5.jpg"));
        assertThatThrownBy(() -> s.validate(list))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("最多");
    }

    @Test
    void rejectsForeignHost() {
        var s = support("https://b.oss.example.com");
        assertThatThrownBy(() -> s.validate(List.of(img("https://evil.example.com/x.jpg"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hasImageDetects() {
        var s = support("https://b.oss.example.com");
        assertThat(s.hasImage(List.of(img("https://b.oss.example.com/x.jpg")))).isTrue();
    }

    @Test
    void emptyAttachmentsPassThrough() {
        var s = support("https://b.oss.example.com");
        s.validate(null);
        assertThat(s.hasImage(null)).isFalse();
    }
}
