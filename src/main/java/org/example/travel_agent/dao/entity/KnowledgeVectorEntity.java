package org.example.travel_agent.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@TableName("t_knowledge_base")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class KnowledgeVectorEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String content;

    /**
     * pgvector 列，实体中按字符串承接即可。
     */
    private String embedding;

    /**
     * 相似度查询时映射的计算列，不落库。
     */
    @TableField(exist = false)
    private Double distance;
}
