package com.agentworkflow.service;

import com.agentworkflow.entity.WorkflowInstance;
import com.agentworkflow.entity.WorkflowExecutionLog;

import java.util.List;
import java.util.Map;

public interface WorkflowInstanceService {
    // 工作流实例相关方法
    List<WorkflowInstance> getAllInstances();
    List<WorkflowInstance> getInstancesByWorkflowId(Long workflowId);
    WorkflowInstance getInstanceById(Long id);
    WorkflowInstance createInstance(WorkflowInstance instance);
    WorkflowInstance updateInstance(WorkflowInstance instance);
    WorkflowInstance startWorkflow(Long workflowId, Map<String, Object> inputParams);
    List<WorkflowExecutionLog> getExecutionLogsByInstanceId(Long instanceId);
    Map<String, Object> getStatus(Long instanceId);
    Map<String, Object> getResult(Long instanceId);
}
