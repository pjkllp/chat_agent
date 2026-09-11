# 聊天附件（图片/音频）输入 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 让聊天支持上传图片/音频：图片送 qwen-vl 多模态理解，音频经 ASR 转写为文本，附件持久化到会话历史。

**架构：** 前端"发送时上传"——选中文件先本地预览，点发送后并发上传到阿里云 OSS 公网桶，拿到直链后再发起 SSE chat。后端无状态：图片以 `Media`(data=URL 字符串) 交给视觉模型，音频用独立 ASR 模型转写后并入文本，附件元信息随 USER 消息落库 `attachment_json`。

**技术栈：** Spring Boot 3.5.9 / Java 21、spring-ai-alibaba 1.1.2.0(`DashScopeChatModel` + `TranscriptionModel`)、`aliyun-sdk-oss`、Apache Tika、MyBatis-Plus + PostgreSQL、Vue 3 + Pinia。

**已验证的框架约束（勿踩坑）：**
- `DashScopeChatModel.convertMediaContent` 每条 `UserMessage` 只支持**一种**媒体格式（metadata `messageFormat`，默认 `IMAGE`）。**图片与音频不能同一条消息混传**。本方案音频走 ASR→文本，最终只发 text+image，规避该限制。
- `Media.data` 若为 **String** 会原样透传给 DashScope（`fromMediaData`），因此 **OSS 直链可直接用**，后端无需下载/base64。**必须传 String，不能传 URI**（URI 会抛 IllegalArgumentException）。
- ASR 用 Spring AI `TranscriptionModel`（`DashScopeAudioTranscriptionModel` 实现之），传 `UrlResource` 走 `Resource.getURL()` → `file_urls=[url]`，无需本地下载。
- 项目现有 **4 处**按类型注入 `ChatClient`（3 个 Node 的 `deepThinkChatClient` + `ChatServiceImpl.chatClient`）。**绝不能新增第二个 `ChatClient` bean**（会 `NoUniqueBeanDefinitionException`）。视觉模型通过**每次请求 options 覆盖**实现。

**测试策略说明：** 纯逻辑（类型解析、附件校验、消息构建）用 JUnit 单测（`spring-boot-starter-test` 已就绪）。OSS 上传与 DashScope 转写依赖真实凭据/网络，**不写单测**，改用"编译通过 + 手动验证"步骤，并在计划中给出确切验证命令。

---

## 文件结构

**后端新增：**
- `src/main/java/org/example/travel_agent/config/AliyunOssProperties.java` — OSS 配置绑定
- `src/main/java/org/example/travel_agent/config/AliyunOssConfig.java` — `OSS` 客户端 bean
- `src/main/java/org/example/travel_agent/dto/ChatAttachmentDTO.java` — 附件元信息
- `src/main/java/org/example/travel_agent/service/ChatAttachmentService.java` — 上传接口
- `src/main/java/org/example/travel_agent/service/impl/AliyunOssChatAttachmentService.java` — OSS 上传实现
- `src/main/java/org/example/travel_agent/service/ChatAttachmentSupport.java` — 校验/转写/构建 `UserMessage`
- `src/main/java/org/example/travel_agent/controller/ChatAttachmentController.java` — 上传端点
- `src/main/resources/db/migration/V2__chat_attachment.sql` — 建表参考脚本
- `src/test/java/org/example/travel_agent/dto/ChatAttachmentDTOTest.java` — 类型解析单测
- `src/test/java/org/example/travel_agent/service/ChatAttachmentSupportTest.java` — 校验单测

**后端修改：**
- `pom.xml` — 加 `aliyun-sdk-oss`
- `src/main/resources/application.yml` — `aliyun.oss.*`、`spring.ai.model.audio.transcription`、`app.chat.*`
- `src/main/java/org/example/travel_agent/dto/ChatRequest.java` — 加 `attachments`
- `src/main/java/org/example/travel_agent/service/impl/ChatServiceImpl.java` — 简单路径接入
- `src/main/java/org/example/travel_agent/Node/answer_node.java` — 深度思考路径接入 + 附件 metadata
- `src/main/java/org/example/travel_agent/dao/entity/AiChatMemoryEntity.java` — 加 `attachmentJson`
- `src/main/java/org/example/travel_agent/common/MessageConvertUtil.java` — 读写 `attachmentJson`

**前端修改：**
- `web-client/src/services/chat.js` — 加 `uploadAttachment`
- `web-client/src/stores/chat.js` — 发送带附件、历史解析附件
- `web-client/src/views/ChatView.vue` — 透传附件
- `web-client/src/components/chat/Composer.vue` — 选择/预览/发送时上传
- `web-client/src/components/chat/ChatMessage.vue` — 渲染附件

---

## 任务 1：依赖与配置骨架

**文件：**
- 修改：`pom.xml`
- 修改：`src/main/resources/application.yml`
- 创建：`src/main/java/org/example/travel_agent/config/AliyunOssProperties.java`
- 创建：`src/main/java/org/example/travel_agent/config/AliyunOssConfig.java`

- [ ] **步骤 1：加 OSS 依赖**

在 `pom.xml` 的 `io.minio:minio` 依赖块之后追加：

```xml
<dependency>
    <groupId>com.aliyun.oss</groupId>
    <artifactId>aliyun-sdk-oss</artifactId>
    <version>3.17.4</version>
</dependency>
```

- [ ] **步骤 2：加配置项**

在 `src/main/resources/application.yml` 的 `spring.ai.dashscope` 下（`embedding` 同级）加：

```yaml
      audio:
        transcription:
          options:
            model: paraformer-v2
```

在 `spring.ai` 下加（与 `dashscope` 同级）：

```yaml
    model:
      audio:
        transcription: dashscope
```

在 `app` 下加：

```yaml
  chat:
    vision-model: qwen-vl-max
    max-attachments: 4
```

文件末尾追加：

```yaml
aliyun:
  oss:
    endpoint: ${OSS_ENDPOINT:}
    bucket: ${OSS_BUCKET:}
    access-key-id: ${OSS_ACCESS_KEY_ID:}
    access-key-secret: ${OSS_ACCESS_KEY_SECRET:}
    public-host: ${OSS_PUBLIC_HOST:}
```

- [ ] **步骤 3：创建 AliyunOssProperties**

`src/main/java/org/example/travel_agent/config/AliyunOssProperties.java`：

```java
package org.example.travel_agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "aliyun.oss")
public class AliyunOssProperties {

    private String endpoint;
    private String bucket;
    private String accessKeyId;
    private String accessKeySecret;
    /** 公网访问前缀，例如 https://mybucket.oss-cn-hangzhou.aliyuncs.com（不含结尾斜杠） */
    private String publicHost;
}
```

- [ ] **步骤 4：创建 AliyunOssConfig**

`src/main/java/org/example/travel_agent/config/AliyunOssConfig.java`：

```java
package org.example.travel_agent.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AliyunOssProperties.class)
public class AliyunOssConfig {

    @Bean(destroyMethod = "shutdown")
    public OSS aliyunOssClient(AliyunOssProperties properties) {
        return new OSSClientBuilder().build(
                properties.getEndpoint(),
                properties.getAccessKeyId(),
                properties.getAccessKeySecret());
    }
}
```

- [ ] **步骤 5：编译验证**

运行：`JAVA_HOME=C:/Users/27978/.jdks/ms-21.0.10 mvn -o -q compile`
预期：BUILD SUCCESS，无编译错误。

- [ ] **步骤 6：Commit**

```bash
git add pom.xml src/main/resources/application.yml src/main/java/org/example/travel_agent/config/AliyunOssProperties.java src/main/java/org/example/travel_agent/config/AliyunOssConfig.java
git commit -m "feat: 接入阿里云 OSS 客户端与附件相关配置"
```

---

## 任务 2：ChatAttachmentDTO 与类型解析（TDD）

**文件：**
- 创建：`src/main/java/org/example/travel_agent/dto/ChatAttachmentDTO.java`
- 测试：`src/test/java/org/example/travel_agent/dto/ChatAttachmentDTOTest.java`

- [ ] **步骤 1：先写失败的测试**

`src/test/java/org/example/travel_agent/dto/ChatAttachmentDTOTest.java`：

```java
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

    @Test
    void rejectsUnsupportedMime() {
        assertThat(ChatAttachmentDTO.resolveType("application/pdf")).isNull();
        assertThat(ChatAttachmentDTO.resolveType("video/mp4")).isNull();
        assertThat(ChatAttachmentDTO.resolveType(null)).isNull();
    }
}
```

- [ ] **步骤 2：运行测试确认失败**

运行：`JAVA_HOME=C:/Users/27978/.jdks/ms-21.0.10 mvn -o -q -Dtest=ChatAttachmentDTOTest test`
预期：编译失败，"cannot find symbol: method resolveType"。

- [ ] **步骤 3：实现 ChatAttachmentDTO**

`src/main/java/org/example/travel_agent/dto/ChatAttachmentDTO.java`：

```java
package org.example.travel_agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatAttachmentDTO {

    public static final String TYPE_IMAGE = "IMAGE";
    public static final String TYPE_AUDIO = "AUDIO";

    private static final Map<String, String> ALLOWED = Map.ofEntries(
            Map.entry("image/jpeg", TYPE_IMAGE),
            Map.entry("image/png", TYPE_IMAGE),
            Map.entry("image/webp", TYPE_IMAGE),
            Map.entry("image/gif", TYPE_IMAGE),
            Map.entry("audio/mpeg", TYPE_AUDIO),
            Map.entry("audio/wav", TYPE_AUDIO),
            Map.entry("audio/x-wav", TYPE_AUDIO),
            Map.entry("audio/mp4", TYPE_AUDIO),
            Map.entry("audio/webm", TYPE_AUDIO),
            Map.entry("audio/ogg", TYPE_AUDIO)
    );

    private String url;
    private String type;      // IMAGE | AUDIO
    private String fileName;
    private String mimeType;
    private Long size;

    /** 返回 IMAGE / AUDIO；不支持的类型返回 null。 */
    public static String resolveType(String mimeType) {
        if (mimeType == null) {
            return null;
        }
        return ALLOWED.get(mimeType.toLowerCase());
    }
}
```

- [ ] **步骤 4：运行测试确认通过**

运行：`JAVA_HOME=C:/Users/27978/.jdks/ms-21.0.10 mvn -o -q -Dtest=ChatAttachmentDTOTest test`
预期：PASS（3 个测试通过）。

- [ ] **步骤 5：Commit**

```bash
git add src/main/java/org/example/travel_agent/dto/ChatAttachmentDTO.java src/test/java/org/example/travel_agent/dto/ChatAttachmentDTOTest.java
git commit -m "feat: 新增 ChatAttachmentDTO 与附件类型解析"
```

---

## 任务 3：上传服务与接口

**文件：**
- 创建：`src/main/java/org/example/travel_agent/service/ChatAttachmentService.java`
- 创建：`src/main/java/org/example/travel_agent/service/impl/AliyunOssChatAttachmentService.java`
- 创建：`src/main/java/org/example/travel_agent/controller/ChatAttachmentController.java`

- [ ] **步骤 1：定义上传接口**

`src/main/java/org/example/travel_agent/service/ChatAttachmentService.java`：

```java
package org.example.travel_agent.service;

import org.example.travel_agent.dto.ChatAttachmentDTO;
import org.springframework.web.multipart.MultipartFile;

public interface ChatAttachmentService {

    ChatAttachmentDTO upload(MultipartFile file, String conversationId);
}
```

- [ ] **步骤 2：实现 OSS 上传**

`src/main/java/org/example/travel_agent/service/impl/AliyunOssChatAttachmentService.java`：

```java
package org.example.travel_agent.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.example.travel_agent.config.AliyunOssProperties;
import org.example.travel_agent.dto.ChatAttachmentDTO;
import org.example.travel_agent.service.ChatAttachmentService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class AliyunOssChatAttachmentService implements ChatAttachmentService {

    private static final long MAX_SIZE = 20L * 1024 * 1024;

    private final OSS aliyunOssClient;
    private final AliyunOssProperties properties;
    private final Tika tika = new Tika();

    @Override
    public ChatAttachmentDTO upload(MultipartFile file, String conversationId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new IllegalArgumentException("文件大小超过 20MB 限制");
        }
        if (StrUtil.isBlank(conversationId)) {
            throw new IllegalArgumentException("conversationId 不能为空");
        }

        String detected;
        try (InputStream in = file.getInputStream()) {
            detected = tika.detect(in, file.getOriginalFilename());
        } catch (Exception e) {
            throw new RuntimeException("文件类型检测失败", e);
        }
        String type = ChatAttachmentDTO.resolveType(detected);
        if (type == null) {
            throw new IllegalArgumentException("不支持的文件类型: " + detected);
        }

        String ext = extOf(file.getOriginalFilename(), detected);
        String objectKey = "chat/" + conversationId + "/" + IdUtil.getSnowflakeNextIdStr() + "." + ext;
        try (InputStream in = file.getInputStream()) {
            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentType(detected);
            meta.setContentLength(file.getSize());
            aliyunOssClient.putObject(properties.getBucket(), objectKey, in, meta);
        } catch (Exception e) {
            log.error("OSS upload failed, file={}, msg={}", file.getOriginalFilename(), e.getMessage(), e);
            throw new RuntimeException("上传到 OSS 失败");
        }

        String url = StrUtil.removeSuffix(properties.getPublicHost(), "/") + "/" + objectKey;
        return ChatAttachmentDTO.builder()
                .url(url)
                .type(type)
                .fileName(file.getOriginalFilename())
                .mimeType(detected)
                .size(file.getSize())
                .build();
    }

    private String extOf(String fileName, String mimeType) {
        if (fileName != null && fileName.lastIndexOf('.') >= 0) {
            return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        }
        return mimeType.substring(mimeType.indexOf('/') + 1);
    }
}
```

> 注意：`io.minio` 的 `ObjectMetadata` 与本类的 `com.aliyun.oss.model.ObjectMetadata` 同名，切勿混淆导入。

- [ ] **步骤 3：创建上传端点**

`src/main/java/org/example/travel_agent/controller/ChatAttachmentController.java`：

```java
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
```

- [ ] **步骤 4：编译验证**

运行：`JAVA_HOME=C:/Users/27978/.jdks/ms-21.0.10 mvn -o -q compile`
预期：BUILD SUCCESS。

- [ ] **步骤 5：手动验证上传（需已配置 OSS 且桶为公共读）**

启动后端后运行（`<TOKEN>` 换成有效 JWT）：
```bash
curl -s -X POST http://localhost:10009/api/chat/attachment/upload \
  -H "Authorization: Bearer <TOKEN>" \
  -F "file=@/path/to/test.jpg" -F "conversationId=test-conv-1"
```
预期：返回 `{"msg":"上传成功","data":{"url":"https://<bucket>.../chat/test-conv-1/<id>.jpg","type":"IMAGE",...},"code":"1"}`。
再用浏览器打开返回的 `url`，确认可公网访问。若返回 `不支持的文件类型` 或 `上传到 OSS 失败`，检查 OSS 配置。

- [ ] **步骤 6：Commit**

```bash
git add src/main/java/org/example/travel_agent/service/ChatAttachmentService.java src/main/java/org/example/travel_agent/service/impl/AliyunOssChatAttachmentService.java src/main/java/org/example/travel_agent/controller/ChatAttachmentController.java
git commit -m "feat: 新增附件上传服务与 /api/chat/attachment/upload 端点"
```

---

## 任务 4：ChatAttachmentSupport（校验 + 转写 + 构建消息，TDD 校验部分）

**文件：**
- 创建：`src/main/java/org/example/travel_agent/service/ChatAttachmentSupport.java`
- 测试：`src/test/java/org/example/travel_agent/service/ChatAttachmentSupportTest.java`

- [ ] **步骤 1：先写失败的测试（仅校验逻辑）**

`src/test/java/org/example/travel_agent/service/ChatAttachmentSupportTest.java`：

```java
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
```

- [ ] **步骤 2：运行测试确认失败**

运行：`JAVA_HOME=C:/Users/27978/.jdks/ms-21.0.10 mvn -o -q -Dtest=ChatAttachmentSupportTest test`
预期：编译失败，"cannot find symbol: class ChatAttachmentSupport"。

- [ ] **步骤 3：实现 ChatAttachmentSupport**

`src/main/java/org/example/travel_agent/service/ChatAttachmentSupport.java`：

```java
package org.example.travel_agent.service;

import lombok.extern.slf4j.Slf4j;
import org.example.travel_agent.dto.ChatAttachmentDTO;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ChatAttachmentSupport {

    private final TranscriptionModel transcriptionModel;
    private final String ossPublicHost;
    private final int maxAttachments;

    public ChatAttachmentSupport(TranscriptionModel transcriptionModel,
                                 @Value("${aliyun.oss.public-host:}") String ossPublicHost,
                                 @Value("${app.chat.max-attachments:4}") int maxAttachments) {
        this.transcriptionModel = transcriptionModel;
        this.ossPublicHost = ossPublicHost;
        this.maxAttachments = maxAttachments;
    }

    public void validate(List<ChatAttachmentDTO> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return;
        }
        if (attachments.size() > maxAttachments) {
            throw new IllegalArgumentException("单条消息最多 " + maxAttachments + " 个附件");
        }
        String expectedHost = hostOf(ossPublicHost);
        for (ChatAttachmentDTO a : attachments) {
            if (a == null || a.getUrl() == null || !hostOf(a.getUrl()).equalsIgnoreCase(expectedHost)) {
                throw new IllegalArgumentException("附件地址不合法");
            }
        }
    }

    public boolean hasImage(List<ChatAttachmentDTO> attachments) {
        return attachments != null && attachments.stream()
                .anyMatch(a -> a != null && ChatAttachmentDTO.TYPE_IMAGE.equals(a.getType()));
    }

    /** 把音频附件逐个转写为文本；无音频返回 ""。失败降级为占位文案，不抛出。 */
    public String transcribeAudios(List<ChatAttachmentDTO> attachments) {
        if (attachments == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ChatAttachmentDTO a : attachments) {
            if (a == null || !ChatAttachmentDTO.TYPE_AUDIO.equals(a.getType())) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            try {
                String text = transcriptionModel.transcribe(new UrlResource(URI.create(a.getUrl())));
                sb.append(text == null || text.isBlank() ? "[语音转写为空]" : "[语音转写] " + text);
            } catch (Exception e) {
                log.warn("audio transcription failed, url={}, msg={}", a.getUrl(), e.getMessage());
                sb.append("[语音转写失败]");
            }
        }
        return sb.toString();
    }

    /** 构建最终发给模型的 UserMessage：音频转写并入文本，图片作为 Media(URL)。 */
    public UserMessage buildUserMessage(String question, List<ChatAttachmentDTO> attachments) {
        String text = question == null ? "" : question;
        String transcript = transcribeAudios(attachments);
        if (!transcript.isBlank()) {
            text = text.isBlank() ? transcript : text + "\n" + transcript;
        }

        List<Media> media = new ArrayList<>();
        if (attachments != null) {
            for (ChatAttachmentDTO a : attachments) {
                if (a != null && ChatAttachmentDTO.TYPE_IMAGE.equals(a.getType())) {
                    media.add(Media.builder()
                            .mimeType(MimeTypeUtils.parseMimeType(a.getMimeType()))
                            .data(a.getUrl())   // 必须是 String，DashScope 会原样透传
                            .build());
                }
            }
        }

        UserMessage.Builder builder = UserMessage.builder().text(text);
        if (!media.isEmpty()) {
            builder.media(media);
        }
        if (attachments != null && !attachments.isEmpty()) {
            builder.metadata(java.util.Map.of("attachments", attachments));
        }
        return builder.build();
    }

    private String hostOf(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            return URI.create(url).getHost() == null ? "" : URI.create(url).getHost();
        } catch (Exception e) {
            return "";
        }
    }
}
```

> 说明：`buildUserMessage` 把附件列表放进 `UserMessage` 的 metadata，任务 7 会从这里读取并写入 `attachment_json`，从而**只关联当前这一轮**，避免污染历史消息。

- [ ] **步骤 4：运行测试确认通过**

运行：`JAVA_HOME=C:/Users/27978/.jdks/ms-21.0.10 mvn -o -q -Dtest=ChatAttachmentSupportTest test`
预期：PASS（5 个测试通过）。

- [ ] **步骤 5：Commit**

```bash
git add src/main/java/org/example/travel_agent/service/ChatAttachmentSupport.java src/test/java/org/example/travel_agent/service/ChatAttachmentSupportTest.java
git commit -m "feat: 新增附件校验与多模态消息构建 ChatAttachmentSupport"
```

---

## 任务 5：ChatRequest 扩展与简单路径接入

**文件：**
- 修改：`src/main/java/org/example/travel_agent/dto/ChatRequest.java`
- 修改：`src/main/java/org/example/travel_agent/service/impl/ChatServiceImpl.java`

- [ ] **步骤 1：ChatRequest 增加 attachments 字段**

在 `ChatRequest` 类中，`isDeepThink` 之后加：

```java
    private List<ChatAttachmentDTO> attachments;
```

并加导入：

```java
import java.util.List;
```

- [ ] **步骤 2：简单路径接入附件与视觉模型**

把 `ChatServiceImpl` 中 `if(isDeepThink!=1){ ... return; }` 整段替换为：

```java
        List<ChatAttachmentDTO> attachments = requestParam.getAttachments();
        chatAttachmentSupport.validate(attachments);

        if(isDeepThink!=1){
            ChatClient.ChatClientRequestSpec promptSpec = chatClient.prompt()
                    .system("你是一名可爱的用户助手，请帮助用户解决问题")
                    .user(chatAttachmentSupport.buildUserMessage(originalQuestion, attachments));
            if (chatAttachmentSupport.hasImage(attachments)) {
                promptSpec.options(DashScopeChatOptions.builder().model(visionModel).build());
            }
            promptSpec.stream()
                    .content()
                    .doOnNext(content -> {
                        try {
                            sse.send(content);
                        } catch (Exception e) {
                            log.error("[chatStream] send message failed, conversationId={}, msg={}", finalConversationId, e.getMessage(), e);
                            sse.completeWithError(e);
                            sseEmitterRegistry.remove(finalConversationId);
                        }
                    }).doOnComplete(() -> {
                        log.info("[chatStream] chat completed, conversationId={}", finalConversationId);
                        sse.complete();
                        sseEmitterRegistry.remove(finalConversationId);
                    }).doOnError(e -> {
                        log.error("[chatStream] chat failed, conversationId={}, msg={}", finalConversationId, e.getMessage(), e);
                        sse.completeWithError(e);
                        sseEmitterRegistry.remove(finalConversationId);
                    }).subscribe();
            return;
        }
```

> 注意：**不能**写 `.options(cond ? opts : null)`——`DefaultChatClient` 会抛 `options cannot be null`。必须如上面这样：先构建 spec，仅在 `hasImage` 时调用 `.options(...)`。

- [ ] **步骤 3：补充字段与导入**

在 `ChatServiceImpl` 中加字段：

```java
    private final ChatAttachmentSupport chatAttachmentSupport;

    @Value("${app.chat.vision-model:qwen-vl-max}")
    private String visionModel;
```

加导入：

```java
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.example.travel_agent.dto.ChatAttachmentDTO;
import org.example.travel_agent.service.ChatAttachmentSupport;
import org.springframework.beans.factory.annotation.Value;
import java.util.List;
```

并把深度思考路径的 state 传入附件：

```java
                deepThinkGraph.invoke(
                        Map.of(
                                "original_question", originalQuestion,
                                "conversationId", finalConversationId,
                                "userId",userId,
                                "attachments", attachments == null ? List.of() : attachments
                        )
                );
```

- [ ] **步骤 4：编译验证**

运行：`JAVA_HOME=C:/Users/27978/.jdks/ms-21.0.10 mvn -o -q compile`
预期：BUILD SUCCESS。
若报 `Map.of` 参数上限或空值异常（`Map.of` 不接受 null），确认 `attachments` 已做空处理（如上）。

- [ ] **步骤 5：手动验证（简单模式）**

启动后端，前端关掉"深度思考"，发一张图 + 文字，确认模型回答里体现出对图片内容的理解。

- [ ] **步骤 6：Commit**

```bash
git add src/main/java/org/example/travel_agent/dto/ChatRequest.java src/main/java/org/example/travel_agent/service/impl/ChatServiceImpl.java
git commit -m "feat: 聊天请求支持附件，简单路径按附件切换视觉模型"
```

---

## 任务 6：深度思考路径接入（answer_node）

**文件：**
- 修改：`src/main/java/org/example/travel_agent/Node/answer_node.java`

- [ ] **步骤 1：读取 state 中的 attachments**

在 `answer_node` 的 `apply` 方法里，`String originalQuestion = state.value("original_question", "");` 之后加：

```java
        @SuppressWarnings("unchecked")
        List<ChatAttachmentDTO> attachments = state.value("attachments")
                .filter(List.class::isInstance)
                .map(v -> (List<ChatAttachmentDTO>) v)
                .orElse(List.of());
```

- [ ] **步骤 2：注入 ChatAttachmentSupport 与 visionModel**

加字段：

```java
    private final ChatAttachmentSupport chatAttachmentSupport;

    @Value("${app.chat.vision-model:qwen-vl-max}")
    private String visionModel;
```

加导入：

```java
import org.example.travel_agent.dto.ChatAttachmentDTO;
import org.example.travel_agent.service.ChatAttachmentSupport;
import org.springframework.beans.factory.annotation.Value;
import java.util.List;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
```

- [ ] **步骤 3：用带附件的 UserMessage 替换原 user 调用**

把两处 `.user(originalQuestion)` 与 `.user(summaryPrompt)` 分别改为携带附件与模型路由。

第一处（流式，约第 57-79 行）改为：

```java
            AtomicBoolean hasStreamChunk = new AtomicBoolean(false);
            ChatClient.ChatClientRequestSpec streamSpec = deepThinkChatClient.prompt()
                    .advisors(persistMemoryAdvisor)
                    .advisors(advisorSpec -> advisorSpec.params(
                            Map.of(
                                    "conversationId", conversationId,
                                    "userId", userId
                            )
                    ))
                    .system(classPathResource)
                    .system(summaryPrompt)
                    .user(chatAttachmentSupport.buildUserMessage(originalQuestion, attachments));
            if (chatAttachmentSupport.hasImage(attachments)) {
                streamSpec.options(DashScopeChatOptions.builder().model(visionModel).build());
            }
            streamSpec.stream()
                    .content()
                    .doOnNext(data -> {
                        hasStreamChunk.set(true);
                        try {
                            sseEventUtil.sendAnswerChunk(state, data);
                        } catch (Exception e) {
                            throw new ClientDisconnectedException("SSE client disconnected", e);
                        }
                    })
                    .blockLast();
```

第二处（回落调用，约第 83-94 行）改为：

```java
                ChatClient.ChatClientRequestSpec fallbackSpec = deepThinkChatClient.prompt()
                        .advisors(persistMemoryAdvisor)
                        .advisors(advisorSpec -> advisorSpec.params(
                                Map.of(
                                        "conversationId", conversationId,
                                        "userId", userId
                                )
                        ))
                        .system(classPathResource)
                        .user(chatAttachmentSupport.buildUserMessage(summaryPrompt, attachments));
                if (chatAttachmentSupport.hasImage(attachments)) {
                    fallbackSpec.options(DashScopeChatOptions.builder().model(visionModel).build());
                }
                String fullAnswer = fallbackSpec.call().content();
```

> 原先未使用的 `import org.springframework.ai.content.Media;` 在本任务后仍不需要（Media 由 `ChatAttachmentSupport` 内部构建），**删除该导入**以免留下未使用告警。

- [ ] **步骤 4：编译验证**

运行：`JAVA_HOME=C:/Users/27978/.jdks/ms-21.0.10 mvn -o -q compile`
预期：BUILD SUCCESS。

- [ ] **步骤 5：手动验证（深度思考模式）**

启动后端，前端开启"深度思考"，发图 + 文字，确认：思考链路正常跑完、最终答案理解图片内容；发一段音频，确认答案里出现"🎙/语音转写"内容（日志中 `[语音转写]`）。

- [ ] **步骤 6：Commit**

```bash
git add src/main/java/org/example/travel_agent/Node/answer_node.java
git commit -m "feat: 深度思考路径支持图片附件与音频转写"
```

---

## 任务 7：附件持久化到会话历史

**文件：**
- 创建：`src/main/resources/db/migration/V2__chat_attachment.sql`
- 修改：`src/main/java/org/example/travel_agent/dao/entity/AiChatMemoryEntity.java`
- 修改：`src/main/java/org/example/travel_agent/common/MessageConvertUtil.java`

- [ ] **步骤 1：写建表参考脚本**

`src/main/resources/db/migration/V2__chat_attachment.sql`：

```sql
ALTER TABLE t_ai_chat_memory ADD COLUMN IF NOT EXISTS attachment_json TEXT;
```

- [ ] **步骤 2：手动执行迁移**

对项目使用的 PostgreSQL（`application.yml` 中 `localhost:15432/ReactAgent`）执行上面的 SQL（用 psql 或客户端工具）。

运行：
```bash
psql -h localhost -p 15432 -U postgres -d ReactAgent -c "ALTER TABLE t_ai_chat_memory ADD COLUMN IF NOT EXISTS attachment_json TEXT;"
```
预期：`ALTER TABLE`。

- [ ] **步骤 3：实体加字段**

在 `AiChatMemoryEntity` 的 `content` 字段之后加：

```java
    @TableField("attachment_json")
    private String attachmentJson;
```

- [ ] **步骤 4：MessageConvertUtil 写入附件 JSON**

在 `MessageConvertUtil` 中加静态 ObjectMapper，并在 `toEntities` 里读取 `UserMessage` metadata 的 `attachments`：

在类顶部加：

```java
import com.fasterxml.jackson.databind.ObjectMapper;
```

在类内加：

```java
    private static final ObjectMapper MAPPER = new ObjectMapper();
```

在 `toEntities` 的 `for (Message message : messages)` 循环体内，`entity.setCreateTime(OffsetDateTime.now());` 之后加：

```java
            Object attachments = message.getMetadata() == null
                    ? null
                    : message.getMetadata().get("attachments");
            if (attachments != null) {
                try {
                    entity.setAttachmentJson(MAPPER.writeValueAsString(attachments));
                } catch (Exception e) {
                    // 序列化失败不影响消息落库
                }
            }
```

> `toMessages`（历史还原给模型）**不**处理附件——历史只用文本，避免每轮重发图片。

- [ ] **步骤 5：编译并跑通测试**

运行：`JAVA_HOME=C:/Users/27978/.jdks/ms-21.0.10 mvn -o -q test`
预期：BUILD SUCCESS，既有测试仍通过。

- [ ] **步骤 6：手动验证持久化**

深度思考模式发一条带图片的消息后，查库：
```bash
psql -h localhost -p 15432 -U postgres -d ReactAgent -c "SELECT message_type, content, attachment_json FROM t_ai_chat_memory ORDER BY create_time DESC LIMIT 5;"
```
预期：USER 行的 `attachment_json` 为 `[{"url":"https://...","type":"IMAGE",...}]`，其余行为 NULL。

- [ ] **步骤 7：Commit**

```bash
git add src/main/resources/db/migration/V2__chat_attachment.sql src/main/java/org/example/travel_agent/dao/entity/AiChatMemoryEntity.java src/main/java/org/example/travel_agent/common/MessageConvertUtil.java
git commit -m "feat: 会话历史持久化附件元信息"
```

---

## 任务 8：前端上传与发送（services / store / view）

**文件：**
- 修改：`web-client/src/services/chat.js`
- 修改：`web-client/src/stores/chat.js`
- 修改：`web-client/src/views/ChatView.vue`

- [ ] **步骤 1：services 加上传方法**

`web-client/src/services/chat.js` 顶部改为：

```js
import { get, del, upload } from "./api";

const BASE = "/api/chat";
```

文件末尾加：

```js
export function uploadAttachment(file, conversationId) {
  const fd = new FormData();
  fd.append("file", file);
  fd.append("conversationId", conversationId);
  return upload(`${BASE}/attachment/upload`, fd);
}
```

- [ ] **步骤 2：store 发送携带附件、历史解析附件**

在 `web-client/src/stores/chat.js` 顶部导入：

```js
import { fetchConversations as apiConversations, fetchMessages as apiMessages, deleteConversation as apiDelete } from "../services/chat";
```

（若已有该导入则保持不变，无需重复。）

把 `sendQuestion(question, deepThink)` 改为：

```js
    sendQuestion(question, deepThink, attachments = []) {
      if (this.streaming) return;
      this.streaming = true;
      this.messages.push({ role: "user", content: question, attachments });
      this.messages.push({ role: "assistant", content: "", streaming: true, thinkingSteps: [] });
      this.workflowSteps = [];
      if (this.title === "新对话") {
        this.title = question.slice(0, 50) || "图片/音频消息";
      }

      const sse = useSSE();
      const convId = this.conversationId;

      sse.connect(
        "/api/chat/chat",
        { question, conversationId: convId, isDeepThink: deepThink ? 1 : 0, attachments },
```

（`sse.connect(...)` 与其后的 handlers 保持原样不变。）

把 `loadMessages` 里的映射改为解析 `attachmentJson`：

```js
      this.messages = (data || []).map((m) => ({
        role: m.messageType === "USER" ? "user" : m.messageType === "ASSISTANT" ? "assistant" : "system",
        content: m.content || "",
        attachments: parseAttachments(m.attachmentJson),
      }));
```

在 `<script setup>` 外部（文件底部）加辅助函数：

```js
function parseAttachments(json) {
  if (!json) return [];
  try {
    const arr = JSON.parse(json);
    return Array.isArray(arr) ? arr : [];
  } catch {
    return [];
  }
}
```

- [ ] **步骤 3：ChatView 透传附件**

把 `handleSend` 改为：

```js
function handleSend(text, deepThink, attachments = []) {
  if ((!text || !text.trim()) && !attachments.length) return;
  if (store.streaming) return;
  store.sendQuestion((text || "").trim(), deepThink, attachments);
  nextTick(() => scrollToBottom());
}
```

- [ ] **步骤 4：构建验证**

运行：`cd web-client && npm run build`
预期：构建成功，无报错。

- [ ] **步骤 5：Commit**

```bash
git add web-client/src/services/chat.js web-client/src/stores/chat.js web-client/src/views/ChatView.vue
git commit -m "feat: 前端发送携带附件并解析历史附件"
```

---

## 任务 9：前端选择/预览/上传 UI 与消息渲染

**文件：**
- 修改：`web-client/src/components/chat/Composer.vue`
- 修改：`web-client/src/components/chat/ChatMessage.vue`

- [ ] **步骤 1：Composer 加附件选择与发送时上传**

把 `web-client/src/components/chat/Composer.vue` 的 `<template>` 替换为：

```vue
<template>
  <div class="composer-wrap">
    <div class="composer">
      <div v-if="files.length" class="attach-preview">
        <div v-for="(f, i) in files" :key="i" class="attach-chip">
          <img v-if="f.type === 'IMAGE'" :src="f.preview" class="attach-thumb" alt="" />
          <span v-else class="attach-audio-name">🎙 {{ f.file.name }}</span>
          <button type="button" class="attach-remove" @click="removeFile(i)">✕</button>
        </div>
      </div>
      <textarea
        class="composer-textarea"
        rows="3"
        placeholder="输入需要深度分析的问题..."
        v-model="text"
        @keydown="handleKeydown"
      ></textarea>
      <div class="composer-toolbar">
        <button type="button" class="pill-deep" :aria-pressed="deepThink" @click="deepThink = !deepThink">
          <span class="pill-dot" aria-hidden="true"></span>
          <span class="pill-icon" aria-hidden="true">◎</span>
          深度思考
        </button>
        <button type="button" class="btn-attach" @click="triggerPick" title="上传图片/音频">📎</button>
        <input
          ref="fileInput"
          type="file"
          accept="image/*,audio/*"
          multiple
          style="display:none"
          @change="onPick"
        />
        <button type="button" class="btn-send" :disabled="(!text.trim() && !files.length) || store.streaming || uploading" @click="send">
          <span aria-hidden="true">{{ uploading ? "…" : "➤" }}</span>
        </button>
      </div>
    </div>
    <p class="composer-hint">Enter 发送 · Shift + Enter 换行 · 支持图片/音频（单文件≤20MB，最多4个）</p>
  </div>
</template>
```

- [ ] **步骤 2：Composer 脚本**

把 `<script setup>` 替换为：

```vue
<script setup>
import { ref } from "vue";
import { useChatStore } from "../../stores/chat";
import { uploadAttachment } from "../../services/chat";

const emit = defineEmits(["send"]);
const store = useChatStore();
const text = ref("");
const deepThink = ref(true);
const files = ref([]);          // { file, type, preview }
const uploading = ref(false);
const fileInput = ref(null);

const MAX_SIZE = 20 * 1024 * 1024;
const MAX_COUNT = 4;
const IMAGE_RE = /^image\/(jpeg|png|webp|gif)$/;
const AUDIO_RE = /^audio\/(mpeg|wav|x-wav|mp4|webm|ogg)$/;

function triggerPick() {
  fileInput.value?.click();
}

function onPick(e) {
  const picked = Array.from(e.target.files || []);
  e.target.value = "";
  for (const file of picked) {
    if (files.value.length >= MAX_COUNT) {
      alert(`最多 ${MAX_COUNT} 个附件`);
      break;
    }
    const type = IMAGE_RE.test(file.type) ? "IMAGE" : AUDIO_RE.test(file.type) ? "AUDIO" : null;
    if (!type) {
      alert(`不支持的文件类型: ${file.name}`);
      continue;
    }
    if (file.size > MAX_SIZE) {
      alert(`文件过大（>20MB）: ${file.name}`);
      continue;
    }
    files.value.push({ file, type, preview: type === "IMAGE" ? URL.createObjectURL(file) : "" });
  }
}

function removeFile(i) {
  const f = files.value[i];
  if (f?.preview) URL.revokeObjectURL(f.preview);
  files.value.splice(i, 1);
}

function handleKeydown(e) {
  if (e.key === "Enter" && !e.shiftKey) {
    e.preventDefault();
    send();
  }
}

async function send() {
  const q = text.value.trim();
  if ((!q && !files.value.length) || store.streaming || uploading.value) return;
  uploading.value = true;
  try {
    const attachments = await Promise.all(
      files.value.map((f) => uploadAttachment(f.file, store.conversationId))
    );
    emit("send", q, deepThink.value, attachments);
    files.value.forEach((f) => f.preview && URL.revokeObjectURL(f.preview));
    files.value = [];
    text.value = "";
  } catch (err) {
    alert(err?.message || "附件上传失败，请重试");
  } finally {
    uploading.value = false;
  }
}
</script>
```

- [ ] **步骤 3：ChatMessage 渲染附件**

把 `web-client/src/components/chat/ChatMessage.vue` 的 `<div class="msg-bubble" ...>` 之前插入附件区块（即放在 `<!-- Answer content -->` 注释之前）：

```vue
    <!-- Attachments (user messages) -->
    <div v-if="msg.attachments && msg.attachments.length" class="msg-attachments">
      <template v-for="(a, i) in msg.attachments" :key="i">
        <a v-if="a.type === 'IMAGE'" :href="a.url" target="_blank" rel="noopener">
          <img :src="a.url" class="msg-attach-image" alt="" />
        </a>
        <audio v-else-if="a.type === 'AUDIO'" :src="a.url" controls class="msg-attach-audio"></audio>
      </template>
    </div>
```

- [ ] **步骤 4：加最简样式**

在 `web-client/src/components/chat/ChatMessage.vue` 末尾追加 `<style scoped>`：

```vue
<style scoped>
.msg-attachments { display: flex; flex-wrap: wrap; gap: 8px; margin: 6px 0; }
.msg-attach-image { max-width: 200px; max-height: 200px; border-radius: 8px; display: block; }
.msg-attach-audio { width: 260px; }
</style>
```

在 `Composer.vue` 末尾追加 `<style scoped>`：

```vue
<style scoped>
.attach-preview { display: flex; flex-wrap: wrap; gap: 8px; padding: 8px 0; }
.attach-chip { position: relative; display: flex; align-items: center; gap: 6px; background: rgba(0,0,0,.06); border-radius: 8px; padding: 4px 8px; }
.attach-thumb { width: 48px; height: 48px; object-fit: cover; border-radius: 6px; }
.attach-remove { border: none; background: transparent; cursor: pointer; font-size: 12px; }
.btn-attach { border: none; background: transparent; cursor: pointer; font-size: 18px; }
</style>
```

- [ ] **步骤 5：构建验证**

运行：`cd web-client && npm run build`
预期：构建成功。

- [ ] **步骤 6：Commit**

```bash
git add web-client/src/components/chat/Composer.vue web-client/src/components/chat/ChatMessage.vue
git commit -m "feat: 聊天框支持选择/预览/上传附件并渲染历史附件"
```

---

## 任务 10：端到端手动验证

**文件：** 无（仅验证）

- [ ] **步骤 1：启动前后端**

后端：`JAVA_HOME=C:/Users/27978/.jdks/ms-21.0.10 mvn -o spring-boot:run`（需 `BAILIAN_API_KEY`、`OSS_*` 环境变量）。
前端：`cd web-client && npm run dev`（端口 5000）。

- [ ] **步骤 2：逐场景验证**

1. 纯文本（简单 + 深度思考）——行为与改造前一致。
2. 纯图片 + 文字——回答体现对图片的理解（`DashScopeChatOptions.model=qwen-vl-max` 生效）。
3. 纯音频 + 文字——答案含转写内容；后端日志出现 `[语音转写]`。
4. 图片 + 音频 + 文字同轮——图片理解 + 音频转写文本，均体现在回答中。
5. 越界文件（大于 20MB / pdf / 第 5 个附件）——前端 alert 拦截或后端返回错误。
6. 刷新页面后进入该会话——历史消息下正确显示图片缩略图与音频播放器。

- [ ] **步骤 3：记录结果**

任一场景失败：回看对应任务的验证步骤定位（上传→任务 3；模型路由→任务 5/6；持久化→任务 7；前端→任务 8/9）。

---

## 已知限制（有意保留）

- **非深度思考路径不落库**：既有实现中简单路径不经过 `PersistMemoryAdvisor`，因此该路径的附件（与文本一样）不会进入会话历史。本计划不改变此行为。
- **历史不重发图片**：还原历史时只送文本（音频转写文本在 `content` 中），以控制 token 与延迟。
- **OSS 桶需公共读**：直链被刷流量有成本风险；后续可升级为私有桶 + 签名 URL（`ChatAttachmentDTO.url` 抽象已预留）。
