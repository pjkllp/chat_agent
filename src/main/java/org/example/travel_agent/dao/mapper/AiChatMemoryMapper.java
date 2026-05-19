package org.example.travel_agent.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.travel_agent.dao.entity.AiChatMemoryEntity;
import org.example.travel_agent.dto.ConversationSummary;

import java.time.OffsetDateTime;
import java.util.List;

@Mapper
public interface AiChatMemoryMapper extends BaseMapper<AiChatMemoryEntity> {

    @Select("SELECT conversation_id, user_id, MIN(create_time) AS first_msg_time, "
            + "MAX(create_time) AS last_msg_time, COUNT(*) AS msg_count "
            + "FROM t_ai_chat_memory "
            + "WHERE user_id = #{userId} AND create_time BETWEEN #{startTime} AND #{endTime} "
            + "GROUP BY conversation_id, user_id "
            + "ORDER BY MAX(create_time) DESC")
    List<ConversationSummary> selectConversationsByUser(@Param("userId") Long userId,
                                                        @Param("startTime") OffsetDateTime startTime,
                                                        @Param("endTime") OffsetDateTime endTime);

    @Select("SELECT conversation_id, user_id, MIN(create_time) AS first_msg_time, "
            + "MAX(create_time) AS last_msg_time, COUNT(*) AS msg_count "
            + "FROM t_ai_chat_memory "
            + "WHERE user_id = #{userId} AND create_time < #{beforeTime} "
            + "GROUP BY conversation_id, user_id "
            + "ORDER BY MAX(create_time) DESC "
            + "LIMIT #{limit}")
    List<ConversationSummary> selectConversationsByUserBefore(@Param("userId") Long userId,
                                                              @Param("beforeTime") OffsetDateTime beforeTime,
                                                              @Param("limit") int limit);

    @Delete("DELETE FROM t_ai_chat_memory WHERE user_id = #{userId} AND conversation_id = #{conversationId}")
    int deleteByConversationId(@Param("userId") Long userId,
                               @Param("conversationId") String conversationId);


    @Select("""
select id, conversation_id, user_id, role, content, create_time
from t_ai_chat_memory
where userId=#{userId} and conversation_id=#{conversationId}
order by create_time DESC
""")
    List<AiChatMemoryEntity> listByUserAndConversation(long userId, String conversationId);

    @Select("SELECT DISTINCT conversation_id FROM t_ai_chat_memory WHERE user_id = #{userId} ORDER BY conversation_id")
    List<String> selectConversationIdsByUserId(@Param("userId") Long userId);
}
