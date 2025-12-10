package com.agentworkflow.service.impl;

import com.agentworkflow.entity.Workflow;
import com.agentworkflow.entity.WorkflowNode;
import com.agentworkflow.entity.WorkflowEdge;
import com.agentworkflow.mapper.WorkflowMapper;
import com.agentworkflow.service.WorkflowService;
import com.agentworkflow.utils.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

@Service
public class WorkflowServiceImpl implements WorkflowService {

    @Autowired
    private WorkflowMapper workflowMapper;
    
    @Autowired
    private JwtService jwtService;
    
    // 添加ObjectMapper实例用于JSON转换
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 获取当前登录用户的ID
     * @return 当前用户ID
     */
    private Long getCurrentUserId() {
        try {
            // 从请求头中获取JWT令牌
            ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (requestAttributes != null) {
                HttpServletRequest request = requestAttributes.getRequest();
                String authHeader = request.getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    String jwtToken = authHeader.substring(7);
                    // 从令牌中提取用户ID
                    Long userId = jwtService.getUserIdFromToken(jwtToken);
                    if (userId != null) {
                        return userId;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("获取用户ID时发生异常: " + e.getMessage());
        }
        
        // 默认返回1L用于开发调试
        return 1L;
    }

    @Override
    public List<Workflow> getAllWorkflows() {
        Long currentUserId = getCurrentUserId();
        return workflowMapper.getWorkflowsByCreatedBy(currentUserId);
    }

    @Override
    public Workflow getWorkflowById(Long id) {
        Long currentUserId = getCurrentUserId();
        return workflowMapper.getWorkflowByIdAndCreatedBy(id, currentUserId);
    }

    @Override
    @Transactional
    public Workflow createWorkflow(Workflow workflow) {
        // 设置默认值
        if (workflow.getStatus() == null) {
            workflow.setStatus(1); // 默认启用状态
        }
        if (workflow.getVersion() == null) {
            workflow.setVersion("1.0.0"); // 默认版本号
        }
        if (workflow.getIsDeleted() == null) {
            workflow.setIsDeleted(0); // 默认未删除
        }
        
        // 设置创建者ID和时间
        workflow.setCreatedBy(getCurrentUserId());
        workflow.setCreatedAt(new Date());
        workflow.setUpdatedAt(new Date());
        
        // 直接插入工作流（definition字段已包含完整定义）
        workflowMapper.insertWorkflow(workflow);
        
        return workflow;
    }

    @Override
    @Transactional
    public Workflow updateWorkflow(Workflow workflow) {
        // 设置默认值
        if (workflow.getStatus() == null) {
            workflow.setStatus(1); // 默认启用状态
        }
        if (workflow.getIsDeleted() == null) {
            workflow.setIsDeleted(0); // 默认未删除
        }
        
        // 更新时间
        workflow.setUpdatedAt(new Date());
        
        // 直接更新工作流（definition字段已包含完整定义）
        workflowMapper.updateWorkflow(workflow);
        
        return workflow;
    }

    @Override
    public boolean deleteWorkflow(Long id) {
        return workflowMapper.deleteWorkflow(id) > 0;
    }

    @Override
    public Workflow getWorkflowDefinition(Long workflowId) {
        // 直接返回完整的Workflow对象，包含definition字段
        return workflowMapper.getWorkflowById(workflowId);
    }
}
