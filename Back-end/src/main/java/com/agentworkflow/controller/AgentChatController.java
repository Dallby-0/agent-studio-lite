package com.agentworkflow.controller;

import com.agentworkflow.entity.Agent;
import com.agentworkflow.service.AgentService;
import com.agentworkflow.service.AIService;
import com.agentworkflow.service.RetrievalService;
import com.agentworkflow.utils.ApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    @Autowired
    private com.agentworkflow.service.impl.IotPluginExecutor iotPluginExecutor;

    private final ObjectMapper objectMapper = new ObjectMapper();

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

            // 直接检测前端是否传来了用于调用 IoT 插件的结构化 JSON（优先）。
            // 支持两种直接命令格式：
            // 1) 控制设备的 JSON：{"device_uuid":"...","port_type":"led","port_id":1,"action":"on"}
            // 2) 传感器查询的 JSON：{"uuid":"...","sensor":"温度"}
            String pluginDirectResponse = null;
            try {
                if (question != null) {
                    try {
                        com.fasterxml.jackson.databind.JsonNode qnode = objectMapper.readTree(question);
                        if (qnode != null && qnode.isObject()) {
                            // control
                            if (qnode.has("device_uuid") && qnode.has("port_type") && qnode.has("port_id") && qnode.has("action")) {
                                // 将整个对象原样发送到 IoT 控制接口
                                String jsonBody = objectMapper.writeValueAsString(qnode);
                                pluginDirectResponse = iotPluginExecutor.controlDevice(pluginsJson, jsonBody);
                            }
                            // sensor
                            else if ((qnode.has("uuid") || qnode.has("device_uuid")) && qnode.has("sensor")) {
                                String uuid = qnode.has("uuid") ? qnode.get("uuid").asText() : qnode.get("device_uuid").asText();
                                String sensor = qnode.get("sensor").asText();
                                pluginDirectResponse = iotPluginExecutor.sensorQuery(pluginsJson, uuid, sensor);
                            }
                        }
                    } catch (com.fasterxml.jackson.core.JsonProcessingException ignore) {
                        // 不是 JSON，就通过历史 messages 的元信息尝试匹配
                    }
                }

                // 如果前端以 messages 列表形式传递 metadata（Map），也尝试从中提取控制/查询指令
                if (pluginDirectResponse == null && request.getMessages() != null) {
                    for (Map<String, String> msgMeta : request.getMessages()) {
                        if (msgMeta == null) continue;
                        if (msgMeta.containsKey("device_uuid") && msgMeta.containsKey("port_type") && msgMeta.containsKey("port_id") && msgMeta.containsKey("action")) {
                            String jsonBody = objectMapper.writeValueAsString(msgMeta);
                            pluginDirectResponse = iotPluginExecutor.controlDevice(pluginsJson, jsonBody);
                            break;
                        }
                        if ((msgMeta.containsKey("uuid") || msgMeta.containsKey("device_uuid")) && msgMeta.containsKey("sensor")) {
                            String uuid = msgMeta.containsKey("uuid") ? msgMeta.get("uuid") : msgMeta.get("device_uuid");
                            String sensor = msgMeta.get("sensor");
                            pluginDirectResponse = iotPluginExecutor.sensorQuery(pluginsJson, uuid, sensor);
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("尝试直接调用 IoT 插件失败: " + e.getMessage());
            }

            // 基于 Agent 绑定的知识库检索上下文
            String context = retrievalService.buildContextForAgent(agentId, question);

            // 将知识库上下文拼接到 system prompt 中
            String enhancedSystemPrompt = systemPrompt;
            if (context != null && !context.isEmpty()) {
                enhancedSystemPrompt = (systemPrompt != null ? systemPrompt : "")
                        + "\n\n以下是与用户问题相关的知识库片段，请在回答时优先参考（如无把握，请明确说明）：\n\n"
                        + context;
            }

            // 如果刚才直接调用了 IoT 插件（前端以 JSON 或 metadata 指定），优先使用该返回值作为 pluginContext
            String pluginContext = null;
            if (pluginDirectResponse != null) {
                pluginContext = "直接调用 IoT 插件返回: " + pluginDirectResponse;
            } else {
                // 否则按原有逻辑使用 LLM 规划/本地规则进行插件调用
                // 此时把 systemPrompt 也传给 IotPluginExecutor，以便从中提取真实 device_uuid
                pluginContext = iotPluginExecutor.buildPluginContext(pluginsJson, question, apiConfig, systemPrompt);
            }

            if (pluginContext != null && !pluginContext.isEmpty()) {
                enhancedSystemPrompt = (enhancedSystemPrompt != null ? enhancedSystemPrompt : "")
                        + "\n\n以下是来自 IoT 插件的实时设备数据或执行结果(JSON)，请据此回答用户问题：\n"
                        + pluginContext;
            }

            // 要求大模型最终以 JSON 协议返回结果
            String jsonProtocolInstruction =
                    "\n\n重要：你的最终回复必须严格是一个 JSON 对象，不能包含任何额外文字。" +
                            "JSON 对象结构严格为：{\"answer\": \"string\", \"meta\": {\"tool_used\": true 或 false, \"tool_type\": \"sensor_query\" 或 \"led_control\" 或 \"none\"}}。" +
                            "其中：answer 是要展示给用户的自然语言回答；如果你没有使用任何外部工具或 IoT 数据，就将 meta.tool_used 设为 false，meta.tool_type 设为 \"none\"。";
            enhancedSystemPrompt = (enhancedSystemPrompt != null ? enhancedSystemPrompt : "") + jsonProtocolInstruction;

            // 调用AI服务（使用Agent的API配置）
            String modelRawResponse;
            if (request.getMessages() != null && !request.getMessages().isEmpty()) {
                // 带历史消息
                modelRawResponse = aiService.chatWithHistoryAndConfig(enhancedSystemPrompt, request.getMessages(), pluginsJson, apiConfig);
            } else {
                // 单条消息
                modelRawResponse = aiService.chatWithConfig(enhancedSystemPrompt, request.getMessage(), pluginsJson, apiConfig);
            }

            // 从模型 JSON 响应中提取要返回给前端的内容（answer 字段）
            String finalAnswer = modelRawResponse;
            try {
                if (modelRawResponse != null && !modelRawResponse.isEmpty()) {
                    JsonNode root = objectMapper.readTree(modelRawResponse);
                    if (root.has("answer") && !root.get("answer").isNull()) {
                        finalAnswer = root.get("answer").asText();
                    }
                }
            } catch (Exception parseEx) {
                System.err.println("解析模型 JSON 响应失败，直接返回原始内容: " + parseEx.getMessage());
            }

            // 构建响应
            Map<String, Object> data = new HashMap<>();
            data.put("agentId", agentId);
            data.put("agentName", agent.getName());
            data.put("response", finalAnswer);
            data.put("modelRawResponse", modelRawResponse);
            // 如果存在直接插件调用的原始响应，一并返回给前端，便于前端展示或二次处理
            if (pluginDirectResponse != null) {
                data.put("pluginRawResponse", pluginDirectResponse);
            }
            // 返回作为上下文注入给模型的插件上下文（如果有）
            if (pluginContext != null) {
                data.put("pluginContext", pluginContext);
            }
            data.put("timestamp", new Date());

            return ApiResponse.success(data);
        } catch (Exception e) {
            System.err.println("Agent聊天失败: " + e.getMessage());
            e.printStackTrace();
            return ApiResponse.fail(500, "聊天服务异常: " + e.getMessage());
        }
    }

    /**
     * 直接对外暴露：查询指定 Agent 插件所配置的 IoT 插件的传感器数据并返回原始响应
     */
    @GetMapping("/{agentId}/plugin/sensor")
    public ApiResponse pluginSensor(@PathVariable Long agentId,
                                    @RequestParam(value = "uuid", required = false) String uuid,
                                    @RequestParam(value = "sensor") String sensor) {
        try {
            Agent agent = agentService.getAgentById(agentId);
            if (agent == null) {
                return ApiResponse.fail(404, "Agent不存在");
            }
            String pluginsJson = agent.getPluginsJson();
            String resp = iotPluginExecutor.sensorQuery(pluginsJson, uuid, sensor);
            if (resp == null) {
                return ApiResponse.fail(500, "调用插件传感器接口失败或无响应");
            }
            // 返回原始字符串（通常为 JSON）
            Map<String, Object> data = new HashMap<>();
            data.put("raw", resp);
            return ApiResponse.success(data);
        } catch (Exception e) {
            return ApiResponse.fail(500, "调用插件传感器接口异常: " + e.getMessage());
        }
    }

    /**
     * 直接对外暴露：控制指定 Agent 插件所配置的 IoT 插件的设备（例如打开/关闭 LED）
     */
    @PostMapping("/{agentId}/plugin/control")
    public ApiResponse pluginControl(@PathVariable Long agentId, @RequestBody Map<String, Object> body) {
        try {
            Agent agent = agentService.getAgentById(agentId);
            if (agent == null) {
                return ApiResponse.fail(404, "Agent不存在");
            }
            String pluginsJson = agent.getPluginsJson();
            String jsonBody = objectMapper.writeValueAsString(body);
            String resp = iotPluginExecutor.controlDevice(pluginsJson, jsonBody);
            if (resp == null) {
                return ApiResponse.fail(500, "调用插件控制接口失败或无响应");
            }
            Map<String, Object> data = new HashMap<>();
            data.put("raw", resp);
            return ApiResponse.success(data);
        } catch (Exception e) {
            return ApiResponse.fail(500, "调用插件控制接口异常: " + e.getMessage());
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

