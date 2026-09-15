-- message_id：一轮对话的唯一标识。请求入口生成，trace 各节点行共用同一个值，
-- 同时作为该轮 AI 回复在 t_ai_chat_memory 中的主键，从而支持
-- t_agent_trace.message_id = t_ai_chat_memory.id 的关联查询。
ALTER TABLE t_agent_trace ADD COLUMN IF NOT EXISTS message_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_agent_trace_message_id ON t_agent_trace(message_id);

-- recordFinish / recordError 需要按会话 + 轮次 + 节点名精确定位 START 行
CREATE INDEX IF NOT EXISTS idx_agent_trace_conversation_message
    ON t_agent_trace(conversation_id, message_id, node_name);
