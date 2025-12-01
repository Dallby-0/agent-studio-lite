package com.agentworkflow.service.impl;

import com.agentworkflow.entity.AgentKnowledge;
import com.agentworkflow.service.AgentService;
import com.agentworkflow.service.EmbeddingService;
import com.agentworkflow.service.RetrievalService;
import com.agentworkflow.service.VectorStoreService;
import com.agentworkflow.service.VectorStoreService.SimilarChunk;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * RAG 检索实现：根据 Agent 绑定的知识库和当前问题，构建可供模型参考的上下文
 */
@Service
public class RetrievalServiceImpl implements RetrievalService {

    @Autowired
    private AgentService agentService;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private VectorStoreService vectorStoreService;

    @Override
    public String buildContextForAgent(Long agentId, String question) {
        if (question == null || question.trim().isEmpty()) {
            return "";
        }

        List<AgentKnowledge> bindings = agentService.getAgentKnowledge(agentId);
        if (bindings == null || bindings.isEmpty()) {
            return "";
        }

        float[] queryEmbedding = embeddingService.embed(question);
        if (queryEmbedding == null || queryEmbedding.length == 0) {
            return "";
        }

        List<SimilarChunk> allChunks = new ArrayList<>();
        for (AgentKnowledge ak : bindings) {
            Long kbId = ak.getKnowledgeBaseId();
            if (kbId == null) {
                continue;
            }
            List<SimilarChunk> topK = vectorStoreService.searchTopK(kbId, queryEmbedding, 3);
            if (topK != null && !topK.isEmpty()) {
                allChunks.addAll(topK);
            }
        }

        if (allChunks.isEmpty()) {
            return "";
        }

        // 按距离从小到大排序，取前若干条
        List<SimilarChunk> topChunks = allChunks.stream()
                .sorted(Comparator.comparingDouble(SimilarChunk::getScore))
                .limit(6)
                .collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (SimilarChunk chunk : topChunks) {
            sb.append("【片段 ").append(index++).append("】\n");
            sb.append(chunk.getContent()).append("\n\n");
        }
        return sb.toString().trim();
    }
}

