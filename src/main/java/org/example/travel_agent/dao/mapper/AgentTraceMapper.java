package org.example.travel_agent.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.travel_agent.dao.entity.AgentTraceEntity;
import org.example.travel_agent.dto.trace.ConversationTraceVO;
import org.example.travel_agent.dto.trace.TraceStatsVO;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;

@Mapper
public interface AgentTraceMapper extends BaseMapper<AgentTraceEntity> {

    @Select("SELECT conversation_id, user_id, COUNT(*) AS node_count, "
            + "MIN(start_time) AS start_time, MAX(end_time) AS end_time, "
            + "SUM(COALESCE(duration, 0)) AS total_duration "
            + "FROM t_agent_trace "
            + "WHERE user_id = #{userId} "
            + "GROUP BY conversation_id, user_id "
            + "ORDER BY MAX(create_time) DESC")
    List<ConversationTraceVO> selectConversationsByUser(@Param("userId") Long userId,
                                                        Page<?> page);

    @Select("SELECT COUNT(*) FROM ("
            + "SELECT conversation_id FROM t_agent_trace "
            + "WHERE user_id = #{userId} GROUP BY conversation_id"
            + ") t")
    Long countConversationsByUser(@Param("userId") Long userId);

    @Select("SELECT * FROM t_agent_trace "
            + "WHERE conversation_id = #{conversationId} "
            + "ORDER BY start_time ASC")
    List<AgentTraceEntity> findByConversationId(@Param("conversationId") String conversationId);

    @Select("SELECT "
            + "COUNT(DISTINCT conversation_id) AS total_conversations, "
            + "COUNT(DISTINCT CASE WHEN status = 'ERROR' THEN conversation_id END) AS error_conversations, "
            + "AVG(CASE WHEN node_name = 'answer_node' THEN duration END) AS avg_duration, "
            + "COUNT(*) AS total_nodes, "
            + "COUNT(CASE WHEN status = 'ERROR' THEN 1 END) AS error_nodes "
            + "FROM t_agent_trace "
            + "WHERE user_id = #{userId}")
    TraceStatsVO selectStatsByUser(@Param("userId") Long userId);
}
