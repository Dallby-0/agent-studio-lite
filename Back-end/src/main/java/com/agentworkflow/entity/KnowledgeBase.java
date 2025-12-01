package com.agentworkflow.entity;

import java.util.Date;
import lombok.Data;

/**
 * pgvector 知识库元信息
 */
@Data
public class KnowledgeBase {

    private Long id;
    private String name;
    private String description;
    private Date createdAt;
}

