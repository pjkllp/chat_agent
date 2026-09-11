package org.example.travel_agent.service;

import org.example.travel_agent.dto.ChatAttachmentDTO;
import org.junit.jupiter.api.Test;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatAttachmentSupportTest {

    private static final String PUBLIC_HOST = "https://b.oss.example.com";

    /** 纯校验 / hasImage 用例用：不带转写模型。 */
    private ChatAttachmentSupport support(String publicHost) {
        return new ChatAttachmentSupport(null, publicHost, 4);
    }

    /** 转写 / buildUserMessage 用例用：注入 mock 模型，白名单固定。 */
    private ChatAttachmentSupport support(TranscriptionModel model) {
        return new ChatAttachmentSupport(model, PUBLIC_HOST, 4);
    }

    private ChatAttachmentDTO img(String url) {
        return ChatAttachmentDTO.builder().url(url).type(ChatAttachmentDTO.TYPE_IMAGE)
                .mimeType("image/jpeg").build();
    }

    private ChatAttachmentDTO audio(String url) {
        return ChatAttachmentDTO.builder().url(url).type(ChatAttachmentDTO.TYPE_AUDIO)
                .mimeType("audio/mpeg").build();
    }

    @Test
    void acceptsCountWithinLimit() {
        var s = support(PUBLIC_HOST);
        s.validate(List.of(
                img("https://b.oss.example.com/chat/c/1.jpg"),
                img("https://b.oss.example.com/chat/c/2.jpg")));
    }

    @Test
    void rejectsTooManyAttachments() {
        var s = support(PUBLIC_HOST);
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
        var s = support(PUBLIC_HOST);
        assertThatThrownBy(() -> s.validate(List.of(img("https://evil.example.com/x.jpg"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hasImageDetects() {
        var s = support(PUBLIC_HOST);
        assertThat(s.hasImage(List.of(img("https://b.oss.example.com/x.jpg")))).isTrue();
    }

    @Test
    void emptyAttachmentsPassThrough() {
        var s = support(PUBLIC_HOST);
        s.validate(null);
        assertThat(s.hasImage(null)).isFalse();
    }

    // ---- 新增失败用例：白名单 fail-closed 与 scheme 校验 ----

    @Test
    void failsClosedWhenPublicHostBlank() {
        var s = support("");
        assertThatThrownBy(() -> s.validate(List.of(img("https://b.oss.example.com/x.jpg"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不合法");
    }

    @Test
    void rejectsNonHttpSchemeWithMatchingHost() {
        var s = support("b.oss.example.com");
        assertThatThrownBy(() -> s.validate(List.of(img("file://b.oss.example.com/x.jpg"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsRelativePath() {
        var s = support("b.oss.example.com");
        assertThatThrownBy(() -> s.validate(List.of(img("/x.jpg"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- transcribeAudios ----

    @Test
    void transcribeJoinsMultipleAudioWithPrefix() {
        var model = mock(TranscriptionModel.class);
        when(model.transcribe(any())).thenReturn("一", "二");
        String result = support(model).transcribeAudios(List.of(
                audio("https://b.oss.example.com/a.mp3"),
                audio("https://b.oss.example.com/b.mp3")));
        assertThat(result).isEqualTo("[语音转写] 一\n[语音转写] 二");
    }

    @Test
    void transcribeNullOrBlankBecomesEmptyPlaceholder() {
        var nullModel = mock(TranscriptionModel.class);
        when(nullModel.transcribe(any())).thenReturn(null);
        assertThat(support(nullModel).transcribeAudios(List.of(audio("https://b.oss.example.com/a.mp3"))))
                .isEqualTo("[语音转写为空]");

        var blankModel = mock(TranscriptionModel.class);
        when(blankModel.transcribe(any())).thenReturn("   ");
        assertThat(support(blankModel).transcribeAudios(List.of(audio("https://b.oss.example.com/a.mp3"))))
                .isEqualTo("[语音转写为空]");
    }

    @Test
    void transcribeFailureDegradesWithoutThrowing() {
        var model = mock(TranscriptionModel.class);
        when(model.transcribe(any())).thenThrow(new RuntimeException("boom"));
        var s = support(model);
        assertThatCode(() -> {
            String result = s.transcribeAudios(List.of(audio("https://b.oss.example.com/a.mp3")));
            assertThat(result).isEqualTo("[语音转写失败]");
        }).doesNotThrowAnyException();
    }

    @Test
    void transcribeNullOrNoAudioReturnsEmpty() {
        var model = mock(TranscriptionModel.class);
        when(model.transcribe(any())).thenReturn("你好");
        var s = support(model);
        assertThat(s.transcribeAudios(null)).isEmpty();
        assertThat(s.transcribeAudios(List.of(img("https://b.oss.example.com/x.jpg")))).isEmpty();
    }

    // ---- buildUserMessage ----

    @Test
    void buildUserMessageAppendsTranscriptToQuestion() {
        var model = mock(TranscriptionModel.class);
        when(model.transcribe(any())).thenReturn("你好");
        UserMessage msg = support(model).buildUserMessage("描述一下",
                List.of(audio("https://b.oss.example.com/a.mp3")));
        assertThat(msg.getText()).isEqualTo("描述一下\n[语音转写] 你好");
    }

    @Test
    void buildUserMessageUsesTranscriptWhenQuestionBlank() {
        var model = mock(TranscriptionModel.class);
        when(model.transcribe(any())).thenReturn("你好");
        UserMessage msg = support(model).buildUserMessage("   ",
                List.of(audio("https://b.oss.example.com/a.mp3")));
        assertThat(msg.getText()).isEqualTo("[语音转写] 你好");
    }

    @Test
    void buildUserMessageAttachesImagesAndMetadata() {
        var s = support(PUBLIC_HOST);
        UserMessage msg = s.buildUserMessage("看这两张图", List.of(
                img("https://b.oss.example.com/1.jpg"),
                img("https://b.oss.example.com/2.jpg")));
        assertThat(msg.getText()).isEqualTo("看这两张图");
        assertThat(msg.getMedia()).hasSize(2);
        assertThat(msg.getMetadata()).containsKey("attachments");
    }
}
