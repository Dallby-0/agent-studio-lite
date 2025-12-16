package com.agentworkflow.service.impl;

import com.agentworkflow.entity.Agent;
import com.agentworkflow.entity.AgentKnowledge;
import com.agentworkflow.entity.AgentPlugin;
import com.agentworkflow.mapper.AgentMapper;
import com.agentworkflow.service.AgentService;
import com.agentworkflow.utils.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.List;

@Service
public class AgentServiceImpl implements AgentService {

    @Autowired
    private AgentMapper agentMapper;
    
    @Autowired
    private JwtService jwtService;
    
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
            
            // 如果无法从JWT令牌中获取用户ID，尝试从SecurityContext获取
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() && !authentication.getName().equals("anonymousUser")) {
                // 不使用硬编码值，继续尝试从JWT获取
                // 为了调试，可以记录用户名
                System.out.println("当前认证用户: " + authentication.getName());
            }
        } catch (Exception e) {
            // 发生异常时记录日志，但不抛出异常
            System.err.println("获取用户ID时发生异常: " + e.getMessage());
        }
        
        // 不再默认返回1L，返回null表示无法获取有效的用户ID
        return null;
    }

    @Override
    public List<Agent> getAllAgents() {
        Long currentUserId = getCurrentUserId();
        // 对于管理员（负数ID），返回所有智能体；对于普通用户，只返回自己创建的智能体
        if (currentUserId == null || currentUserId < 0) {
            return agentMapper.getAllAgents();
        } else {
            return agentMapper.getAgentsByCreatedBy(currentUserId);
        }
    }

    @Override
    public Agent getAgentById(Long id) {
        Long currentUserId = getCurrentUserId();
        // 对于管理员（负数ID）或无法获取用户ID时，直接返回指定ID的智能体
        if (currentUserId == null || currentUserId < 0) {
            return agentMapper.getAgentById(id);
        } else {
            // 对于普通用户，只返回自己创建的智能体
            return agentMapper.getAgentByIdAndCreatedBy(id, currentUserId);
        }
    }

    @Override
    public Agent createAgent(Agent agent) {
        // 简化实现，只设置必要的默认值
        if (agent.getStatus() == null) {
            agent.setStatus(1); // 默认启用状态
        }

        // 确保type字段有值
        if (agent.getAgentType() == null || agent.getAgentType().isEmpty()) {
            agent.setAgentType("default"); // 设置默认类型
        }

        // 处理JSON字段：空字符串转为null（MySQL JSON类型不接受空字符串）
        if (agent.getPluginsJson() != null && agent.getPluginsJson().isEmpty()) {
            agent.setPluginsJson(null);
        }
        if (agent.getConfigJson() != null && agent.getConfigJson().isEmpty()) {
            agent.setConfigJson(null);
        }

        // 获取当前用户ID
        Long currentUserId = getCurrentUserId();

        // 如果无法获取用户ID，使用默认值1（用于开发调试）
        if (currentUserId == null) {
            System.out.println("警告：无法获取用户ID，使用默认值1");
            currentUserId = 1L;
        }

        // 设置创建时间和更新时间
        agent.setCreatedAt(new Date());
        agent.setUpdatedAt(new Date());
        // 设置创建者ID
        agent.setCreatedBy(currentUserId);
        // 设置未删除状态
        agent.setIsDeleted(0);

        System.out.println("创建智能体，用户ID: " + currentUserId + ", 智能体名称: " + agent.getName());
        // 调用Mapper插入数据
        agentMapper.insertAgent(agent);
        return agent;
    }

    @Override
    public Agent updateAgent(Agent agent) {
        // 检查智能体是否存在
        Agent existingAgent = agentMapper.getAgentById(agent.getId());
        if (existingAgent == null) {
            return null;
        }

        // 确保不会覆盖创建者ID、创建时间等重要字段
        agent.setCreatedBy(existingAgent.getCreatedBy());
        agent.setCreatedAt(existingAgent.getCreatedAt());
        agent.setIsDeleted(existingAgent.getIsDeleted());

        // 如果未提供某些字段，使用现有值
        if (agent.getName() == null) {
            agent.setName(existingAgent.getName());
        }
        if (agent.getAgentType() == null || agent.getAgentType().isEmpty()) {
            agent.setAgentType(existingAgent.getAgentType());
        }
        if (agent.getStatus() == null) {
            agent.setStatus(existingAgent.getStatus());
        }

        // 处理JSON字段：空字符串转为null（MySQL JSON类型不接受空字符串）
        if (agent.getPluginsJson() != null && agent.getPluginsJson().isEmpty()) {
            agent.setPluginsJson(null);
        }
        if (agent.getConfigJson() != null && agent.getConfigJson().isEmpty()) {
            agent.setConfigJson(null);
        }

        // 更新时间戳
        agent.setUpdatedAt(new Date());

        try {
            agentMapper.updateAgent(agent);
            return agentMapper.getAgentById(agent.getId()); // 返回更新后的对象
        } catch (Exception e) {
            System.err.println("更新智能体时发生错误: " + e.getMessage());
            e.printStackTrace();
            throw e; // 重新抛出异常以便上层捕获
        }
    }

    @Override
    public boolean deleteAgent(Long id) {
        return agentMapper.deleteAgent(id) > 0;
    }

    @Override
    public List<AgentKnowledge> getAgentKnowledge(Long agentId) {
        return agentMapper.getAgentKnowledge(agentId);
    }

    @Override
    public AgentKnowledge addAgentKnowledge(AgentKnowledge agentKnowledge) {
        agentMapper.insertAgentKnowledge(agentKnowledge);
        return agentKnowledge;
    }

    @Override
    public boolean removeAgentKnowledge(Long id) {
        return agentMapper.deleteAgentKnowledge(id) > 0;
    }

    @Override
    public int removeAgentKnowledgeByKnowledgeBaseId(Long knowledgeBaseId) {
        return agentMapper.deleteAgentKnowledgeByKnowledgeBaseId(knowledgeBaseId);
    }

    @Override
    public List<AgentPlugin> getAllPlugins() {
        return agentMapper.getAllPlugins();
    }

    @Override
    public AgentPlugin getPluginById(Long id) {
        return agentMapper.getPluginById(id);
    }

    @Override
    public AgentPlugin createPlugin(AgentPlugin plugin) {
        agentMapper.insertPlugin(plugin);
        return plugin;
    }

    @Override
    public AgentPlugin updatePlugin(AgentPlugin plugin) {
        agentMapper.updatePlugin(plugin);
        return plugin;
    }

    @Override
    public boolean deletePlugin(Long id) {
        return agentMapper.deletePlugin(id) > 0;
    }
}