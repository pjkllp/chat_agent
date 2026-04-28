package org.example.travel_agent.dto.knowledge;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.travel_agent.dao.entity.KnowledgeBaseEntity;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeBasePageRequest extends Page<KnowledgeBaseEntity> {

    /**
     * 知识库名称（模糊查询，可选）。
     */
    private String kbName;

    /**
     * 启用状态（可选）。
     */
    private Boolean enabled;
}
