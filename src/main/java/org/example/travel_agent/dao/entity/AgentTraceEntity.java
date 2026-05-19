package org.example.travel_agent.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@TableName("t_agent_trace")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentTraceEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("conversation_id")
    private String conversationId;

    @TableField("node_name")
    private String nodeName;

    private String status;

    @TableField("start_time")
    private OffsetDateTime startTime;

    @TableField("end_time")
    private OffsetDateTime endTime;

    private Long duration;

    @TableField("result_data")
    private String resultData;

    @TableField("error_message")
    private String errorMessage;

    @TableField("create_time")
    private OffsetDateTime createTime;
}
