package org.example.travel_agent.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 向量表：向量化结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_kb_vector")
public class KbVectorEntity {

    /**
     * 向量记录ID（雪花ID）。
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 所属知识库ID。
     */
    @TableField("kb_id")
    private Long kbId;

    /**
     * 所属文档ID。
     */
    @TableField("doc_id")
    private Long docId;

    /**
     * 对应分块ID。
     */
    @TableField("chunk_id")
    private Long chunkId;

    /**
     * 冗余文本，便于直接召回展示。
     */
    private String content;

    /**
     * 元数据（jsonb），如 docId/chunkId/version。
     */
    private String metadata;

    /**
     * 向量（vector(1024)）。
     * 这里先用 String 承接数据库向量文本表示（如 "[0.1,0.2,...]"）。
     */
    private String embedding;

    /**
     * 向量是否启用。
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
