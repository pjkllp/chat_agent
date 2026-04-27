package org.example.travel_agent.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 文档表：文件上传与状态机。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_kb_document")
public class KbDocumentEntity {

    /**
     * 文档ID（UUID）。
     */
    private String id;

    /**
     * 所属知识库ID。
     */
    @TableField("kb_id")
    private String kbId;

    /**
     * 原始文件名（展示用）。
     */
    @TableField("file_name")
    private String fileName;

    /**
     * RustFS桶名。
     */
    private String bucket;

    /**
     * RustFS对象键（下载/删除靠它）。
     */
    @TableField("object_key")
    private String objectKey;

    /**
     * 文档状态。
     * INIT / CHUNKING / CHUNKED / EMBEDDING / VECTORIZED / FAILED
     */
    private String status;

    /**
     * 文档是否启用。
     */
    private Boolean enabled;

    /**
     * 版本号（重传整文时+1）。
     */
    private Integer version;

    /**
     * 失败原因（可选）。
     */
    @TableField("error_message")
    private String errorMessage;

    /**
     * 创建时间。
     */
    @TableField("created_at")
    private OffsetDateTime createdAt;

    /**
     * 更新时间。
     */
    @TableField("updated_at")
    private OffsetDateTime updatedAt;
}
