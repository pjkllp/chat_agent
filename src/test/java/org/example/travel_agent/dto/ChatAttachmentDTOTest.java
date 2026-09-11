package org.example.travel_agent.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatAttachmentDTOTest {

    @Test
    void resolvesImageMime() {
        assertThat(ChatAttachmentDTO.resolveType("image/jpeg")).isEqualTo(ChatAttachmentDTO.TYPE_IMAGE);
        assertThat(ChatAttachmentDTO.resolveType("image/png")).isEqualTo(ChatAttachmentDTO.TYPE_IMAGE);
        assertThat(ChatAttachmentDTO.resolveType("image/webp")).isEqualTo(ChatAttachmentDTO.TYPE_IMAGE);
        assertThat(ChatAttachmentDTO.resolveType("image/gif")).isEqualTo(ChatAttachmentDTO.TYPE_IMAGE);
    }

    @Test
    void resolvesAudioMime() {
        assertThat(ChatAttachmentDTO.resolveType("audio/mpeg")).isEqualTo(ChatAttachmentDTO.TYPE_AUDIO);
        assertThat(ChatAttachmentDTO.resolveType("audio/wav")).isEqualTo(ChatAttachmentDTO.TYPE_AUDIO);
        assertThat(ChatAttachmentDTO.resolveType("audio/mp4")).isEqualTo(ChatAttachmentDTO.TYPE_AUDIO);
        assertThat(ChatAttachmentDTO.resolveType("audio/webm")).isEqualTo(ChatAttachmentDTO.TYPE_AUDIO);
        assertThat(ChatAttachmentDTO.resolveType("audio/ogg")).isEqualTo(ChatAttachmentDTO.TYPE_AUDIO);
    }

    /** Tika 对 wav/ogg/webm 的内容检测结果与浏览器上报的 MIME 不同，必须同样放行。 */
    @Test
    void resolvesAudioMimeDetectedByTika() {
        assertThat(ChatAttachmentDTO.resolveType("audio/vnd.wave")).isEqualTo(ChatAttachmentDTO.TYPE_AUDIO);
        assertThat(ChatAttachmentDTO.resolveType("audio/vorbis")).isEqualTo(ChatAttachmentDTO.TYPE_AUDIO);
        assertThat(ChatAttachmentDTO.resolveType("video/webm")).isEqualTo(ChatAttachmentDTO.TYPE_AUDIO);
    }

    @Test
    void rejectsUnsupportedMime() {
        assertThat(ChatAttachmentDTO.resolveType("application/pdf")).isNull();
        assertThat(ChatAttachmentDTO.resolveType("video/mp4")).isNull();
        assertThat(ChatAttachmentDTO.resolveType(null)).isNull();
    }
}
