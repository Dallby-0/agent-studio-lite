package com.agentworkflow.entity;

import java.io.Serializable;
import java.util.Date;

import lombok.Data;

@Data
public class WorkflowExecutionLog implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long instanceId;
    private Long nodeId;
    private String nodeType;
    private Double executionTime;
    private String status;
    private String inputData;
    private String outputData;
    private Date createdAt;
}