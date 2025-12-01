package com.agentworkflow.service;

/**
 * RAG 检索服务：根据 Agent 绑定的知识库和用户问题构造上下文
 */
public interface RetrievalService {

    /**
     * 为指定 Agent 和当前问题构建知识库上下文字符串
     */
    String buildContextForAgent(Long agentId, String question);
}

