package org.example.travel_agent.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.travel_agent.dao.entity.KnowledgeVectorEntity;

import java.util.List;

@Mapper
public interface KnowledgeVectorMapper extends BaseMapper<KnowledgeVectorEntity> {

    @Select("""
            SELECT id, content, embedding,
                   (embedding <=> CAST(#{queryVector} AS vector)) AS distance
            FROM knowledge_base
            ORDER BY embedding <=> CAST(#{queryVector} AS vector)
            LIMIT #{topK}
            """)
    List<KnowledgeVectorEntity> similaritySearch(@Param("queryVector") String queryVector, @Param("topK") int topK);
}
