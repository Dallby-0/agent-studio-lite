package com.agentworkflow.service.impl;

import com.agentworkflow.entity.WorkflowInstance;
import com.agentworkflow.entity.WorkflowExecutionLog;
import com.agentworkflow.mapper.WorkflowMapper;
import com.agentworkflow.service.WorkflowInstanceService;
import com.agentworkflow.service.WorkflowEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Date;

@Service
public class WorkflowInstanceServiceImpl implements WorkflowInstanceService {

    @Autowired
    private WorkflowMapper workflowMapper;

    @Autowired
    private WorkflowEngine workflowEngine;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<WorkflowInstance> getAllInstances() {
        return workflowMapper.getAllInstances();
    }

    @Override
    public List<WorkflowInstance> getInstancesByWorkflowId(Long workflowId) {
        return workflowMapper.getInstancesByWorkflowId(workflowId);
    }

    @Override
    public WorkflowInstance getInstanceById(Long id) {
        return workflowMapper.getInstanceById(id);
    }

    @Override
    public WorkflowInstance createInstance(WorkflowInstance instance) {
        if (instance.getStatus() == null) {
            instance.setStatus("pending");
        }
        if (instance.getCreatedAt() == null) {
            instance.setCreatedAt(new Date());
        }
        if (instance.getUpdatedAt() == null) {
            instance.setUpdatedAt(new Date());
        }
        workflowMapper.insertInstance(instance);
        return instance;
    }

    @Override
    public WorkflowInstance updateInstance(WorkflowInstance instance) {
        instance.setUpdatedAt(new Date());
        workflowMapper.updateInstance(instance);
        return instance;
    }

    @Override
    public WorkflowInstance startWorkflow(Long workflowId, Map<String, Object> inputParams) {
        return workflowEngine.executeWorkflow(workflowId, inputParams);
    }

    @Override
    public List<WorkflowExecutionLog> getExecutionLogsByInstanceId(Long instanceId) {
        return workflowMapper.getExecutionLogsByInstanceId(instanceId);
    }
}
