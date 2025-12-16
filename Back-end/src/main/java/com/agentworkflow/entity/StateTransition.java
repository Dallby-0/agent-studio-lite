package com.agentworkflow.entity;

import java.io.Serializable;
import java.util.Date;

import lombok.Data;

@Data
public class StateTransition implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long workflowId;
    private String fromNodeKey;
    private String toNodeKey;
    private String conditionExpression;
    private String variableMappings;
    private Date createdAt;
    private Date updatedAt;
}
