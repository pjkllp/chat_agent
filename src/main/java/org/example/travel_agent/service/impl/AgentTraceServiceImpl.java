package org.example.travel_agent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.travel_agent.dao.entity.AgentTraceEntity;
import org.example.travel_agent.dao.mapper.AgentTraceMapper;
import org.example.travel_agent.dto.trace.ConversationTraceVO;
import org.example.travel_agent.dto.trace.NodeTraceStep;
import org.example.travel_agent.dto.trace.TraceDetailVO;
import org.example.travel_agent.dto.trace.TraceStatsVO;
import org.example.travel_agent.dto.trace.TraceTurnVO;
import org.example.travel_agent.service.AgentTraceService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentTraceServiceImpl extends ServiceImpl<AgentTraceMapper, AgentTraceEntity> implements AgentTraceService {

    @Override
    public void recordStart(String conversationId, Long userId, Long messageId, String nodeName) {
        AgentTraceEntity entity = AgentTraceEntity.builder()
                .conversationId(conversationId)
                .userId(userId)
                .messageId(messageId)
                .nodeName(nodeName)
                .status("START")
                .startTime(OffsetDateTime.now())
                .createTime(OffsetDateTime.now())
                .build();
        this.save(entity);
    }

    @Override
    public void recordFinish(String conversationId, Long messageId, String nodeName, String resultData) {
        AgentTraceEntity entity = findPendingStart(conversationId, messageId, nodeName);
        if (entity == null) {
            log.warn("recordFinish: no START record found for conversationId={}, messageId={}, nodeName={}",
                    conversationId, messageId, nodeName);
            return;
        }
        OffsetDateTime endTime = OffsetDateTime.now();
        entity.setEndTime(endTime);
        entity.setDuration(Duration.between(entity.getStartTime(), endTime).toMillis());
        entity.setStatus("FINISH");
        entity.setResultData(resultData);
        this.updateById(entity);
    }

    @Override
    public void recordError(String conversationId, Long userId, Long messageId, String nodeName, String errorMessage) {
        AgentTraceEntity entity = findPendingStart(conversationId, messageId, nodeName);
        if (entity == null) {
            // 节点在 start 落库前就失败了，补一条完整的 ERROR 行
            AgentTraceEntity fallback = AgentTraceEntity.builder()
                    .conversationId(conversationId)
                    .userId(userId)
                    .messageId(messageId)
                    .nodeName(nodeName)
                    .status("ERROR")
                    .startTime(OffsetDateTime.now())
                    .endTime(OffsetDateTime.now())
                    .duration(0L)
                    .errorMessage(errorMessage)
                    .createTime(OffsetDateTime.now())
                    .build();
            this.save(fallback);
            return;
        }
        OffsetDateTime endTime = OffsetDateTime.now();
        entity.setEndTime(endTime);
        entity.setDuration(Duration.between(entity.getStartTime(), endTime).toMillis());
        entity.setStatus("ERROR");
        entity.setErrorMessage(errorMessage);
        this.updateById(entity);
    }

    /**
     * 定位某轮某节点尚未结束的 START 行。带上 messageId 后可精确命中本轮，
     * 不会再误取到同一会话中历史轮次遗留的孤儿 START。
     */
    private AgentTraceEntity findPendingStart(String conversationId, Long messageId, String nodeName) {
        List<AgentTraceEntity> existing = this.list(
                Wrappers.<AgentTraceEntity>lambdaQuery()
                        .eq(AgentTraceEntity::getConversationId, conversationId)
                        .eq(messageId != null, AgentTraceEntity::getMessageId, messageId)
                        .eq(AgentTraceEntity::getNodeName, nodeName)
                        .eq(AgentTraceEntity::getStatus, "START")
                        .orderByDesc(AgentTraceEntity::getStartTime)
                        .last("LIMIT 1")
        );
        return existing.isEmpty() ? null : existing.get(0);
    }

    @Override
    public Page<ConversationTraceVO> listConversations(int current, int size) {
        Page<ConversationTraceVO> page = new Page<>(current, size);
        page.setTotal(this.baseMapper.countConversations());
        List<ConversationTraceVO> records = this.baseMapper.selectConversations(page);
        page.setRecords(records);
        return page;
    }

    @Override
    public TraceDetailVO getTraceDetail(String conversationId) {
        List<AgentTraceEntity> entities = this.baseMapper.findByConversationId(conversationId);

        // findByConversationId 已按 start_time 升序，LinkedHashMap 因此保持轮次的先后顺序。
        // messageId 为 null 的是加字段之前的历史数据，统一并入一个"未知轮次"分组。
        Map<Long, List<AgentTraceEntity>> grouped = new LinkedHashMap<>();
        for (AgentTraceEntity entity : entities) {
            grouped.computeIfAbsent(entity.getMessageId(), key -> new ArrayList<>()).add(entity);
        }

        List<TraceTurnVO> turns = grouped.entrySet().stream()
                .map(entry -> toTurn(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());

        return TraceDetailVO.builder()
                .conversationId(conversationId)
                .totalTurns(turns.size())
                .turns(turns)
                .build();
    }

    private TraceTurnVO toTurn(Long messageId, List<AgentTraceEntity> rows) {
        List<NodeTraceStep> steps = rows.stream()
                .map(e -> NodeTraceStep.builder()
                        .nodeName(e.getNodeName())
                        .status(e.getStatus())
                        .startTime(e.getStartTime())
                        .endTime(e.getEndTime())
                        .duration(e.getDuration())
                        .resultData(e.getResultData())
                        .errorMessage(e.getErrorMessage())
                        .build())
                .collect(Collectors.toList());

        OffsetDateTime startTime = rows.stream()
                .map(AgentTraceEntity::getStartTime)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
        OffsetDateTime endTime = rows.stream()
                .map(AgentTraceEntity::getEndTime)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        long totalDuration = rows.stream()
                .map(AgentTraceEntity::getDuration)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();

        return TraceTurnVO.builder()
                .messageId(messageId)
                .status(resolveTurnStatus(rows))
                .startTime(startTime)
                .endTime(endTime)
                .totalDuration(totalDuration)
                .nodeCount(rows.size())
                .steps(steps)
                .build();
    }

    private String resolveTurnStatus(List<AgentTraceEntity> rows) {
        // 取消优先于 RUNNING：取消后残留的未结束 START 行不应把整轮显示成“进行中”
        if (rows.stream().anyMatch(r -> "CANCEL".equals(r.getStatus()))) {
            return "CANCEL";
        }
        if (rows.stream().anyMatch(r -> "ERROR".equals(r.getStatus()))) {
            return "ERROR";
        }
        boolean hasUnfinished = rows.stream()
                .anyMatch(r -> "START".equals(r.getStatus()) && r.getEndTime() == null);
        return hasUnfinished ? "RUNNING" : "FINISH";
    }

    @Override
    public TraceStatsVO getTraceStats() {
        TraceStatsVO stats = this.baseMapper.selectStats();
        if (stats == null) {
            return TraceStatsVO.builder()
                    .totalConversations(0)
                    .errorConversations(0)
                    .errorRate("0%")
                    .avgDuration(0.0)
                    .totalNodes(0)
                    .errorNodes(0)
                    .build();
        }
        long total = stats.getTotalConversations();
        long errors = stats.getErrorConversations();
        stats.setErrorRate(total > 0 ? String.format("%.1f%%", errors * 100.0 / total) : "0%");
        return stats;
    }

    @Override
    public void recordCancel(String conversationId, Long userId, Long messageId, String node, String safeMessage) {
        AgentTraceEntity entity = findPendingStart(conversationId, messageId, node);
        if (entity == null) {
            // 节点在 start 落库前就被取消了，补一条完整的 CANCEL 行
            AgentTraceEntity fallback = AgentTraceEntity.builder()
                    .conversationId(conversationId)
                    .userId(userId)
                    .messageId(messageId)
                    .nodeName(node)
                    .status("CANCEL")
                    .startTime(OffsetDateTime.now())
                    .endTime(OffsetDateTime.now())
                    .duration(0L)
                    .errorMessage(safeMessage)
                    .createTime(OffsetDateTime.now())
                    .build();
            this.save(fallback);
            return;
        }
        OffsetDateTime endTime = OffsetDateTime.now();
        entity.setEndTime(endTime);
        entity.setDuration(Duration.between(entity.getStartTime(), endTime).toMillis());
        entity.setStatus("CANCEL");
        entity.setErrorMessage(safeMessage);
        this.updateById(entity);
    }
}
