package com.agentworkflow.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * pgvector 向量数据库配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "vector-db")
public class VectorDbProperties {

    /**
     * JDBC 连接 URL，例如 jdbc:postgresql://pgvector-test:5432/vector_db
     */
    private String url;

    /**
     * 数据库用户名
     */
    private String username;

    /**
     * 数据库密码
     */
    private String password;
}

