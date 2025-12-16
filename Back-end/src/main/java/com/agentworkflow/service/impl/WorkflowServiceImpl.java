package com.agentworkflow.service.impl;

import com.agentworkflow.entity.Workflow;
import com.agentworkflow.entity.WorkflowNode;
import com.agentworkflow.entity.WorkflowEdge;
import com.agentworkflow.entity.StateWorkflowDefinition;
import com.agentworkflow.mapper.StateWorkflowMapper;
import com.agentworkflow.service.WorkflowService;
import com.agentworkflow.utils.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class WorkflowServiceImpl implements WorkflowService {

    @Autowired
    private StateWorkflowMapper workflowMapper;
    
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
        // 暂时返回空列表，因为我们已经迁移到新的状态机工作流
        return new ArrayList<>();
    }

    @Override
    public Workflow getWorkflowById(Long id) {
        // 暂时返回null，因为我们已经迁移到新的状态机工作流
        return null;
    }

    @Override
    @Transactional
    public Workflow createWorkflow(Workflow workflow) {
        // 暂时不支持创建旧版工作流
        return null;
    }

    @Override
    @Transactional
    public Workflow updateWorkflow(Workflow workflow) {
        // 暂时不支持更新旧版工作流
        return null;
    }

    @Override
    public boolean deleteWorkflow(Long id) {
        // 暂时不支持删除旧版工作流
        return false;
    }

    @Override
    public Workflow getWorkflowDefinition(Long workflowId) {
        // 暂时返回null，因为我们已经迁移到新的状态机工作流
        return null;
    }
}
