package com.agentworkflow.service;

import com.agentworkflow.entity.Agent;
import com.agentworkflow.entity.AgentKnowledge;
import com.agentworkflow.entity.AgentPlugin;

import java.util.List;

public interface AgentService {
    // 智能体相关方法
    List<Agent> getAllAgents();
    Agent getAgentById(Long id);
    Agent createAgent(Agent agent);
    Agent updateAgent(Agent agent);
    boolean deleteAgent(Long id);

    // 智能体知识库相关方法
    List<AgentKnowledge> getAgentKnowledge(Long agentId);
    AgentKnowledge addAgentKnowledge(AgentKnowledge agentKnowledge);
    boolean removeAgentKnowledge(Long id);
    int removeAgentKnowledgeByKnowledgeBaseId(Long knowledgeBaseId);

    // 智能体插件相关方法
    List<AgentPlugin> getAllPlugins();
    AgentPlugin getPluginById(Long id);
    AgentPlugin createPlugin(AgentPlugin plugin);
    AgentPlugin updatePlugin(AgentPlugin plugin);
    boolean deletePlugin(Long id);
}