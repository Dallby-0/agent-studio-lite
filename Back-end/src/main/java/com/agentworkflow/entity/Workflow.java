package com.agentworkflow.entity;

import java.io.Serializable;
import java.util.Date;

import lombok.Data;

@Data
public class Workflow implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String description;
    private String version;
    private Integer status;
    private Long createdBy;
    private Date createdAt;
    private Date updatedAt;
    private Integer isDeleted;
    private String definition; // 存储完整工作流定义的JSON字符串
}