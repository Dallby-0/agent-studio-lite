package com.agentworkflow.entity;

import java.io.Serializable;
import java.util.Date;

import lombok.Data;

@Data
public class WorkflowNode implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long workflowId;
    private String name;
    private String type;
    private String configJson;
    private Integer positionX;
    private Integer positionY;
    private Date createdAt;
    private Date updatedAt;
}