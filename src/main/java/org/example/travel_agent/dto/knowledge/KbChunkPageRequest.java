package org.example.travel_agent.dto.knowledge;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.travel_agent.dao.entity.KbChunkEntity;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KbChunkPageRequest extends Page<KbChunkEntity> {

    /**
     * 所属知识库ID（可选）。
     */
    private Long kbId;

    /**
     * 所属文档ID（建议必传）。
     */
    private Long docId;

    /**
     * 内容模糊搜索（可选）。
     */
    private String content;

    /**
     * 启用状态（可选）。
     */
    private Boolean enabled;
}
