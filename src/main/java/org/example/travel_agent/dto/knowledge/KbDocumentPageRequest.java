package org.example.travel_agent.dto.knowledge;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.travel_agent.dao.entity.KbDocumentEntity;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class KbDocumentPageRequest extends Page<KbDocumentEntity> {

    /**
     * 按知识库ID过滤（可选）。
     */
    private Long kbId;

    /**
     * 按文件名模糊查询（可选）。
     */
    private String fileName;

    /**
     * 按文档状态过滤（可选）。
     */
    private Integer status;

}
