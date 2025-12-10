package com.agentworkflow.service;

import com.agentworkflow.entity.Workflow;

import java.util.List;

public interface WorkflowService {
    // 工作流定义相关方法
    List<Workflow> getAllWorkflows();
    Workflow getWorkflowById(Long id);
    Workflow createWorkflow(Workflow workflow);
    Workflow updateWorkflow(Workflow workflow);
    boolean deleteWorkflow(Long id);
    Workflow getWorkflowDefinition(Long workflowId);
}
