package com.agentworkflow.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 本地 Embedding 服务配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "embedding")
public class EmbeddingProperties {

    /**
     * Embedding 服务基础地址，例如 http://embedding-service:9000
     */
    private String baseUrl;

    /**
     * 使用的模型名称，例如 BAAI/bge-small-zh-v1.5
     */
    private String model;

    /**
     * 向量维度，例如 512
     */
    private int dimension;
}

