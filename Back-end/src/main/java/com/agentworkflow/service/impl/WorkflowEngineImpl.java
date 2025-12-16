package com.agentworkflow.service.impl;

import com.agentworkflow.entity.WorkflowInstance;
import com.agentworkflow.engine.StateMachineEngine;
import com.agentworkflow.service.WorkflowEngine;
import com.agentworkflow.service.NodeProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Date;
import java.util.stream.Collectors;

@Service
public class WorkflowEngineImpl implements WorkflowEngine {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowEngineImpl.class);

    @Autowired
    private StateMachineEngine stateMachineEngine;

    @Autowired
    private List<NodeProcessor> nodeProcessors;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, NodeProcessor> processorMap;

    // 初始化处理器映射
    public WorkflowEngineImpl() {
        this.processorMap = new HashMap<>();
    }

    // Spring会在所有bean初始化后调用此方法
    @Autowired
    public void initProcessors() {
        for (NodeProcessor processor : nodeProcessors) {
            processorMap.put(processor.getNodeType(), processor);
        }
    }

    @Override
    @Transactional
    public WorkflowInstance executeWorkflow(Long workflowId, Map<String, Object> inputParams) {
        logger.debug("开始执行工作流，ID: {}, 输入参数: {}", workflowId, inputParams);
        
        // 委托给新的状态机引擎执行工作流
        com.agentworkflow.entity.StateWorkflowInstance stateInstance = stateMachineEngine.executeWorkflow(workflowId, inputParams);
        logger.debug("工作流执行完成，实例ID: {}, 状态: {}", stateInstance.getId(), stateInstance.getStatus());
        
        // 转换为旧的WorkflowInstance类型（临时兼容）
        WorkflowInstance instance = new WorkflowInstance();
        instance.setId(stateInstance.getId());
        instance.setWorkflowId(stateInstance.getWorkflowId());
        instance.setName(stateInstance.getName());
        instance.setStatus(stateInstance.getStatus());
        instance.setInputParams(stateInstance.getInputParams());
        instance.setOutputParams(stateInstance.getOutputParams());
        instance.setStartedAt(stateInstance.getStartedAt());
        instance.setFinishedAt(stateInstance.getFinishedAt());
        instance.setCreatedAt(stateInstance.getCreatedAt());
        instance.setUpdatedAt(stateInstance.getUpdatedAt());
        
        return instance;
    }

    @Override
    @Transactional
    public WorkflowInstance executeWorkflowInstance(Long instanceId) {
        logger.debug("开始执行工作流实例，ID: {}", instanceId);
        // 暂时返回空，因为我们已经迁移到新的状态机工作流
        return null;
    }

    @Override
    @Transactional
    public WorkflowInstance continueExecution(Long instanceId, Long nodeId, Map<String, Object> context) {
        logger.debug("继续执行工作流实例，ID: {}, 从节点: {}, 上下文: {}", instanceId, nodeId, context);
        // 暂时返回空，因为我们已经迁移到新的状态机工作流
        return null;
    }

    @Override
    @Transactional
    public WorkflowInstance cancelWorkflow(Long instanceId) {
        // 暂时返回空，因为我们已经迁移到新的状态机工作流
        return null;
    }

    /**
     * 将Map转换为JSON字符串
     */
    private String mapToJsonString(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 将JSON字符串转换为Map
     */
    private Map<String, Object> jsonStringToMap(String jsonString) {
        try {
            if (jsonString == null || jsonString.isEmpty()) {
                return new HashMap<>();
            }
            return objectMapper.readValue(jsonString, Map.class);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
}
