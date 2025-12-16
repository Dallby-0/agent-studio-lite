package com.agentworkflow.entity;

import java.io.Serializable;
import java.util.Date;

import lombok.Data;

@Data
public class StateWorkflowInstance implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long workflowId;
    private String name;
    private String status;
    private String inputParams;
    private String outputParams;
    private String currentNodeKey;
    private String globalVariables;
    private Date startedAt;
    private Date finishedAt;
    private Date createdAt;
    private Date updatedAt;
}
