package com.agentworkflow.config;

import com.agentworkflow.config.properties.VectorDbProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * pgvector 向量数据库 JdbcTemplate 配置（单独 DataSource，不影响 MySQL 默认数据源）
 */
@Configuration
public class VectorDataSourceConfig {

    @Bean(name = "vectorJdbcTemplate")
    public JdbcTemplate vectorJdbcTemplate(VectorDbProperties properties) {
        return new JdbcTemplate(
                DataSourceBuilder.create()
                        .url(properties.getUrl())
                        .username(properties.getUsername())
                        .password(properties.getPassword())
                        .driverClassName("org.postgresql.Driver")
                        .build()
        );
    }
}

