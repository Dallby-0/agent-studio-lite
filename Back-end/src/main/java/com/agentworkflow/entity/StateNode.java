package com.agentworkflow.entity;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

import lombok.Data;

@Data
public class StateNode implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long workflowId;
    private String nodeKey;
    private String name;
    private String type;
    private String configJson;
    private Integer positionX;
    private Integer positionY;
    private Date createdAt;
    private Date updatedAt;
    
    // 非数据库字段，用于内存中存储节点输出关系
    private List<StateTransition> outputs;
}
