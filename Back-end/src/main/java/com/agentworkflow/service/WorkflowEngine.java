package com.agentworkflow.service;

import com.agentworkflow.entity.WorkflowInstance;
import com.agentworkflow.entity.WorkflowExecutionLog;

import java.util.Map;

public interface WorkflowEngine {
    /**
     * 执行工作流
     * @param workflowId 工作流ID
     * @param inputParams 输入参数
     * @return 工作流实例
     */
    WorkflowInstance executeWorkflow(Long workflowId, Map<String, Object> inputParams);
    
    /**
     * 执行工作流实例
     * @param instanceId 工作流实例ID
     * @return 工作流实例
     */
    WorkflowInstance executeWorkflowInstance(Long instanceId);
    
    /**
     * 继续执行工作流实例
     * @param instanceId 工作流实例ID
     * @param nodeId 当前节点ID
     * @param context 当前上下文
     * @return 工作流实例
     */
    WorkflowInstance continueExecution(Long instanceId, Long nodeId, Map<String, Object> context);
    
    /**
     * 取消工作流实例
     * @param instanceId 工作流实例ID
     * @return 工作流实例
     */
    WorkflowInstance cancelWorkflow(Long instanceId);
}
