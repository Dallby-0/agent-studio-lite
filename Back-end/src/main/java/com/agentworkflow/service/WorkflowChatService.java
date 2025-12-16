package com.agentworkflow.service;

import com.agentworkflow.websocket.WorkflowChatWebSocketHandler;
import org.springframework.stereotype.Service;

@Service
public class WorkflowChatService {
    
    private final WorkflowChatWebSocketHandler webSocketHandler;
    
    public WorkflowChatService(WorkflowChatWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }
    
    /**
     * 向工作流实例的对话界面发送消息
     * @param instanceId 工作流实例ID
     * @param role 消息角色（user/assistant）
     * @param content 消息内容
     * @param nickname 昵称（可选）
     */
    public void sendChatMessage(Long instanceId, String role, String content, String nickname) {
        System.out.println("\n========== WorkflowChatService.sendChatMessage 被调用 ==========");
        System.out.println("【调用时间】" + new java.util.Date());
        System.out.println("【实例ID】" + instanceId);
        System.out.println("【消息角色】" + role);
        System.out.println("【智能体昵称】" + (nickname != null ? nickname : "(未设置)"));
        System.out.println("【消息内容长度】" + (content != null ? content.length() : 0) + " 字符");
        
        // 显示消息内容预览（前200字符）
        if (content != null && !content.isEmpty()) {
            String preview = content.length() > 200 ? content.substring(0, 200) + "..." : content;
            System.out.println("【消息内容预览】" + preview.replace("\n", "\\n"));
        } else {
            System.out.println("【消息内容预览】(空消息)");
        }
        
        if (webSocketHandler.hasActiveConnection(instanceId)) {
            System.out.println("【连接状态】✓ 检测到活跃的WebSocket连接，准备发送消息");
            webSocketHandler.sendMessage(instanceId, role, content, nickname);
        } else {
            System.err.println("【连接状态】✗ 实例 " + instanceId + " 没有活跃的WebSocket连接，消息将被缓存");
            System.err.println("【当前活跃连接】" + webSocketHandler.getActiveConnections());
            // 即使没有连接，也尝试发送（会被缓存）
            webSocketHandler.sendMessage(instanceId, role, content, nickname);
        }
        System.out.println("========== WorkflowChatService.sendChatMessage 调用结束 ==========\n");
    }
    
    /**
     * 发送状态消息到前端
     * @param instanceId 工作流实例ID
     * @param status 状态（waiting_user_input, running等）
     */
    public void sendStatus(Long instanceId, String status) {
        System.out.println("\n========== WorkflowChatService.sendStatus 被调用 ==========");
        System.out.println("【调用时间】" + new java.util.Date());
        System.out.println("【实例ID】" + instanceId);
        System.out.println("【状态】" + status);
        
        // 检查连接状态
        boolean hasConnection = webSocketHandler.hasActiveConnection(instanceId);
        System.out.println("【连接检查】" + (hasConnection ? "✓ 有活跃连接" : "✗ 无活跃连接"));
        System.out.println("【当前活跃连接】" + webSocketHandler.getActiveConnections());
        
        // 即使没有连接也尝试发送（可能会失败，但至少会记录日志）
        webSocketHandler.sendStatus(instanceId, status);
        
        System.out.println("========== WorkflowChatService.sendStatus 调用结束 ==========\n");
    }
}

