CREATE TABLE IF NOT EXISTS t_agent_trace (
    id              BIGINT PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    conversation_id VARCHAR(64) NOT NULL,
    node_name       VARCHAR(64) NOT NULL,
    status          VARCHAR(16) NOT NULL,
    start_time      TIMESTAMP NOT NULL,
    end_time        TIMESTAMP,
    duration        BIGINT,
    result_data     JSONB,
    error_message   TEXT,
    create_time     TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_agent_trace_user_conv ON t_agent_trace(user_id, conversation_id, create_time);
CREATE INDEX IF NOT EXISTS idx_agent_trace_conv ON t_agent_trace(conversation_id);
