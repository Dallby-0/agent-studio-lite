package com.agentworkflow.mapper;

import com.agentworkflow.entity.Agent;
import com.agentworkflow.entity.AgentKnowledge;
import com.agentworkflow.entity.AgentPlugin;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AgentMapper {
    // 智能体相关方法
    List<Agent> getAllAgents();
    List<Agent> getAgentsByCreatedBy(Long createdBy);
    Agent getAgentById(Long id);
    Agent getAgentByIdAndCreatedBy(Long id, Long createdBy);
    int insertAgent(Agent agent);
    int updateAgent(Agent agent);
    int deleteAgent(Long id);

    // 智能体知识库相关方法
    List<AgentKnowledge> getAgentKnowledge(Long agentId);
    int insertAgentKnowledge(AgentKnowledge agentKnowledge);
    int deleteAgentKnowledge(Long id);
    int deleteAgentKnowledgeByKnowledgeBaseId(Long knowledgeBaseId);

    // 智能体插件相关方法
    List<AgentPlugin> getAllPlugins();
    AgentPlugin getPluginById(Long id);
    int insertPlugin(AgentPlugin plugin);
    int updatePlugin(AgentPlugin plugin);
    int deletePlugin(Long id);
}