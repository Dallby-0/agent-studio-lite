package com.agentworkflow.service;

import java.util.List;

/**
 * 文本向量化服务接口，封装对本地 sentence-transformers 服务的调用
 */
public interface EmbeddingService {

    /**
     * 将单条文本转换为向量
     */
    float[] embed(String text);

    /**
     * 将多条文本批量转换为向量
     */
    List<float[]> embedBatch(List<String> texts);
}

