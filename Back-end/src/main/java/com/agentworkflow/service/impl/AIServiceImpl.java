package com.agentworkflow.service.impl;

import com.agentworkflow.service.AIService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * AI服务实现类 - 调用DeepSeek API
 */
@Service
public class AIServiceImpl implements AIService {

    @Value("${ai.deepseek.api-key}")
    private String defaultApiKey;

    @Value("${ai.deepseek.base-url}")
    private String defaultBaseUrl;

    @Value("${ai.deepseek.model}")
    private String defaultModel;

    @Value("${ai.deepseek.max-tokens}")
    private int defaultMaxTokens;

    @Value("${ai.deepseek.temperature}")
    private double defaultTemperature;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 构造函数，初始化RestTemplate并设置超时
    public AIServiceImpl() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000); // 连接超时10秒
        factory.setReadTimeout(60000); // 读取超时60秒
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public String chat(String systemPrompt, String userMessage, String plugins) {
        return chatWithConfig(systemPrompt, userMessage, plugins, null);
    }

    @Override
    public String chatWithHistory(String systemPrompt, List<Map<String, String>> messages, String plugins) {
        return chatWithHistoryAndConfig(systemPrompt, messages, plugins, null);
    }

    @Override
    public String chatWithConfig(String systemPrompt, String userMessage, String plugins, String apiConfig) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", userMessage));
        return chatWithHistoryAndConfig(systemPrompt, messages, plugins, apiConfig);
    }

    @Override
    public String chatWithHistoryAndConfig(String systemPrompt, List<Map<String, String>> messages, String plugins, String apiConfig) {
        try {
            // 解析API配置，优先使用Agent配置，否则使用默认配置
            String useApiKey = defaultApiKey;
            String useBaseUrl = defaultBaseUrl;
            String useModel = defaultModel;
            int useMaxTokens = defaultMaxTokens;
            double useTemperature = defaultTemperature;

            if (apiConfig != null && !apiConfig.isEmpty()) {
                try {
                    JsonNode configNode = objectMapper.readTree(apiConfig);
                    if (configNode.has("apiKey") && !configNode.get("apiKey").asText().isEmpty()) {
                        useApiKey = configNode.get("apiKey").asText();
                    }
                    if (configNode.has("baseUrl") && !configNode.get("baseUrl").asText().isEmpty()) {
                        useBaseUrl = configNode.get("baseUrl").asText();
                    }
                    if (configNode.has("model") && !configNode.get("model").asText().isEmpty()) {
                        useModel = configNode.get("model").asText();
                    }
                    if (configNode.has("maxTokens")) {
                        useMaxTokens = configNode.get("maxTokens").asInt();
                    }
                    if (configNode.has("temperature")) {
                        useTemperature = configNode.get("temperature").asDouble();
                    }
                } catch (Exception e) {
                    // 如果解析失败，apiConfig可能只是一个API密钥字符串
                    if (apiConfig.startsWith("sk-")) {
                        useApiKey = apiConfig;
                    }
                    System.out.println("使用apiConfig作为API密钥: " + apiConfig.substring(0, Math.min(10, apiConfig.length())) + "...");
                }
            }

            String url = useBaseUrl + "/v1/chat/completions";

            // 构建请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(useApiKey);

            // 构建消息列表
            List<Map<String, String>> allMessages = new ArrayList<>();

            // 添加系统提示词（合并插件配置到系统提示词中）
            String fullSystemPrompt = buildSystemPrompt(systemPrompt, plugins);
            if (fullSystemPrompt != null && !fullSystemPrompt.isEmpty()) {
                allMessages.add(Map.of("role", "system", "content", fullSystemPrompt));
            }

            // 添加历史消息
            allMessages.addAll(messages);

            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", useModel);
            requestBody.put("messages", allMessages);
            requestBody.put("max_tokens", useMaxTokens);
            requestBody.put("temperature", useTemperature);

            String requestJson = objectMapper.writeValueAsString(requestBody);
            HttpEntity<String> entity = new HttpEntity<>(requestJson, headers);

            System.out.println("发送AI请求: " + url);
            System.out.println("使用API密钥: " + useApiKey.substring(0, Math.min(10, useApiKey.length())) + "...");
            System.out.println("系统提示词: " + fullSystemPrompt);
            System.out.println("消息数量: " + allMessages.size());

            // 发送请求
            System.out.println("开始发送HTTP请求...");
            long requestStartTime = System.currentTimeMillis();
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            long requestDuration = System.currentTimeMillis() - requestStartTime;
            System.out.println("HTTP请求完成，耗时: " + requestDuration + "ms");
            System.out.println("响应状态码: " + response.getStatusCode());

            // 解析响应
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode jsonNode = objectMapper.readTree(response.getBody());
                JsonNode choices = jsonNode.get("choices");
                if (choices != null && choices.isArray() && choices.size() > 0) {
                    JsonNode message = choices.get(0).get("message");
                    if (message != null && message.has("content")) {
                        return message.get("content").asText();
                    }
                }
            }

            return "抱歉，无法获取AI响应。";
        } catch (Exception e) {
            System.err.println("AI服务调用失败: " + e.getMessage());
            e.printStackTrace();
            return "抱歉，AI服务暂时不可用: " + e.getMessage();
        }
    }

    /**
     * 构建完整的系统提示词（包含插件配置）
     */
    private String buildSystemPrompt(String systemPrompt, String plugins) {
        StringBuilder sb = new StringBuilder();
        
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            sb.append(systemPrompt);
        }
        
        // 解析并添加插件信息
        if (plugins != null && !plugins.isEmpty()) {
            try {
                JsonNode pluginsNode = objectMapper.readTree(plugins);
                if (pluginsNode.isArray() && pluginsNode.size() > 0) {
                    sb.append("\n\n你可以使用以下插件能力：\n");
                    for (JsonNode plugin : pluginsNode) {
                        String name = plugin.has("name") ? plugin.get("name").asText() : "未知插件";
                        String desc = plugin.has("description") ? plugin.get("description").asText() : "";
                        sb.append("- ").append(name).append(": ").append(desc).append("\n");
                    }
                }
            } catch (Exception e) {
                System.err.println("解析插件配置失败: " + e.getMessage());
            }
        }
        
        return sb.toString();
    }
}

