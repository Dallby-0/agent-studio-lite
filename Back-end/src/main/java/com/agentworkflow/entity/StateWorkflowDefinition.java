package com.agentworkflow.entity;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

import lombok.Data;

@Data
public class StateWorkflowDefinition implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String description;
    private String version;
    private Integer status;
    private Long createdBy;
    private String jsonDefinition = "";
    private Date createdAt;
    private Date updatedAt;
    private Integer isDeleted;
    
    // 非数据库字段，用于内存中存储节点和转换关系
    private List<StateNode> nodes;
    private List<StateTransition> transitions;
    private List<GlobalVariable> globalVariables;
}
