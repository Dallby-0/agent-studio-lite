package com.agentworkflow.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import com.agentworkflow.websocket.WorkflowChatWebSocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    
    private final WorkflowChatWebSocketHandler workflowChatWebSocketHandler;
    
    public WebSocketConfig(WorkflowChatWebSocketHandler workflowChatWebSocketHandler) {
        this.workflowChatWebSocketHandler = workflowChatWebSocketHandler;
    }
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(workflowChatWebSocketHandler, "/api/workflow-instances/{instanceId}/chat")
                .setAllowedOrigins("*"); // 允许所有来源，生产环境应该配置具体来源
    }
}

