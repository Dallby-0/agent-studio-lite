package com.agentworkflow.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import com.agentworkflow.engine.StateMachineEngine;
import com.agentworkflow.mapper.StateWorkflowMapper;
import com.agentworkflow.service.AIService;
import com.agentworkflow.service.WorkflowChatService;
import com.agentworkflow.service.VectorStoreService;
import com.agentworkflow.service.EmbeddingService;

@Configuration
public class StateWorkflowConfig {
    
    @Autowired
    private AIService aiService;
    
    @Autowired
    private VectorStoreService vectorStoreService;
    
    @Autowired
    private EmbeddingService embeddingService;
    
    @Autowired
    private ApplicationContext applicationContext;
    
    @Bean
    @DependsOn("workflowChatService")  // 确保 WorkflowChatService 先创建
    public StateMachineEngine stateMachineEngine(StateWorkflowMapper workflowMapper) {
        System.out.println("\n========== StateWorkflowConfig: 创建 StateMachineEngine Bean ==========");
        System.out.println("AIService: " + (aiService != null ? "已注入" : "未注入"));
        
        // 从 ApplicationContext 获取 WorkflowChatService（避免循环依赖）
        WorkflowChatService chatService = null;
        try {
            chatService = applicationContext.getBean(WorkflowChatService.class);
            System.out.println("WorkflowChatService: 从ApplicationContext获取成功");
        } catch (Exception e) {
            System.err.println("WorkflowChatService: 从ApplicationContext获取失败: " + e.getMessage());
            e.printStackTrace();
        }
        
        StateMachineEngine engine = new StateMachineEngine(workflowMapper);
        engine.setAIService(aiService);
        engine.setChatService(chatService);
        engine.setVectorStoreService(vectorStoreService);
        engine.setEmbeddingService(embeddingService);
        
        System.out.println("StateMachineEngine Bean 创建完成");
        System.out.println("========== StateWorkflowConfig: Bean 创建结束 ==========\n");
        
        return engine;
    }
}
