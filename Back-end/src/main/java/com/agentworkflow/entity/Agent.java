package com.agentworkflow.entity;

import java.io.Serializable;
import java.util.Date;

import lombok.Data;

@Data
public class Agent implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String description;
    private String type;  // 改为与数据库表匹配的字段名
    private String apiConfig;  // API配置信息
    private String systemPrompt;  // 系统提示词
    private String pluginsJson;  // 插件配置（JSON格式）
    private String configJson;  // 配置信息（为后续扩展预留）
    private Integer status;
    private Long createdBy;  // 添加创建者ID字段
    private Date createdAt;
    private Date updatedAt;
    private Integer isDeleted;

    // 为了兼容前端，添加getter/setter方法
    public String getAgentType() {
        return type;
    }

    public void setAgentType(String agentType) {
        this.type = agentType;
    }
}