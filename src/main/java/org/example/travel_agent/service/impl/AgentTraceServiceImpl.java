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
import org.example.travel_agent.service.AgentTraceService;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentTraceServiceImpl extends ServiceImpl<AgentTraceMapper, AgentTraceEntity> implements AgentTraceService {

    @Override
    public void recordStart(String conversationId, Long userId, String nodeName) {
        AgentTraceEntity entity = AgentTraceEntity.builder()
                .conversationId(conversationId)
                .userId(userId)
                .nodeName(nodeName)
                .status("START")
                .startTime(OffsetDateTime.now())
                .createTime(OffsetDateTime.now())
                .build();
        this.save(entity);
    }

    @Override
    public void recordFinish(String conversationId, String nodeName, String resultData) {
        List<AgentTraceEntity> existing = this.list(
                Wrappers.<AgentTraceEntity>lambdaQuery()
                        .eq(AgentTraceEntity::getConversationId, conversationId)
                        .eq(AgentTraceEntity::getNodeName, nodeName)
                        .eq(AgentTraceEntity::getStatus, "START")
                        .orderByDesc(AgentTraceEntity::getStartTime)
                        .last("LIMIT 1")
        );
        if (existing.isEmpty()) {
            log.warn("recordFinish: no START record found for conversationId={}, nodeName={}",
                    conversationId, nodeName);
            return;
        }
        AgentTraceEntity entity = existing.get(0);
        OffsetDateTime endTime = OffsetDateTime.now();
        long duration = java.time.Duration.between(entity.getStartTime(), endTime).toMillis();
        entity.setEndTime(endTime);
        entity.setDuration(duration);
        entity.setStatus("FINISH");
        entity.setResultData(resultData);
        this.updateById(entity);
    }

    @Override
    public void recordError(String conversationId, String nodeName, String errorMessage) {
        List<AgentTraceEntity> existing = this.list(
                Wrappers.<AgentTraceEntity>lambdaQuery()
                        .eq(AgentTraceEntity::getConversationId, conversationId)
                        .eq(AgentTraceEntity::getNodeName, nodeName)
                        .eq(AgentTraceEntity::getStatus, "START")
                        .orderByDesc(AgentTraceEntity::getStartTime)
                        .last("LIMIT 1")
        );
        if (existing.isEmpty()) {
            AgentTraceEntity entity = AgentTraceEntity.builder()
                    .conversationId(conversationId)
                    .nodeName(nodeName)
                    .status("ERROR")
                    .startTime(OffsetDateTime.now())
                    .endTime(OffsetDateTime.now())
                    .duration(0L)
                    .errorMessage(errorMessage)
                    .createTime(OffsetDateTime.now())
                    .build();
            this.save(entity);
        } else {
            AgentTraceEntity entity = existing.get(0);
            OffsetDateTime endTime = OffsetDateTime.now();
            long duration = java.time.Duration.between(entity.getStartTime(), endTime).toMillis();
            entity.setEndTime(endTime);
            entity.setDuration(duration);
            entity.setStatus("ERROR");
            entity.setErrorMessage(errorMessage);
            this.updateById(entity);
        }
    }

    @Override
    public Page<ConversationTraceVO> listConversations(Long userId, int current, int size) {
        Page<ConversationTraceVO> page = new Page<>(current, size);
        page.setTotal(this.baseMapper.countConversationsByUser(userId));
        List<ConversationTraceVO> records = this.baseMapper.selectConversationsByUser(userId, page);
        page.setRecords(records);
        return page;
    }

    @Override
    public TraceDetailVO getTraceDetail(String conversationId) {
        List<AgentTraceEntity> entities = this.baseMapper.findByConversationId(conversationId);
        List<NodeTraceStep> steps = entities.stream()
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
        return TraceDetailVO.builder()
                .conversationId(conversationId)
                .steps(steps)
                .build();
    }

    @Override
    public TraceStatsVO getTraceStats(Long userId) {
        TraceStatsVO stats = this.baseMapper.selectStatsByUser(userId);
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
}
