package com.agentworkflow.service.impl;

import com.agentworkflow.entity.WorkflowInstance;
import com.agentworkflow.entity.WorkflowExecutionLog;
import com.agentworkflow.service.WorkflowInstanceService;
import com.agentworkflow.service.WorkflowEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Date;

@Service
public class WorkflowInstanceServiceImpl implements WorkflowInstanceService {

    @Autowired
    private WorkflowEngine workflowEngine;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<WorkflowInstance> getAllInstances() {
        // 暂时返回空列表，因为我们已经迁移到新的状态机工作流
        return new ArrayList<>();
    }

    @Override
    public List<WorkflowInstance> getInstancesByWorkflowId(Long workflowId) {
        // 暂时返回空列表，因为我们已经迁移到新的状态机工作流
        return new ArrayList<>();
    }

    @Override
    public WorkflowInstance getInstanceById(Long id) {
        // 暂时返回null，因为我们已经迁移到新的状态机工作流
        return null;
    }

    @Override
    public WorkflowInstance createInstance(WorkflowInstance instance) {
        // 暂时不支持创建旧版工作流实例
        return null;
    }

    @Override
    public WorkflowInstance updateInstance(WorkflowInstance instance) {
        // 暂时不支持更新旧版工作流实例
        return null;
    }

    @Override
    public WorkflowInstance startWorkflow(Long workflowId, Map<String, Object> inputParams) {
        return workflowEngine.executeWorkflow(workflowId, inputParams);
    }

    @Override
    public List<WorkflowExecutionLog> getExecutionLogsByInstanceId(Long instanceId) {
        // 暂时返回空列表，因为我们已经迁移到新的状态机工作流
        return new ArrayList<>();
    }

    @Override
    public Map<String, Object> getStatus(Long instanceId) {
        // 暂时返回空状态，因为我们已经迁移到新的状态机工作流
        return new java.util.HashMap<>();
    }

    @Override
    public Map<String, Object> getResult(Long instanceId) {
        // 暂时返回空结果，因为我们已经迁移到新的状态机工作流
        return new java.util.HashMap<>();
    }
}
