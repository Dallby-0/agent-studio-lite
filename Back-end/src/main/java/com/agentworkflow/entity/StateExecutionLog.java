package com.agentworkflow.entity;

import java.io.Serializable;
import java.util.Date;

import lombok.Data;

@Data
public class StateExecutionLog implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long instanceId;
    private String nodeKey;
    private String nodeType;
    private Long executionTime;
    private String status;
    private String inputData;
    private String outputData;
    private String errorMessage;
    private Date createdAt;
}
