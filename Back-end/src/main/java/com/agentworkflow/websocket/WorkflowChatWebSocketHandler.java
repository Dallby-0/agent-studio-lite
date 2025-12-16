package com.agentworkflow.websocket;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriTemplate;

import com.agentworkflow.engine.StateMachineEngine;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WorkflowChatWebSocketHandler extends TextWebSocketHandler {
    
    // 存储每个实例的WebSocket会话，key为instanceId
    private final Map<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();
    
    // 存储待发送的消息队列，key为instanceId，value为消息列表
    private final Map<Long, java.util.List<Map<String, Object>>> pendingMessages = new ConcurrentHashMap<>();
    
    // URI模板，用于从路径中提取instanceId
    private final UriTemplate uriTemplate = new UriTemplate("/api/workflow-instances/{instanceId}/chat");
    
    // 注入工作流引擎，用于提交用户输入
    @Autowired(required = false)
    private StateMachineEngine stateMachineEngine;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long instanceId = extractInstanceId(session);
        if (instanceId != null) {
            sessions.put(instanceId, session);
            System.out.println("\n========== WebSocket连接已建立 ==========");
            System.out.println("【实例ID】" + instanceId);
            System.out.println("【连接时间】" + new java.util.Date());
            System.out.println("【会话URI】" + session.getUri());
            System.out.println("【当前活跃连接数】" + sessions.size());
            
            // 检查是否有缓存消息
            java.util.List<Map<String, Object>> cachedMessages = pendingMessages.get(instanceId);
            if (cachedMessages != null && !cachedMessages.isEmpty()) {
                System.out.println("【发现缓存消息】" + cachedMessages.size() + " 条，准备发送");
            } else {
                System.out.println("【缓存消息】无");
            }
            
            // 发送缓存的消息
            sendPendingMessages(instanceId, session);
            System.out.println("========== WebSocket连接建立完成 ==========\n");
        } else {
            System.err.println("【错误】无法从WebSocket路径中提取实例ID: " + session.getUri());
            session.close(CloseStatus.BAD_DATA);
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Long instanceId = extractInstanceId(session);
        if (instanceId != null) {
            sessions.remove(instanceId);
            // 注意：不清除pendingMessages，以便重连后可以发送
            System.out.println("WebSocket连接已关闭，实例ID: " + instanceId);
        }
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long instanceId = extractInstanceId(session);
        if (instanceId == null) {
            System.err.println("无法从WebSocket会话中提取实例ID，忽略消息");
            return;
        }
        
        try {
            String payload = message.getPayload();
            System.out.println("收到WebSocket消息，实例ID: " + instanceId + ", 内容: " + payload);
            
            // 解析JSON消息
            Map<String, Object> messageData = objectMapper.readValue(payload, Map.class);
            String type = (String) messageData.get("type");
            
            // 处理用户输入消息
            if ("user_input".equals(type)) {
                String userInput = (String) messageData.get("content");
                if (userInput == null) {
                    userInput = "";
                }
                
                System.out.println("收到用户输入消息，实例ID: " + instanceId + ", 输入内容: " + userInput);
                
                // 提交用户输入到工作流引擎
                if (stateMachineEngine != null) {
                    boolean success = stateMachineEngine.submitUserInput(instanceId, userInput);
                    if (success) {
                        System.out.println("用户输入已成功提交，工作流将继续执行");
                    } else {
                        System.err.println("提交用户输入失败，实例可能没有等待用户输入的任务");
                    }
                } else {
                    System.err.println("工作流引擎未注入，无法提交用户输入");
                }
            } else {
                System.out.println("收到其他类型的WebSocket消息: " + type);
            }
        } catch (Exception e) {
            System.err.println("处理WebSocket消息失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 从WebSocket会话的URI中提取实例ID
     */
    private Long extractInstanceId(WebSocketSession session) {
        try {
            String path = session.getUri().getPath();
            Map<String, String> variables = uriTemplate.match(path);
            String instanceIdStr = variables.get("instanceId");
            if (instanceIdStr != null) {
                return Long.parseLong(instanceIdStr);
            }
        } catch (Exception e) {
            System.err.println("提取实例ID失败: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * 向指定实例发送消息
     */
    public void sendMessage(Long instanceId, String role, String content, String nickname) {
        System.out.println("\n--- WorkflowChatWebSocketHandler.sendMessage 开始 ---");
        System.out.println("【实例ID】" + instanceId);
        System.out.println("【角色】" + role);
        System.out.println("【昵称】" + (nickname != null ? nickname : "(未设置)"));
        System.out.println("【内容长度】" + (content != null ? content.length() : 0) + " 字符");
        
        WebSocketSession session = sessions.get(instanceId);
        if (session != null && session.isOpen()) {
            System.out.println("【会话状态】✓ WebSocket会话存在且已打开");
            try {
                // 使用ObjectMapper构建JSON消息，更安全可靠
                java.util.Map<String, Object> messageMap = new java.util.HashMap<>();
                messageMap.put("type", "message");
                messageMap.put("role", role);
                messageMap.put("content", content);
                messageMap.put("nickname", nickname != null ? nickname : "");
                messageMap.put("timestamp", System.currentTimeMillis());
                
                String jsonMessage = objectMapper.writeValueAsString(messageMap);
                System.out.println("【JSON消息】" + (jsonMessage.length() > 300 ? jsonMessage.substring(0, 300) + "..." : jsonMessage));
                
                session.sendMessage(new TextMessage(jsonMessage));
                System.out.println("【发送结果】✓ 消息已成功发送到WebSocket");
                System.out.println("【消息预览】" + (content != null && content.length() > 100 ? content.substring(0, 100).replace("\n", "\\n") + "..." : (content != null ? content.replace("\n", "\\n") : "(空)")));
            } catch (Exception e) {
                System.err.println("【发送结果】✗ 发送WebSocket消息失败");
                System.err.println("【错误类型】" + e.getClass().getName());
                System.err.println("【错误信息】" + e.getMessage());
                e.printStackTrace();
                // 如果发送失败，移除会话并缓存消息
                sessions.remove(instanceId);
                cacheMessage(instanceId, role, content, nickname);
            }
        } else {
            // WebSocket连接不存在，缓存消息
            if (session == null) {
                System.out.println("【会话状态】✗ WebSocket会话不存在");
            } else {
                System.out.println("【会话状态】✗ WebSocket会话已关闭 (isOpen=" + session.isOpen() + ")");
            }
            System.out.println("【处理方式】消息将被缓存，等待连接建立后发送");
            cacheMessage(instanceId, role, content, nickname);
        }
        System.out.println("--- WorkflowChatWebSocketHandler.sendMessage 结束 ---\n");
    }
    
    /**
     * 缓存消息，等待WebSocket连接建立后发送
     */
    private void cacheMessage(Long instanceId, String role, String content, String nickname) {
        System.out.println("\n--- 开始缓存消息 ---");
        System.out.println("【实例ID】" + instanceId);
        System.out.println("【角色】" + role);
        System.out.println("【昵称】" + (nickname != null ? nickname : "(未设置)"));
        System.out.println("【内容长度】" + (content != null ? content.length() : 0) + " 字符");
        
        java.util.Map<String, Object> messageMap = new java.util.HashMap<>();
        messageMap.put("type", "message");
        messageMap.put("role", role);
        messageMap.put("content", content);
        messageMap.put("nickname", nickname != null ? nickname : "");
        messageMap.put("timestamp", System.currentTimeMillis());
        
        java.util.List<Map<String, Object>> messageList = pendingMessages.computeIfAbsent(instanceId, k -> new java.util.ArrayList<>());
        messageList.add(messageMap);
        
        System.out.println("【缓存结果】✓ 消息已缓存");
        System.out.println("【当前缓存数】实例 " + instanceId + " 共有 " + messageList.size() + " 条缓存消息");
        System.out.println("【消息预览】" + (content != null && content.length() > 100 ? content.substring(0, 100).replace("\n", "\\n") + "..." : (content != null ? content.replace("\n", "\\n") : "(空)")));
        System.out.println("--- 消息缓存完成 ---\n");
    }
    
    /**
     * 发送缓存的消息
     */
    private void sendPendingMessages(Long instanceId, WebSocketSession session) {
        System.out.println("\n========== 开始发送缓存的消息 ==========");
        System.out.println("【实例ID】" + instanceId);
        
        java.util.List<Map<String, Object>> messages = pendingMessages.get(instanceId);
        if (messages != null && !messages.isEmpty()) {
            System.out.println("【缓存消息数】" + messages.size());
            System.out.println("【会话状态】" + (session.isOpen() ? "OPEN" : "CLOSED"));
            
            try {
                int successCount = 0;
                int failCount = 0;
                
                for (int i = 0; i < messages.size(); i++) {
                    Map<String, Object> messageMap = messages.get(i);
                    try {
                        String role = (String) messageMap.get("role");
                        String content = (String) messageMap.get("content");
                        String nickname = (String) messageMap.get("nickname");
                        
                        System.out.println("\n【发送第 " + (i + 1) + "/" + messages.size() + " 条消息】");
                        System.out.println("  角色: " + role);
                        System.out.println("  昵称: " + (nickname != null ? nickname : "(未设置)"));
                        System.out.println("  内容长度: " + (content != null ? content.length() : 0) + " 字符");
                        System.out.println("  内容预览: " + (content != null && content.length() > 100 ? content.substring(0, 100).replace("\n", "\\n") + "..." : (content != null ? content.replace("\n", "\\n") : "(空)")));
                        
                        String jsonMessage = objectMapper.writeValueAsString(messageMap);
                        session.sendMessage(new TextMessage(jsonMessage));
                        successCount++;
                        System.out.println("  ✓ 发送成功");
                    } catch (Exception e) {
                        failCount++;
                        System.err.println("  ✗ 发送失败: " + e.getMessage());
                    }
                }
                
                System.out.println("\n【发送统计】");
                System.out.println("  成功: " + successCount + " 条");
                System.out.println("  失败: " + failCount + " 条");
                System.out.println("  总计: " + messages.size() + " 条");
                
                // 清空缓存
                pendingMessages.remove(instanceId);
                System.out.println("【缓存清理】✓ 已清空实例 " + instanceId + " 的缓存消息");
            } catch (Exception e) {
                System.err.println("【发送失败】发送缓存消息时发生异常");
                System.err.println("【错误类型】" + e.getClass().getName());
                System.err.println("【错误信息】" + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.println("【缓存状态】无缓存消息需要发送");
        }
        System.out.println("========== 缓存消息发送完成 ==========\n");
    }
    
    /**
     * 检查指定实例是否有活跃的WebSocket连接
     */
    public boolean hasActiveConnection(Long instanceId) {
        WebSocketSession session = sessions.get(instanceId);
        boolean hasConnection = session != null && session.isOpen();
        System.out.println("检查实例 " + instanceId + " 的连接状态: " + hasConnection);
        if (session != null) {
            System.out.println("  会话状态: " + (session.isOpen() ? "OPEN" : "CLOSED"));
        } else {
            System.out.println("  会话不存在");
        }
        return hasConnection;
    }
    
    /**
     * 获取所有活跃连接的实例ID（用于调试）
     */
    public String getActiveConnections() {
        return sessions.keySet().toString();
    }
    
    /**
     * 发送状态消息到前端
     * @param instanceId 实例ID
     * @param status 状态（waiting_user_input, running, completed等）
     */
    public void sendStatus(Long instanceId, String status) {
        System.out.println("\n--- WorkflowChatWebSocketHandler.sendStatus 开始 ---");
        System.out.println("【实例ID】" + instanceId);
        System.out.println("【状态】" + status);
        
        WebSocketSession session = sessions.get(instanceId);
        if (session != null && session.isOpen()) {
            System.out.println("【会话状态】✓ WebSocket会话存在且已打开");
            try {
                java.util.Map<String, Object> messageMap = new java.util.HashMap<>();
                messageMap.put("type", "status");
                messageMap.put("status", status);
                messageMap.put("waitingUserInput", "waiting_user_input".equals(status));
                messageMap.put("timestamp", System.currentTimeMillis());
                
                String jsonMessage = objectMapper.writeValueAsString(messageMap);
                System.out.println("【JSON消息】" + jsonMessage);
                
                session.sendMessage(new TextMessage(jsonMessage));
                System.out.println("【发送结果】✓ 状态消息已成功发送到WebSocket");
            } catch (Exception e) {
                System.err.println("【发送结果】✗ 发送状态消息失败");
                System.err.println("【错误类型】" + e.getClass().getName());
                System.err.println("【错误信息】" + e.getMessage());
                e.printStackTrace();
                // 如果发送失败，移除会话
                sessions.remove(instanceId);
            }
        } else {
            if (session == null) {
                System.out.println("【会话状态】✗ WebSocket会话不存在");
            } else {
                System.out.println("【会话状态】✗ WebSocket会话已关闭 (isOpen=" + session.isOpen() + ")");
            }
            System.out.println("【处理方式】状态消息无法发送，但不会缓存（状态消息应该实时发送）");
        }
        System.out.println("--- WorkflowChatWebSocketHandler.sendStatus 结束 ---\n");
    }
}

