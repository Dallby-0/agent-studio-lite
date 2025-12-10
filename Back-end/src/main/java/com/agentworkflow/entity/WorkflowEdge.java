package com.agentworkflow.entity;

import java.io.Serializable;
import java.util.Date;

import lombok.Data;

@Data
public class WorkflowEdge implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long workflowId;
    private Long fromNodeId;
    private Long toNodeId;
    private String condition;
    private Date createdAt;
    private Date updatedAt;
}