package com.agentworkflow.controller;

import com.agentworkflow.entity.Workflow;
import com.agentworkflow.service.WorkflowService;
import com.agentworkflow.utils.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowController.class);

    @Autowired
    private WorkflowService workflowService;

    // 获取所有工作流
    @GetMapping
    public ApiResponse getAllWorkflows() {
        logger.debug("接收到获取所有工作流的请求");
        List<Workflow> workflows = workflowService.getAllWorkflows();
        logger.debug("获取到 {} 个工作流", workflows.size());
        
        // 直接返回工作流列表
        return ApiResponse.success(workflows);
    }

    // 获取单个工作流
    @GetMapping("/{id}")
    public ApiResponse getWorkflowById(@PathVariable Long id) {
        logger.debug("接收到获取单个工作流的请求，ID: {}", id);
        Workflow workflow = workflowService.getWorkflowById(id);
        if (workflow != null) {
            logger.debug("获取到工作流: {}", workflow.getName());
            return ApiResponse.success(workflow);
        } else {
            logger.warn("获取工作流失败，工作流不存在，ID: {}", id);
            return ApiResponse.fail(404, "工作流不存在");
        }
    }

    // 获取工作流完整定义（包含节点和边）
    @GetMapping("/{id}/definition")
    public ApiResponse getWorkflowDefinition(@PathVariable Long id) {
        logger.debug("接收到获取工作流完整定义的请求，ID: {}", id);
        Workflow workflow = workflowService.getWorkflowDefinition(id);
        if (workflow != null) {
            logger.debug("获取到工作流定义，名称: {}", workflow.getName());
            return ApiResponse.success(workflow);
        } else {
            logger.warn("获取工作流定义失败，工作流不存在，ID: {}", id);
            return ApiResponse.fail(404, "工作流定义不存在");
        }
    }

    // 创建工作流
    @PostMapping
    public ApiResponse createWorkflow(@RequestBody Workflow workflow) {
        logger.debug("接收到创建工作流的请求，名称: {}", workflow.getName());
        try {
            // 直接创建工作流
            Workflow createdWorkflow = workflowService.createWorkflow(workflow);
            logger.debug("创建工作流成功，ID: {}", createdWorkflow.getId());
            
            // 返回创建的工作流
            return ApiResponse.success(createdWorkflow);
        } catch (Exception e) {
            logger.error("创建工作流失败: {}", e.getMessage(), e);
            return ApiResponse.fail(500, "创建工作流失败: " + e.getMessage());
        }
    }

    // 更新工作流
    @PutMapping("/{id}")
    public ApiResponse updateWorkflow(@PathVariable Long id, @RequestBody Workflow workflow) {
        logger.debug("接收到更新工作流的请求，ID: {}, 名称: {}", id, workflow.getName());
        try {
            // 设置ID
            workflow.setId(id);
            
            // 直接更新工作流
            Workflow updatedWorkflow = workflowService.updateWorkflow(workflow);
            logger.debug("更新工作流成功，ID: {}", updatedWorkflow.getId());
            
            // 返回更新后的工作流
            return ApiResponse.success(updatedWorkflow);
        } catch (Exception e) {
            logger.error("更新工作流失败: {}", e.getMessage(), e);
            return ApiResponse.fail(500, "更新工作流失败: " + e.getMessage());
        }
    }

    // 删除工作流
    @DeleteMapping("/{id}")
    public ApiResponse deleteWorkflow(@PathVariable Long id) {
        logger.debug("接收到删除工作流的请求，ID: {}", id);
        boolean deleted = workflowService.deleteWorkflow(id);
        if (deleted) {
            logger.debug("删除工作流成功，ID: {}", id);
            return ApiResponse.success(null);
        } else {
            logger.warn("删除工作流失败，工作流不存在，ID: {}", id);
            return ApiResponse.fail(404, "工作流不存在");
        }
    }
}
