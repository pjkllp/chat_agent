# 聊天附件（图片 / 音频）输入能力 — 设计规格

日期：2026-09-11
状态：待评审

## 1. 背景与目标

当前 `/api/chat/chat` 只接受纯文本 `question`，模型只走 qwen-plus。需要让用户能在聊天中传入图片与音频，并让模型**真正理解内容**（多模态），而不是仅做文件存储或占位提示。

**目标**
- 用户可在聊天框上传图片、音频，随消息一起发送。
- 图片走视觉模型（qwen-vl）理解；音频经 ASR 转写后并入文本供模型推理。
- 附件持久化到会话历史，重进会话时可还原展示。
- 模型侧通过**公网直链 URL** 获取文件，不通过后端转发文件本体。

**非目标**
- 不做文档（pdf/docx）上传（知识库已有该能力，且走 RustFS）。
- 不做视频输入。
- 不改造检索/总结链路对附件的语义理解（附件只作用于最终答案生成与简单对话路径）。

## 2. 关键决策（已与用户确认）

| 维度 | 决策 |
|---|---|
| 理解方式 | 多模态理解，非仅存储 |
| 模型接入 | 混合接入：图片→qwen-vl（`Media` 直链）；音频→ASR 转写并入文本；纯文本→qwen-plus |
| 传输方式 | 后端向模型传**公网直链 URL**，不传文件字节 |
| 对象存储 | 阿里云 OSS（`aliyun-sdk-oss`），公网可读桶；与知识库的 RustFS 隔离 |
| 持久化 | 附件持久化到会话历史；用户消息中**只带 OSS 链接与元信息，不含文件本体** |

## 3. 架构与组件

新增/改动组件：

```
前端 Composer.vue ──上传──> POST /api/chat/attachment/upload ──> AliyunOssStorageService ──> OSS(公网桶)
        │                                                                                     │
        └──发消息(携带 attachments:[url...])──> POST /api/chat/chat ──> ChatServiceImpl / answer_node
                                                                                  │
                                                        ┌─────────────────────┼─────────────────────┐
                                                     有图片                有音频                 纯文本
                                                  visionChatClient     ASR 转写→文本          chatClient
                                                  (qwen-vl + Media)    (DashScope ASR)        (qwen-plus)
```

**新增组件**
- `AliyunOssProperties`：endpoint / bucket / accessKeyId / accessKeySecret / publicHost。
- `AliyunOssStorageService`：上传单文件 → 返回公网 URL；只服务聊天附件，不复用 RustFS 的 bucket 语义。
- `AliyunOssConfig`：构造 `OSS` 客户端 bean。
- `ChatAttachmentController`：`POST /api/chat/attachment/upload`（multipart 单文件）。
- `ChatAttachmentDTO`：`url / type(IMAGE|AUDIO) / fileName / mimeType / size`。
- `ChatPromptBuilder`：根据附件类型拼装 `UserMessage`（含 `Media`）或转写文本；供简单路径与 `answer_node` 共用。
- `ChatClientConfig#visionChatClient`：qwen-vl-max 的 `ChatClient` bean。

**改动组件**
- `pom.xml`：新增 `com.aliyun.oss:aliyun-sdk-oss`。
- `application.yml`：新增 `aliyun.oss.*` 配置段。
- `ChatRequest`：新增 `List<ChatAttachmentDTO> attachments`。
- `ChatServiceImpl`：简单路径按附件路由模型。
- `answer_node`：深度思考路径按附件路由模型。
- `t_ai_chat_memory` 表：新增 `attachment_json` 列（text，存 JSON 数组）。
- `AiChatMemoryEntity` / `MessageConvertUtil` / `ConversationService.getMessages`：读写附件元信息。
- 前端：`Composer.vue`、`stores/chat.js`、`ChatView.vue`、消息渲染组件。

## 4. 数据模型

`t_ai_chat_memory` 新增一列：

```sql
ALTER TABLE t_ai_chat_memory ADD COLUMN attachment_json text;
```

- `content`：保持为消息文本。音频消息在此追加 ASR 转写文本（便于历史回放时模型可读）。
- `attachment_json`：JSON 数组，仅链接与元信息：

```json
[{"url":"https://<bucket>.<host>/chat/<convId>/<snowflake>.jpg",
  "type":"IMAGE","fileName":"a.jpg","mimeType":"image/jpeg","size":20480}]
```

- 不含文件字节，符合"只带 OSS 链接"的要求。

## 5. 接口

### 5.1 上传附件
`POST /api/chat/attachment/upload`，`multipart/form-data`，字段 `file`。

- 校验：按 **Tika 魔数**判定真实类型（不信扩展名）；
  - 图片：jpeg / png / webp / gif
  - 音频：mp3 / wav / m4a / webm / ogg
- 限制：单文件 ≤ 20MB；类型不合法 → 返回错误。
- 返回：`Result<ChatAttachmentDTO>`（含公网 url）。
- 对象键：`chat/{conversationId}/{snowflake}.{ext}`。

### 5.2 聊天请求
`ChatRequest` 扩展：

```java
private String question;
private String conversationId;
private int isDeepThink;
private List<ChatAttachmentDTO> attachments; // 可为空
```

类型校验只在上传时一次性完成（按魔数）；聊天请求**信任上传结果，不再次下载校验**。仅校验 `attachments.size() ≤ 4`，超出拒绝。

## 6. 模型路由（混合接入）

`ChatPromptBuilder` 统一逻辑：

- **含图片**：用 `visionChatClient`，构造 `UserMessage.builder().text(question).media(Media.builder().mimeType(m).data(URI.create(url)).build()...).build()`。用户文本作为 text。
- **含音频**：调 DashScope ASR 对公网 URL 转写；转写文本追加进 `content`（并写库），作为普通文本随问题发送，使用 `chatClient`/`visionChatClient`（若同时有图片）。转写结果通过 SSE `workflow` 事件回传前端展示（"🎙 语音转写：…"）。
- **纯文本**：沿用现有 qwen-plus 流程。
- **图片+音频混传**：音频先转写并入文本，再与图片一起送 vision 模型。

## 7. 深度思考管线透传

`original_question` 现为 String 经 graph state 传递。新增 state 字段 `attachments`（`List<ChatAttachmentDTO>`）。

- `rewrite_node` / `intent_identify` / 检索节点：只消费文本；音频转写文本已并入 `question`，图片在文本中以 `[图片x1]` 占位，保证链路可降级运行。
- `answer_node`：读取 `attachments`，走 `ChatPromptBuilder` 决定 vision 或普通模型；`Media` 注入即取即用，不落库字节。

## 8. 持久化与历史回放

- 写入：`PersistMemoryAdvisor` 两处 `llmMemory.save(...)` 前，`MessageConvertUtil.toEntities` 需把当前用户消息的 `attachments` 写入 `attachment_json`。附件来源从 `ChatClientRequest.context()` 透传（与 `conversationId`/`userId` 同路）。
- 读取：`getMessages` 返回 `AiChatMemoryEntity`，新增字段 `attachmentJson` 自动序列化；前端 `chat.js#loadMessages` 解析为附件列表用于渲染。
- **历史送模型**：仍只送文本（音频转写已在 `content` 中；图片以占位符体现），不每轮重发图片，控制 token 与延迟。

## 9. 前端

- `Composer.vue`：
  - 新增 📎 按钮 + 隐藏 `<input type="file" accept="image/*,audio/*" multiple>`。
  - 选中即上传，展示可删除的 chip（图片缩略图 / 音频文件名）。
  - `emit("send", text, deepThink, attachments)`。
- `stores/chat.js`：`sendQuestion(question, deepThink, attachments)`；SSE body 带 `attachments`；用户气泡乐观渲染附件；`loadMessages` 解析 `attachmentJson`。
- `ChatView.vue`：透传 attachments。
- 消息渲染：用户气泡渲染图片缩略图（点击看原图）与 `<audio controls>` 播放器。

## 10. 边界与错误处理

- 上传失败 / 超限 / 类型不符：前端 toast，禁止发送。
- ASR 失败：降级为提示文本（"语音转写失败"），不阻断其余内容。
- OSS 上传成功但 URL 后续失效：历史渲染降级为纯文本链接，模型历史本就只用文本，不受影响。
- 切换会话 / 新建会话导致的上传中断：沿用现有 `ClientDisconnectedException` 静默语义，不报系统错误。

## 11. 配置

```yaml
aliyun:
  oss:
    endpoint: ${OSS_ENDPOINT:}
    bucket: ${OSS_BUCKET:}
    access-key-id: ${OSS_ACCESS_KEY_ID:}
    access-key-secret: ${OSS_ACCESS_KEY_SECRET:}
    public-host: ${OSS_PUBLIC_HOST:}   # 公网访问域名，用于拼接直链
```

桶需设为公共读（或经 CDN），保证 DashScope 侧能拉取直链。

## 12. 测试计划

- 后端单测：`ChatPromptBuilder` 按附件类型选择模型/构造消息；Tika 类型校验拒绝非法文件。
- 接口测试：上传返回可公网访问的 URL；超 4 个附件被拒；聊天请求携带附件正常返回 SSE。
- 前端：上传预览、删除、发送；历史会话还原附件展示。
- 端到端：纯图、纯音频、图+音频、越界文件各跑一遍。

## 13. 风险与待定

- OSS 桶公共读 + 直链存在被刷流量风险；后续可升级为**前端签名直传 + 私有桶 + 临时签名 URL**（本设计已预留 `ChatAttachmentDTO.url` 抽象）。
- qwen-vl 与 qwen-plus 的 `ChatModel` 参数覆盖方式需在实现时确认（同一 `DashScopeChatModel` 换 model 名 vs 独立 bean）。
- `attachment_json` 用 text 存储；若后续需要按附件检索再迁移为 jsonb。
