package com.agentworkflow.controller;

import com.agentworkflow.entity.WorkflowInstance;
import com.agentworkflow.entity.WorkflowExecutionLog;
import com.agentworkflow.service.WorkflowInstanceService;
import com.agentworkflow.utils.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workflow-instances")
public class WorkflowInstanceController {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowInstanceController.class);

    @Autowired
    private WorkflowInstanceService workflowInstanceService;

    // 获取所有工作流实例
    @GetMapping
    public ApiResponse getAllInstances() {
        logger.debug("接收到获取所有工作流实例的请求");
        List<WorkflowInstance> instances = workflowInstanceService.getAllInstances();
        logger.debug("获取到 {} 个工作流实例", instances.size());
        return ApiResponse.success(instances);
    }

    // 根据工作流ID获取实例
    @GetMapping("/workflow/{workflowId}")
    public ApiResponse getInstancesByWorkflowId(@PathVariable Long workflowId) {
        logger.debug("接收到根据工作流ID获取实例的请求，工作流ID: {}", workflowId);
        List<WorkflowInstance> instances = workflowInstanceService.getInstancesByWorkflowId(workflowId);
        logger.debug("获取到 {} 个工作流实例", instances.size());
        return ApiResponse.success(instances);
    }

    // 获取单个工作流实例
    @GetMapping("/{id}")
    public ApiResponse getInstanceById(@PathVariable Long id) {
        logger.debug("接收到获取单个工作流实例的请求，ID: {}", id);
        WorkflowInstance instance = workflowInstanceService.getInstanceById(id);
        if (instance != null) {
            logger.debug("获取到工作流实例: {}", instance.getName());
            return ApiResponse.success(instance);
        } else {
            logger.warn("获取工作流实例失败，实例不存在，ID: {}", id);
            return ApiResponse.fail(404, "工作流实例不存在");
        }
    }

    // 获取工作流实例执行日志
    @GetMapping("/{id}/logs")
    public ApiResponse getInstanceLogs(@PathVariable Long id) {
        logger.debug("接收到获取工作流实例执行日志的请求，实例ID: {}", id);
        List<WorkflowExecutionLog> logs = workflowInstanceService.getExecutionLogsByInstanceId(id);
        logger.debug("获取到 {} 条执行日志", logs.size());
        return ApiResponse.success(logs);
    }

    // 创建工作流实例
    @PostMapping
    public ApiResponse createInstance(@RequestBody WorkflowInstance instance) {
        logger.debug("接收到创建工作流实例的请求，实例: {}", instance);
        WorkflowInstance createdInstance = workflowInstanceService.createInstance(instance);
        logger.debug("创建工作流实例成功，ID: {}", createdInstance.getId());
        return ApiResponse.success(createdInstance);
    }

    // 启动工作流
    @PostMapping("/start/{workflowId}")
    public ApiResponse startWorkflow(@PathVariable Long workflowId, @RequestBody Map<String, Object> inputParams) {
        logger.debug("接收到启动工作流的请求，工作流ID: {}, 输入参数: {}", workflowId, inputParams);
        try {
            WorkflowInstance instance = workflowInstanceService.startWorkflow(workflowId, inputParams);
            logger.debug("启动工作流成功，实例ID: {}, 状态: {}", instance.getId(), instance.getStatus());
            return ApiResponse.success(instance);
        } catch (Exception e) {
            logger.error("启动工作流失败: {}", e.getMessage(), e);
            return ApiResponse.fail(500, "启动工作流失败: " + e.getMessage());
        }
    }
}
