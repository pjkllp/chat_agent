package org.example.travel_agent.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 分块表：解析后切块结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_kb_chunk")
public class KbChunkEntity {

    /**
     * 分块ID（UUID）。
     */
    private String id;

    /**
     * 所属知识库ID。
     */
    @TableField("kb_id")
    private String kbId;

    /**
     * 所属文档ID。
     */
    @TableField("doc_id")
    private String docId;

    /**
     * 文档内块序号。
     */
    @TableField("chunk_no")
    private Integer chunkNo;

    /**
     * 分块文本。
     */
    private String content;

    /**
     * 块是否启用。
     */
    private Boolean enabled;

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
