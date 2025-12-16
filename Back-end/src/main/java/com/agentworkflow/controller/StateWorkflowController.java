package com.agentworkflow.controller;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.agentworkflow.entity.StateWorkflowDefinition;
import com.agentworkflow.entity.StateNode;
import com.agentworkflow.entity.StateTransition;
import com.agentworkflow.entity.GlobalVariable;
import com.agentworkflow.entity.StateWorkflowInstance;
import com.agentworkflow.entity.StateExecutionLog;
import com.agentworkflow.engine.StateMachineEngine;
import com.agentworkflow.mapper.StateWorkflowMapper;
import com.agentworkflow.utils.ApiResponse;

@RestController
@RequestMapping("/api/state-workflows")
public class StateWorkflowController {
    
    @Autowired
    private StateWorkflowMapper workflowMapper;
    
    @Autowired
    private StateMachineEngine stateMachineEngine;
    
    // 工作流定义相关API
    @GetMapping
    public ResponseEntity<ApiResponse> getAllWorkflows() {
        List<StateWorkflowDefinition> workflows = workflowMapper.getAllWorkflows();
        return ResponseEntity.ok(ApiResponse.success(workflows));
    }
    
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse> getWorkflowsByUser(@PathVariable Long userId) {
        List<StateWorkflowDefinition> workflows = workflowMapper.getWorkflowsByCreatedBy(userId);
        return ResponseEntity.ok(ApiResponse.success(workflows));
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse> getWorkflowById(@PathVariable Long id) {
        StateWorkflowDefinition workflow = workflowMapper.getWorkflowById(id);
        if (workflow == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(404, "Workflow not found"));
        }
        
        // 加载完整的工作流定义
        loadWorkflowFullDefinition(workflow);
        
        return ResponseEntity.ok(ApiResponse.success(workflow));
    }
    
    @PostMapping
    public ResponseEntity<ApiResponse> createWorkflow(@RequestBody StateWorkflowDefinition workflow) {
        workflow.setCreatedAt(new Date());
        workflow.setUpdatedAt(new Date());
        workflow.setIsDeleted(0);
        
        workflowMapper.insertWorkflow(workflow);
        
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(workflow));
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse> updateWorkflow(@PathVariable Long id, 
                                                      @RequestBody StateWorkflowDefinition workflow) {
        StateWorkflowDefinition existingWorkflow = workflowMapper.getWorkflowById(id);
        if (existingWorkflow == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(404, "Workflow not found"));
        }
        
        workflow.setId(id);
        workflow.setUpdatedAt(new Date());
        
        // 更新工作流
        workflowMapper.updateWorkflow(workflow);
        
        return ResponseEntity.ok(ApiResponse.success(workflow));
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> deleteWorkflow(@PathVariable Long id) {
        int result = workflowMapper.deleteWorkflow(id);
        if (result == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(404, "Workflow not found"));
        }
        
        return ResponseEntity.ok(ApiResponse.success("Workflow deleted successfully"));
    }
    
    // 工作流实例相关API
    @PostMapping("/{id}/execute")
    public ResponseEntity<ApiResponse> executeWorkflow(@PathVariable Long id, 
                                                     @RequestBody Map<String, Object> inputParams) {
        try {
            System.out.println("========== 收到工作流执行请求 ==========");
            System.out.println("工作流ID: " + id);
            System.out.println("输入参数: " + inputParams);
            
            // 先创建实例（不执行），立即返回实例ID
            StateWorkflowInstance instance = stateMachineEngine.createWorkflowInstance(id, inputParams);
            System.out.println("工作流实例已创建，实例ID: " + instance.getId() + "，准备异步执行");
            
            // 异步执行工作流
            stateMachineEngine.executeWorkflowAsync(instance.getId());
            
            return ResponseEntity.ok(ApiResponse.success(instance));
        } catch (Exception e) {
            System.err.println("========== 工作流执行失败 ==========");
            System.err.println("工作流ID: " + id);
            System.err.println("错误类型: " + e.getClass().getName());
            System.err.println("错误信息: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(500, "Failed to execute workflow: " + e.getMessage()));
        }
    }
    
    @GetMapping("/instances")
    public ResponseEntity<ApiResponse> getAllInstances() {
        List<StateWorkflowInstance> instances = workflowMapper.getAllInstances();
        return ResponseEntity.ok(ApiResponse.success(instances));
    }
    
    @GetMapping("/{workflowId}/instances")
    public ResponseEntity<ApiResponse> getInstancesByWorkflow(@PathVariable Long workflowId) {
        List<StateWorkflowInstance> instances = workflowMapper.getInstancesByWorkflowId(workflowId);
        return ResponseEntity.ok(ApiResponse.success(instances));
    }
    
    @GetMapping("/instances/{instanceId}")
    public ResponseEntity<ApiResponse> getInstanceById(@PathVariable Long instanceId) {
        StateWorkflowInstance instance = workflowMapper.getInstanceById(instanceId);
        if (instance == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(404, "Instance not found"));
        }
        
        return ResponseEntity.ok(ApiResponse.success(instance));
    }
    
    @GetMapping("/instances/{instanceId}/logs")
    public ResponseEntity<ApiResponse> getInstanceLogs(@PathVariable Long instanceId) {
        List<StateExecutionLog> logs = workflowMapper.getExecutionLogsByInstanceId(instanceId);
        return ResponseEntity.ok(ApiResponse.success(logs));
    }
    
    /**
     * 提交用户输入，恢复工作流执行
     * @param instanceId 工作流实例ID
     * @param requestBody 请求体，包含用户输入内容
     * @return 响应结果
     */
    @PostMapping("/instances/{instanceId}/user-input")
    public ResponseEntity<ApiResponse> submitUserInput(@PathVariable Long instanceId, 
                                                         @RequestBody Map<String, Object> requestBody) {
        try {
            String userInput = (String) requestBody.getOrDefault("content", "");
            System.out.println("========== 收到用户输入提交请求 ==========");
            System.out.println("实例ID: " + instanceId);
            System.out.println("用户输入: " + userInput);
            
            boolean success = stateMachineEngine.submitUserInput(instanceId, userInput);
            if (success) {
                return ResponseEntity.ok(ApiResponse.success("用户输入已提交，工作流将继续执行"));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.fail(400, "提交用户输入失败，实例可能没有等待用户输入的任务"));
            }
        } catch (Exception e) {
            System.err.println("提交用户输入失败: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(500, "Failed to submit user input: " + e.getMessage()));
        }
    }
    
    // 辅助方法
    private void loadWorkflowFullDefinition(StateWorkflowDefinition workflow) {
        // 工作流定义已经包含完整的JSON定义，不需要额外加载
        // 如果需要在内存中构建节点和转换关系，可以在这里添加JSON解析逻辑
        // 类似StateMachineEngine中的loadWorkflowDefinition方法
    }
}
