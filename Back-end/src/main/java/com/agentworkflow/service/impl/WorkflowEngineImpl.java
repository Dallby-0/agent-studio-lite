package com.agentworkflow.service.impl;

import com.agentworkflow.entity.Workflow;
import com.agentworkflow.entity.WorkflowNode;
import com.agentworkflow.entity.WorkflowEdge;
import com.agentworkflow.entity.WorkflowInstance;
import com.agentworkflow.entity.WorkflowExecutionLog;
import com.agentworkflow.mapper.WorkflowMapper;
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
import java.util.Date;
import java.util.stream.Collectors;

@Service
public class WorkflowEngineImpl implements WorkflowEngine {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowEngineImpl.class);

    @Autowired
    private WorkflowMapper workflowMapper;

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
        
        // 获取工作流定义
        Workflow workflow = workflowMapper.getWorkflowById(workflowId);
        if (workflow == null) {
            logger.error("执行工作流失败，工作流不存在，ID: {}", workflowId);
            throw new IllegalArgumentException("工作流不存在");
        }
        logger.debug("获取到工作流定义: {}", workflow.getName());

        // 创建工作流实例
        WorkflowInstance instance = new WorkflowInstance();
        instance.setWorkflowId(workflowId);
        instance.setName(workflow.getName() + "_" + System.currentTimeMillis());
        instance.setStatus("running");
        instance.setInputParams(mapToJsonString(inputParams));
        instance.setStartedAt(new Date());
        instance.setCreatedAt(new Date());
        instance.setUpdatedAt(new Date());
        workflowMapper.insertInstance(instance);
        logger.debug("创建工作流实例成功，ID: {}", instance.getId());

        // 执行工作流实例
        WorkflowInstance result = executeWorkflowInstance(instance.getId());
        logger.debug("工作流执行完成，实例ID: {}, 状态: {}", result.getId(), result.getStatus());
        return result;
    }

    @Override
    @Transactional
    public WorkflowInstance executeWorkflowInstance(Long instanceId) {
        logger.debug("开始执行工作流实例，ID: {}", instanceId);
        
        // 获取工作流实例
        WorkflowInstance instance = workflowMapper.getInstanceById(instanceId);
        if (instance == null) {
            logger.error("执行工作流实例失败，实例不存在，ID: {}", instanceId);
            throw new IllegalArgumentException("工作流实例不存在");
        }
        logger.debug("获取到工作流实例，名称: {}", instance.getName());

        // 获取工作流定义
        List<WorkflowNode> nodes = workflowMapper.getNodesByWorkflowId(instance.getWorkflowId());
        List<WorkflowEdge> edges = workflowMapper.getEdgesByWorkflowId(instance.getWorkflowId());
        logger.debug("获取到工作流定义，节点数: {}, 边数: {}", nodes.size(), edges.size());

        // 查找开始节点
        WorkflowNode startNode = nodes.stream()
                .filter(node -> "start".equals(node.getType()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("工作流中没有开始节点"));
        logger.debug("找到开始节点，ID: {}, 类型: {}", startNode.getId(), startNode.getType());

        // 查找边的映射关系
        Map<Long, List<WorkflowEdge>> outgoingEdges = edges.stream()
                .collect(Collectors.groupingBy(WorkflowEdge::getFromNodeId));
        logger.debug("构建边映射关系完成，包含 {} 个节点的出边", outgoingEdges.size());

        // 解析输入参数
        Map<String, Object> context = jsonStringToMap(instance.getInputParams());
        logger.debug("解析输入参数完成: {}", context);

        // 开始执行工作流
        WorkflowInstance result = executeNodeChain(instance, startNode, outgoingEdges, nodes, context);
        logger.debug("工作流实例执行完成，ID: {}, 状态: {}", result.getId(), result.getStatus());
        return result;
    }

    @Override
    @Transactional
    public WorkflowInstance continueExecution(Long instanceId, Long nodeId, Map<String, Object> context) {
        logger.debug("继续执行工作流实例，ID: {}, 从节点: {}, 上下文: {}", instanceId, nodeId, context);
        
        // 获取工作流实例
        WorkflowInstance instance = workflowMapper.getInstanceById(instanceId);
        if (instance == null) {
            logger.error("继续执行工作流实例失败，实例不存在，ID: {}", instanceId);
            throw new IllegalArgumentException("工作流实例不存在");
        }
        logger.debug("获取到工作流实例，名称: {}", instance.getName());

        // 获取工作流定义
        List<WorkflowNode> nodes = workflowMapper.getNodesByWorkflowId(instance.getWorkflowId());
        List<WorkflowEdge> edges = workflowMapper.getEdgesByWorkflowId(instance.getWorkflowId());
        logger.debug("获取到工作流定义，节点数: {}, 边数: {}", nodes.size(), edges.size());

        // 查找边的映射关系
        Map<Long, List<WorkflowEdge>> outgoingEdges = edges.stream()
                .collect(Collectors.groupingBy(WorkflowEdge::getFromNodeId));
        logger.debug("构建边映射关系完成，包含 {} 个节点的出边", outgoingEdges.size());

        // 查找当前节点
        WorkflowNode currentNode = nodes.stream()
                .filter(node -> node.getId().equals(nodeId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("节点不存在"));
        logger.debug("找到当前节点，ID: {}, 类型: {}", currentNode.getId(), currentNode.getType());

        // 继续执行工作流
        WorkflowInstance result = executeNodeChain(instance, currentNode, outgoingEdges, nodes, context);
        logger.debug("继续执行工作流实例完成，ID: {}, 状态: {}", result.getId(), result.getStatus());
        return result;
    }

    @Override
    @Transactional
    public WorkflowInstance cancelWorkflow(Long instanceId) {
        WorkflowInstance instance = workflowMapper.getInstanceById(instanceId);
        if (instance == null) {
            throw new IllegalArgumentException("工作流实例不存在");
        }

        // 更新实例状态为已取消
        instance.setStatus("canceled");
        instance.setUpdatedAt(new Date());
        workflowMapper.updateInstance(instance);

        return instance;
    }

    /**
     * 执行节点链
     */
    private WorkflowInstance executeNodeChain(WorkflowInstance instance, WorkflowNode currentNode, 
                                             Map<Long, List<WorkflowEdge>> outgoingEdges, 
                                             List<WorkflowNode> allNodes, 
                                             Map<String, Object> context) {
        logger.debug("开始执行节点链，当前实例: {}, 当前节点: {}", instance.getId(), currentNode.getId());
        
        while (currentNode != null) {
            logger.debug("执行节点，ID: {}, 类型: {}, 上下文: {}", currentNode.getId(), currentNode.getType(), context);
            
            // 执行当前节点
            Map<String, Object> executionResult = executeNode(currentNode, instance, context);
            logger.debug("节点执行结果: {}", executionResult);
            
            // 记录执行日志
            logExecution(instance, currentNode, context, executionResult);
            logger.debug("已记录节点执行日志，实例: {}, 节点: {}", instance.getId(), currentNode.getId());
            
            // 检查执行状态
            if ("failed".equals(executionResult.get("status"))) {
                logger.error("节点执行失败，实例: {}, 节点: {}, 错误: {}", 
                            instance.getId(), currentNode.getId(), executionResult.get("errorMessage"));
                instance.setStatus("failed");
                instance.setOutputParams(mapToJsonString(executionResult));
                instance.setFinishedAt(new Date());
                instance.setUpdatedAt(new Date());
                workflowMapper.updateInstance(instance);
                logger.debug("已更新工作流实例状态为失败，ID: {}", instance.getId());
                return instance;
            }
            
            // 更新上下文
            context = (Map<String, Object>) executionResult.get("outputData");
            logger.debug("已更新上下文: {}", context);
            
            // 检查是否到达结束节点
            if ("end".equals(currentNode.getType())) {
                logger.debug("到达结束节点，工作流执行完成，实例: {}", instance.getId());
                instance.setStatus("completed");
                instance.setOutputParams(mapToJsonString(context));
                instance.setFinishedAt(new Date());
                instance.setUpdatedAt(new Date());
                workflowMapper.updateInstance(instance);
                logger.debug("已更新工作流实例状态为完成，ID: {}", instance.getId());
                return instance;
            }
            
            // 获取下一个节点
            List<WorkflowEdge> edges = outgoingEdges.get(currentNode.getId());
            if (edges == null || edges.isEmpty()) {
                // 没有后续节点，工作流结束
                logger.debug("没有后续节点，工作流执行完成，实例: {}", instance.getId());
                instance.setStatus("completed");
                instance.setOutputParams(mapToJsonString(context));
                instance.setFinishedAt(new Date());
                instance.setUpdatedAt(new Date());
                workflowMapper.updateInstance(instance);
                logger.debug("已更新工作流实例状态为完成，ID: {}", instance.getId());
                return instance;
            }
            
            // 目前只支持单一分支，取第一条边
            WorkflowEdge nextEdge = edges.get(0);
            currentNode = allNodes.stream()
                    .filter(node -> node.getId().equals(nextEdge.getToNodeId()))
                    .findFirst()
                    .orElse(null);
            logger.debug("获取到下一个节点，ID: {}", currentNode != null ? currentNode.getId() : "null");
        }
        
        // 执行完成
        logger.debug("节点链执行完成，工作流实例: {}", instance.getId());
        instance.setStatus("completed");
        instance.setOutputParams(mapToJsonString(context));
        instance.setFinishedAt(new Date());
        instance.setUpdatedAt(new Date());
        workflowMapper.updateInstance(instance);
        logger.debug("已更新工作流实例状态为完成，ID: {}", instance.getId());
        return instance;
    }

    /**
     * 执行单个节点
     */
    private Map<String, Object> executeNode(WorkflowNode node, WorkflowInstance instance, Map<String, Object> context) {
        logger.debug("开始执行单个节点，实例: {}, 节点: {}, 类型: {}", 
                    instance.getId(), node.getId(), node.getType());
        
        NodeProcessor processor = processorMap.get(node.getType());
        if (processor == null) {
            logger.error("执行节点失败，不支持的节点类型: {}, 节点: {}", node.getType(), node.getId());
            throw new IllegalArgumentException("不支持的节点类型: " + node.getType());
        }
        logger.debug("找到对应的节点处理器: {}", processor.getClass().getSimpleName());

        long startTime = System.currentTimeMillis();
        Map<String, Object> result = processor.process(node, instance, context);
        long endTime = System.currentTimeMillis();
        
        // 添加执行时间
        double executionTime = (endTime - startTime) / 1000.0;
        result.put("executionTime", executionTime);
        
        logger.debug("节点执行完成，实例: {}, 节点: {}, 耗时: {}s, 结果: {}", 
                    instance.getId(), node.getId(), executionTime, result);
        
        return result;
    }

    /**
     * 记录执行日志
     */
    private void logExecution(WorkflowInstance instance, WorkflowNode node, 
                             Map<String, Object> inputData, Map<String, Object> outputData) {
        WorkflowExecutionLog log = new WorkflowExecutionLog();
        log.setInstanceId(instance.getId());
        log.setNodeId(node.getId());
        log.setNodeType(node.getType());
        log.setExecutionTime((Double) outputData.getOrDefault("executionTime", 0.0));
        log.setStatus((String) outputData.getOrDefault("status", "success"));
        log.setInputData(mapToJsonString(inputData));
        log.setOutputData(mapToJsonString((Map<String, Object>) outputData.getOrDefault("outputData", new HashMap<>())));
        log.setCreatedAt(new Date());
        workflowMapper.insertExecutionLog(log);
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
