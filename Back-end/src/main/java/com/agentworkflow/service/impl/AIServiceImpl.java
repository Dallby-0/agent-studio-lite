package com.agentworkflow.service.impl;

import com.agentworkflow.service.AIService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
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

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

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
            List<Map<String, Object>> allMessages = new ArrayList<>();

            // 添加系统提示词
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                allMessages.add(Map.of("role", "system", "content", systemPrompt));
            }

            // 添加历史消息
            for (Map<String, String> msg : messages) {
                allMessages.add(new HashMap<>(msg));
            }

            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", useModel);
            requestBody.put("messages", allMessages);
            requestBody.put("max_tokens", useMaxTokens);
            requestBody.put("temperature", useTemperature);

            // 转换OpenAPI3插件为DeepSeek支持的tools格式
            if (plugins != null && !plugins.isEmpty()) {
                System.out.println("原始插件配置: " + plugins);
                List<Map<String, Object>> tools = convertPluginsToTools(plugins);
                if (!tools.isEmpty()) {
                    System.out.println("转换后的tools: " + objectMapper.writeValueAsString(tools));
                    requestBody.put("tools", tools);
                    requestBody.put("tool_choice", "auto");
                }
            }

            String requestJson = objectMapper.writeValueAsString(requestBody);
            HttpEntity<String> entity = new HttpEntity<>(requestJson, headers);

            System.out.println("发送AI请求: " + url);
            System.out.println("使用API密钥: " + useApiKey.substring(0, Math.min(10, useApiKey.length())) + "...");
            System.out.println("完整AI请求体: " + requestJson);

            // 发送请求
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            // 解析响应
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                System.out.println("完整AI响应: " + response.getBody());
                JsonNode jsonNode = objectMapper.readTree(response.getBody());
                JsonNode choices = jsonNode.get("choices");
                if (choices != null && choices.isArray() && choices.size() > 0) {
                    JsonNode choice = choices.get(0);
                    JsonNode message = choice.get("message");
                    String finishReason = choice.get("finish_reason").asText();

                    System.out.println("AI响应finish_reason: " + finishReason);
                    System.out.println("AI响应message: " + objectMapper.writeValueAsString(message));

                    // 检查是否有tool_calls
                    if ("tool_calls".equals(finishReason) && message.has("tool_calls")) {
                        JsonNode toolCalls = message.get("tool_calls");
                        System.out.println("AI响应包含tool_calls: " + objectMapper.writeValueAsString(toolCalls));
                        if (toolCalls.isArray() && toolCalls.size() > 0) {
                            // 执行tool_calls
                            return handleToolCalls(toolCalls, allMessages, message, useApiKey, useBaseUrl, useModel, useMaxTokens, useTemperature, plugins);
                        }
                    }

                    // 如果没有tool_calls，直接返回内容
                    if (message != null && message.has("content")) {
                        System.out.println("AI直接返回内容: " + message.get("content").asText());
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
     * 处理AI返回的tool_calls
     */
    private String handleToolCalls(JsonNode toolCalls, List<Map<String, Object>> allMessages, JsonNode assistantMessage, 
                                  String apiKey, String baseUrl, String model, int maxTokens, double temperature, String plugins) throws Exception {
        System.out.println("开始处理tool_calls");
        // 将assistant消息添加到历史记录
        Map<String, Object> assistantMsgMap = new HashMap<>();
        assistantMsgMap.put("role", "assistant");
        if (assistantMessage.has("content")) {
            assistantMsgMap.put("content", assistantMessage.get("content").asText());
        }
        assistantMsgMap.put("tool_calls", objectMapper.readValue(toolCalls.toString(), List.class));
        System.out.println("添加assistant消息到历史记录: " + objectMapper.writeValueAsString(assistantMsgMap));
        allMessages.add(assistantMsgMap);

        // 执行每个tool_call
        for (JsonNode toolCall : toolCalls) {
            String toolCallId = toolCall.get("id").asText();
            String functionName = toolCall.get("function").get("name").asText();
            String argumentsJson = toolCall.get("function").get("arguments").asText();

            System.out.println("执行tool_call: " + functionName);
            System.out.println("tool_call_id: " + toolCallId);
            System.out.println("参数: " + argumentsJson);

            // 执行HTTP请求
            String toolResult = executeToolCall(functionName, argumentsJson, plugins);
            System.out.println("tool_call执行结果: " + toolResult);

            // 添加tool结果到历史记录
            Map<String, Object> toolResultMsg = new HashMap<>();
            toolResultMsg.put("role", "tool");
            toolResultMsg.put("content", toolResult);
            toolResultMsg.put("tool_call_id", toolCallId);
            System.out.println("添加tool结果到历史记录: " + objectMapper.writeValueAsString(toolResultMsg));
            allMessages.add(toolResultMsg);
        }

        // 再次调用AI，获取最终响应
        System.out.println("准备再次调用AI，获取最终响应");
        System.out.println("当前消息历史: " + objectMapper.writeValueAsString(allMessages));
        return callAIAgain(allMessages, apiKey, baseUrl, model, maxTokens, temperature);
    }

    /**
     * 执行tool_call对应的HTTP请求
     */
    private String executeToolCall(String functionName, String argumentsJson, String plugins) throws Exception {
        System.out.println("=== 开始执行tool_call ===");
        System.out.println("functionName: " + functionName);
        System.out.println("argumentsJson: " + argumentsJson);
        System.out.println("plugins: " + plugins);
        
        // 解析参数
        JsonNode args = objectMapper.readTree(argumentsJson);
        System.out.println("解析后的参数: " + args.toString());
        
        // 解析插件配置，获取operationId对应的URL和方法
        JsonNode pluginsNode = objectMapper.readTree(plugins);
        JsonNode pathsNode = pluginsNode.get("paths");
        System.out.println("解析插件配置成功，paths数量: " + (pathsNode != null ? pathsNode.size() : 0));
        
        // 查找对应的path和method
        String url = null;
        String method = null;
        String serverUrl = null;
        
        // 获取服务器URL
        if (pluginsNode.has("servers") && pluginsNode.get("servers").isArray() && pluginsNode.get("servers").size() > 0) {
            serverUrl = pluginsNode.get("servers").get(0).get("url").asText();
            System.out.println("获取到服务器URL: " + serverUrl);
        } else {
            System.out.println("未找到servers配置");
        }
        
        // 遍历所有paths，查找对应的operationId
        if (pathsNode != null) {
            for (Iterator<String> pathIterator = pathsNode.fieldNames(); pathIterator.hasNext();) {
                String path = pathIterator.next();
                JsonNode pathNode = pathsNode.get(path);
                System.out.println("检查path: " + path);
                
                // 遍历所有HTTP方法
                for (Iterator<String> methodIterator = pathNode.fieldNames(); methodIterator.hasNext();) {
                    String httpMethod = methodIterator.next();
                    JsonNode operationNode = pathNode.get(httpMethod);
                    System.out.println("  检查method: " + httpMethod);
                    
                    // 检查operationId是否匹配
                    if (operationNode.has("operationId")) {
                        String opId = operationNode.get("operationId").asText();
                        System.out.println("  operationId: " + opId);
                        if (opId.equals(functionName)) {
                            method = httpMethod;
                            url = path;
                            System.out.println("  匹配成功！method: " + method + ", url: " + url);
                            break;
                        }
                    }
                }
                
                if (url != null && method != null) {
                    break;
                }
            }
        }
        
        if (url == null || method == null) {
            System.out.println("未找到对应的API配置: " + functionName);
            Map<String, Object> result = new HashMap<>();
            result.put("code", 404);
            result.put("msg", "未找到对应的API配置: " + functionName);
            return objectMapper.writeValueAsString(result);
        }
        
        // 构建完整的URL
        String fullUrl = serverUrl + url;
        System.out.println("构建完整URL: " + fullUrl);
        
        // 执行HTTP请求
        ResponseEntity<String> response;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        System.out.println("请求头: " + headers.toString());
        
        try {
            if ("get".equalsIgnoreCase(method)) {
                // GET请求，将参数作为查询参数
                StringBuilder queryString = new StringBuilder();
                for (Iterator<String> fieldNames = args.fieldNames(); fieldNames.hasNext();) {
                    String fieldName = fieldNames.next();
                    String fieldValue = args.get(fieldName).asText();
                    if (queryString.length() == 0) {
                        queryString.append("?");
                    } else {
                        queryString.append("&");
                    }
                    queryString.append(fieldName).append("=");
                    queryString.append(fieldValue);
                }
                
                if (queryString.length() > 0) {
                    fullUrl += queryString.toString();
                    System.out.println("添加查询参数后URL: " + fullUrl);
                }
                
                HttpEntity<String> entity = new HttpEntity<>(headers);
                System.out.println("准备发送GET请求: " + fullUrl);
                response = restTemplate.exchange(fullUrl, HttpMethod.GET, entity, String.class);
            } else {
                // POST/PUT请求，将参数作为请求体
                String requestBody = argumentsJson;
                HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
                String methodUpper = method.toUpperCase();
                System.out.println("准备发送" + methodUpper + "请求: " + fullUrl);
                System.out.println("请求体: " + requestBody);
                
                // 使用HttpMethod.valueOf()替代HttpMethod.resolve()
                HttpMethod httpMethod = HttpMethod.valueOf(methodUpper);
                response = restTemplate.exchange(fullUrl, httpMethod, entity, String.class);
            }
            
            // 输出响应信息
            System.out.println("HTTP请求执行成功");
            System.out.println("响应状态码: " + response.getStatusCode());
            System.out.println("响应头: " + response.getHeaders());
            System.out.println("响应体: " + response.getBody());
            
            // 返回响应结果
            return response.getBody();
        } catch (Exception e) {
            // 处理请求异常
            System.out.println("HTTP请求执行失败: " + e.getMessage());
            e.printStackTrace();
            Map<String, Object> result = new HashMap<>();
            result.put("code", 500);
            result.put("msg", "HTTP请求执行失败: " + e.getMessage());
            return objectMapper.writeValueAsString(result);
        } finally {
            System.out.println("=== tool_call执行结束 ===");
        }
    }

    /**
     * 再次调用AI，获取最终响应
     */
    private String callAIAgain(List<Map<String, Object>> messages, String apiKey, String baseUrl, 
                              String model, int maxTokens, double temperature) throws Exception {
        String url = baseUrl + "/v1/chat/completions";

        // 构建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        // 构建请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("max_tokens", maxTokens);
        requestBody.put("temperature", temperature);

        String requestJson = objectMapper.writeValueAsString(requestBody);
        HttpEntity<String> entity = new HttpEntity<>(requestJson, headers);

        System.out.println("再次调用AI获取最终响应");
        System.out.println("最终AI请求体: " + requestJson);

        // 发送请求
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        System.out.println("最终AI响应: " + response.getBody());

        // 解析响应
        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            JsonNode choices = jsonNode.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode message = choices.get(0).get("message");
                if (message != null && message.has("content")) {
                    System.out.println("AI最终响应内容: " + message.get("content").asText());
                    return message.get("content").asText();
                }
            }
        }

        return "抱歉，无法获取AI最终响应。";
    }

    /**
     * 将OpenAPI3格式的插件转换为DeepSeek支持的tools格式
     */
    private List<Map<String, Object>> convertPluginsToTools(String plugins) throws Exception {
        List<Map<String, Object>> tools = new ArrayList<>();

        // 解析OpenAPI3规范的JSON
        JsonNode pluginsNode = objectMapper.readTree(plugins);
        
        // 获取paths节点
        JsonNode pathsNode = pluginsNode.get("paths");
        if (pathsNode != null && pathsNode.isObject()) {
            // 遍历所有paths
            for (Iterator<String> pathIterator = pathsNode.fieldNames(); pathIterator.hasNext();) {
                String path = pathIterator.next();
                JsonNode pathNode = pathsNode.get(path);
                
                // 遍历所有HTTP方法
                for (Iterator<String> methodIterator = pathNode.fieldNames(); methodIterator.hasNext();) {
                    String method = methodIterator.next();
                    JsonNode operationNode = pathNode.get(method);
                    
                    // 获取operationId作为function name
                    String operationId = operationNode.has("operationId") ? operationNode.get("operationId").asText() : "";
                    if (operationId.isEmpty()) {
                        continue;
                    }
                    
                    // 获取description
                    String description = operationNode.has("description") ? operationNode.get("description").asText() : "";
                    
                    // 获取请求体或查询参数，转换为parameters
                    Map<String, Object> parameters = new HashMap<>();
                    
                    // 处理请求体参数（POST/PUT请求）
                    if (operationNode.has("requestBody")) {
                        JsonNode requestBodyNode = operationNode.get("requestBody");
                        JsonNode contentNode = requestBodyNode.get("content");
                        if (contentNode != null && contentNode.has("application/json")) {
                            JsonNode schemaNode = contentNode.get("application/json").get("schema");
                            if (schemaNode != null) {
                                parameters = convertSchemaToParameters(schemaNode);
                            }
                        }
                    } 
                    // 处理查询参数（GET请求）
                    else if (operationNode.has("parameters")) {
                        JsonNode paramsNode = operationNode.get("parameters");
                        if (paramsNode != null && paramsNode.isArray()) {
                            parameters = convertQueryParamsToSchema(paramsNode);
                        }
                    }
                    
                    if (!parameters.isEmpty()) {
                        // 创建function map
                        Map<String, Object> functionMap = new HashMap<>();
                        functionMap.put("name", operationId);
                        functionMap.put("description", description);
                        functionMap.put("parameters", parameters);

                        // 创建tool map
                        Map<String, Object> toolMap = new HashMap<>();
                        toolMap.put("type", "function");
                        toolMap.put("function", functionMap);
                        tools.add(toolMap);
                    }
                }
            }
        }

        return tools;
    }
    
    /**
     * 将OpenAPI3 schema转换为DeepSeek工具参数格式
     */
    private Map<String, Object> convertSchemaToParameters(JsonNode schemaNode) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        List<Object> required = new ArrayList<>();
        
        // 处理properties
        if (schemaNode.has("properties")) {
            JsonNode propertiesNode = schemaNode.get("properties");
            for (Iterator<String> propIterator = propertiesNode.fieldNames(); propIterator.hasNext();) {
                String propName = propIterator.next();
                JsonNode propNode = propertiesNode.get(propName);
                
                Map<String, Object> property = new HashMap<>();
                
                // 处理类型
                if (propNode.has("type")) {
                    property.put("type", propNode.get("type").asText());
                }
                
                // 处理描述
                if (propNode.has("description")) {
                    property.put("description", propNode.get("description").asText());
                }
                
                // 处理枚举值
                if (propNode.has("enum")) {
                    JsonNode enumNode = propNode.get("enum");
                    List<Object> enumValues = new ArrayList<>();
                    for (JsonNode enumItem : enumNode) {
                        enumValues.add(enumItem.asText());
                    }
                    property.put("enum", enumValues);
                }
                
                // 处理示例
                if (propNode.has("example")) {
                    property.put("example", propNode.get("example").asText());
                }
                
                // 处理最小值
                if (propNode.has("minimum")) {
                    property.put("minimum", propNode.get("minimum").asInt());
                }
                
                // 处理最大值
                if (propNode.has("maximum")) {
                    property.put("maximum", propNode.get("maximum").asInt());
                }
                
                // 处理additionalProperties
                if (propNode.has("additionalProperties")) {
                    property.put("additionalProperties", propNode.get("additionalProperties").asBoolean());
                }
                
                properties.put(propName, property);
            }
        }
        
        // 处理required
        if (schemaNode.has("required")) {
            JsonNode requiredNode = schemaNode.get("required");
            if (requiredNode != null && requiredNode.isArray()) {
                for (JsonNode reqItem : requiredNode) {
                    required.add(reqItem.asText());
                }
            }
        }
        
        parameters.put("properties", properties);
        if (!required.isEmpty()) {
            parameters.put("required", required);
        }
        
        return parameters;
    }
    
    /**
     * 将OpenAPI3查询参数转换为schema格式
     */
    private Map<String, Object> convertQueryParamsToSchema(JsonNode paramsNode) {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        List<Object> required = new ArrayList<>();
        
        for (JsonNode paramNode : paramsNode) {
            String paramName = paramNode.get("name").asText();
            String in = paramNode.get("in").asText();
            
            // 只处理query参数
            if (!"query".equals(in)) {
                continue;
            }
            
            Map<String, Object> property = new HashMap<>();
            
            // 处理类型
            JsonNode schemaNode = paramNode.get("schema");
            if (schemaNode.has("type")) {
                property.put("type", schemaNode.get("type").asText());
            }
            
            // 处理描述
            if (paramNode.has("description")) {
                property.put("description", paramNode.get("description").asText());
            }
            
            // 处理枚举值
            if (schemaNode.has("enum")) {
                JsonNode enumNode = schemaNode.get("enum");
                List<Object> enumValues = new ArrayList<>();
                for (JsonNode enumItem : enumNode) {
                    enumValues.add(enumItem.asText());
                }
                property.put("enum", enumValues);
            }
            
            // 处理示例
            if (schemaNode.has("example")) {
                property.put("example", schemaNode.get("example").asText());
            }
            
            properties.put(paramName, property);
            
            // 处理required
            if (paramNode.has("required") && paramNode.get("required").asBoolean()) {
                required.add(paramName);
            }
        }
        
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        
        return schema;
    }
}

