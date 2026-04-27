package org.example.travel_agent.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 知识库表：只管“库”本身。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_knowledge_base")
public class KnowledgeBaseEntity {

    /**
     * 知识库ID（UUID）。
     */
    private String id;

    /**
     * 知识库名称。
     */
    @TableField("kb_name")
    private String kbName;

    /**
     * 知识库描述。
     */
    private String description;

    /**
     * 是否启用。
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
