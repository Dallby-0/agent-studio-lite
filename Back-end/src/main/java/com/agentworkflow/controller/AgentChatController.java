package com.agentworkflow.controller;

import com.agentworkflow.entity.Agent;
import com.agentworkflow.service.AgentService;
import com.agentworkflow.service.AIService;
import com.agentworkflow.service.RetrievalService;
import com.agentworkflow.utils.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Agent聊天控制器 - 处理与Agent的对话请求
 */
@RestController
@RequestMapping("/api/agent-chat")
public class AgentChatController {

    @Autowired
    private AgentService agentService;

    @Autowired
    private AIService aiService;

    @Autowired
    private RetrievalService retrievalService;

    /**
     * 向指定Agent发送消息
     * @param agentId Agent ID
     * @param request 聊天请求
     * @return AI响应
     */
    @PostMapping("/{agentId}")
    public ApiResponse chat(@PathVariable Long agentId, @RequestBody ChatRequest request) {
        try {
            // 获取Agent信息
            Agent agent = agentService.getAgentById(agentId);
            if (agent == null) {
                return ApiResponse.fail(404, "Agent不存在");
            }

            // 检查Agent状态
            if (agent.getStatus() != 1) {
                return ApiResponse.fail(400, "该Agent已禁用");
            }

            // 获取Agent的系统提示词、插件配置和API配置
            String systemPrompt = agent.getSystemPrompt();
            String pluginsJson = agent.getPluginsJson();
            String apiConfig = agent.getApiConfig();

            System.out.println("Agent聊天 - ID: " + agentId + ", 名称: " + agent.getName());
            System.out.println("系统提示词: " + systemPrompt);
            System.out.println("插件配置: " + pluginsJson);
            System.out.println("API配置: " + (apiConfig != null ? apiConfig.substring(0, Math.min(15, apiConfig.length())) + "..." : "使用默认配置"));

            // 提取当前问题文本（优先使用历史消息中的最后一条）
            String question = request.getMessage();
            if (request.getMessages() != null && !request.getMessages().isEmpty()) {
                Map<String, String> lastMsg = request.getMessages().get(request.getMessages().size() - 1);
                if (lastMsg != null) {
                    String content = lastMsg.get("content");
                    if (content != null && !content.isEmpty()) {
                        question = content;
                    }
                }
            }

            // 基于 Agent 绑定的知识库检索上下文
            String context = retrievalService.buildContextForAgent(agentId, question);

            // 将上下文拼接到 system prompt 中
            String enhancedSystemPrompt = systemPrompt;
            if (context != null && !context.isEmpty()) {
                enhancedSystemPrompt = (systemPrompt != null ? systemPrompt : "")
                        + "\n\n以下是与用户问题相关的知识库片段，请在回答时优先参考（如无把握，请明确说明）：\n\n"
                        + context;
            }

            // 调用AI服务（使用Agent的API配置）
            String response;
            if (request.getMessages() != null && !request.getMessages().isEmpty()) {
                // 带历史消息
                response = aiService.chatWithHistoryAndConfig(enhancedSystemPrompt, request.getMessages(), pluginsJson, apiConfig);
            } else {
                // 单条消息
                response = aiService.chatWithConfig(enhancedSystemPrompt, request.getMessage(), pluginsJson, apiConfig);
            }

            // 构建响应
            Map<String, Object> data = new HashMap<>();
            data.put("agentId", agentId);
            data.put("agentName", agent.getName());
            data.put("response", response);
            data.put("timestamp", new Date());

            return ApiResponse.success(data);
        } catch (Exception e) {
            System.err.println("Agent聊天失败: " + e.getMessage());
            e.printStackTrace();
            return ApiResponse.fail(500, "聊天服务异常: " + e.getMessage());
        }
    }

    /**
     * 获取Agent的基本信息（用于聊天前的展示）
     */
    @GetMapping("/{agentId}/info")
    public ApiResponse getAgentInfo(@PathVariable Long agentId) {
        Agent agent = agentService.getAgentById(agentId);
        if (agent == null) {
            return ApiResponse.fail(404, "Agent不存在");
        }

        Map<String, Object> info = new HashMap<>();
        info.put("id", agent.getId());
        info.put("name", agent.getName());
        info.put("description", agent.getDescription());
        info.put("type", agent.getType());
        info.put("status", agent.getStatus());

        return ApiResponse.success(info);
    }

    /**
     * 聊天请求内部类
     */
    public static class ChatRequest {
        private String message;  // 单条消息
        private List<Map<String, String>> messages;  // 历史消息列表

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public List<Map<String, String>> getMessages() {
            return messages;
        }

        public void setMessages(List<Map<String, String>> messages) {
            this.messages = messages;
        }
    }
}

