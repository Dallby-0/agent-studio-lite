package com.agentworkflow.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.agentworkflow.service.AIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * IoT 插件执行器 MVP：
 * - 从 pluginsJson (OpenAPI JSON) 中解析 baseUrl
 * - 根据用户问题决定是否调用传感器查询或 LED 控制接口
 * - 调用真实 IoT 后端并返回原始 JSON 文本，供上层拼接到 system prompt
 */
@Service
public class IotPluginExecutor {

    @Value("${ai.iot.default-device-uuid:test}")
    private String defaultDeviceUuid;

    @Autowired
    private AIService aiService;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 兼容老接口：不带 apiConfig 时，只使用本地规则
     */
    public String buildPluginContext(String pluginsJson, String question) {
        return buildPluginContext(pluginsJson, question, null, null);
    }

    /**
     * 根据当前问题和 Agent 的 API 配置，优先使用 LLM 输出 JSON 计划，再按计划调用 IoT 接口
     */
    public String buildPluginContext(String pluginsJson, String question, String apiConfig) {
        return buildPluginContext(pluginsJson, question, apiConfig, null);
    }

    /**
     * 根据当前问题、Agent 的 API 配置和系统提示词，调用 IoT 接口
     */
    public String buildPluginContext(String pluginsJson, String question, String apiConfig, String systemPrompt) {
        if (pluginsJson == null || pluginsJson.isEmpty() || question == null || question.isEmpty()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(pluginsJson);
            String baseUrl = extractBaseUrl(root);
            if (baseUrl == null || baseUrl.isEmpty()) {
                return null;
            }

            // 1. 先尝试用 LLM 规划调用（函数调用 / 工具调用模式）
            String planContext = buildPluginContextByPlan(pluginsJson, baseUrl, question, apiConfig, systemPrompt);
            if (planContext != null && !planContext.isEmpty()) {
                return planContext;
            }

            // 2. 回退到本地规则
            return buildPluginContextByRules(baseUrl, question, systemPrompt);
        } catch (Exception e) {
            System.err.println("IotPluginExecutor 调用失败: " + e.getMessage());
        }
        return null;
    }

    private String extractBaseUrl(JsonNode root) {
        if (root == null) return null;
        JsonNode servers = root.path("servers");
        if (servers.isArray() && servers.size() > 0) {
            JsonNode first = servers.get(0);
            if (first.has("url")) {
                String url = first.get("url").asText();
                return (url == null || url.isEmpty()) ? null : url;
            }
        }
        return null;
    }

    /**
     * 本地关键词规则：不经过大模型规划，直接根据问题判断调用哪个接口
     */
    private String buildPluginContextByRules(String baseUrl, String question, String systemPrompt) {
        String q = question.trim();

        // 简单规则：判断是否是传感器查询
        if (isSensorQuestion(q)) {
            String resp = callSensorApi(baseUrl, q, systemPrompt);
            if (resp != null && !resp.isEmpty()) {
                return "IoT 插件传感器查询原始响应: " + resp;
            }
        }

        // 简单规则：判断是否是 LED 控制
        if (isLedControlQuestion(q)) {
            String resp = callLedControlApi(baseUrl, q, systemPrompt);
            if (resp != null && !resp.isEmpty()) {
                return "IoT 插件控制指令原始响应: " + resp;
            }
        }
        return null;
    }

    /**
     * 使用大模型输出 JSON 计划，然后按计划决定是否调用 IoT 接口
     */
    private String buildPluginContextByPlan(String pluginsJson, String baseUrl, String question, String apiConfig, String systemPrompt) {
        if (aiService == null) {
            return null;
        }
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("你是一名 IoT 插件调用规划器。")
                    .append("下面是一个 IoT HTTP 插件的 OpenAPI 3.0 说明文档：\n")
                    .append(pluginsJson)
                    .append("\n\n根据用户的问题，判断是否需要调用该插件。")
                    .append("你只允许输出一个 JSON 对象，不能包含其它任何文字或解释。")
                    .append("JSON 对象格式严格为：")
                    .append("{\"use_plugin\": true 或 false, \"intent\": \"sensor_query\" 或 \"led_control\" 或 \"none\"}。")
                    .append("如果不需要调用插件，就输出 {\"use_plugin\": false, \"intent\": \"none\"}。");

            String plannerSystemPrompt = sb.toString();
            String rawPlan = aiService.chatWithConfig(plannerSystemPrompt, question, null, apiConfig);
            if (rawPlan == null || rawPlan.isEmpty()) {
                return null;
            }

            String jsonText = extractJsonFromText(rawPlan);
            JsonNode planNode = objectMapper.readTree(jsonText);
            boolean usePlugin = planNode.path("use_plugin").asBoolean(false);
            String intent = planNode.path("intent").asText("none");

            if (!usePlugin || "none".equals(intent)) {
                return null;
            }

            if ("sensor_query".equals(intent)) {
                String resp = callSensorApi(baseUrl, question, systemPrompt);
                if (resp != null && !resp.isEmpty()) {
                    return "IoT 插件传感器查询原始响应: " + resp;
                }
            } else if ("led_control".equals(intent)) {
                String resp = callLedControlApi(baseUrl, question, systemPrompt);
                if (resp != null && !resp.isEmpty()) {
                    return "IoT 插件控制指令原始响应: " + resp;
                }
            }
        } catch (Exception e) {
            System.err.println("IoT 插件 LLM 规划失败: " + e.getMessage());
        }
        return null;
    }

    /**
     * 从可能包含代码块或前后缀的文本中提取 JSON 子串
     */
    private String extractJsonFromText(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }



    private boolean isSensorQuestion(String q) {
        String lower = q.toLowerCase();
        return q.contains("温度") || lower.contains("temperature")
                || q.contains("湿度") || lower.contains("humidity")
                || q.contains("雨") || lower.contains("rain");
    }

    private boolean isLedControlQuestion(String q) {
        String lower = q.toLowerCase();
        return q.contains("LED") || lower.contains("led")
                || q.contains("灯") || q.contains("指示灯")
                || q.contains("继电器") || lower.contains("relay")
                || q.contains("舵机") || lower.contains("servo")
                || q.contains("PWM") || lower.contains("pwm");
    }

    private String detectSensorFromQuestion(String q) {
        String lower = q.toLowerCase();
        if (q.contains("温度") || lower.contains("temperature")) {
            return "temperature";
        }
        if (q.contains("湿度") || lower.contains("humidity")) {
            return "humidity";
        }
        if (q.contains("雨") || lower.contains("rain")) {
            if (q.contains("雨水级别") || lower.contains("rain_level")) {
                return "rain_level";
            }
            return "rain";
        }
        if (q.contains("DS18B20") || lower.contains("ds18b20")) {
            if (q.contains("DS18B20温度") || lower.contains("ds18b20温度")) {
                return "DS18B20温度";
            }
            return "DS18B20";
        }
        return null;
    }

    /**
     * 从问题中提取端口 ID：支持 LED1/LED2/LED3/LED4、继电器1-4、舵机1-4、PWM1-2
     */
    private int extractPortId(String q) {
        // 查找数字：LED1、LED2、LED3、LED4、继电器1、舵机2、PWM1 等
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("[LED继电器舵机PWMledpwm]+(\\d)");
        java.util.regex.Matcher m = p.matcher(q);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (Exception e) {
                return 1; // 默认值
            }
        }
        return 1; // 默认为端口 1
    }

    /**
     * 从问题中提取设备类型：led/relay/servo/pwm
     */
    private String extractPortType(String q) {
        String lower = q.toLowerCase();
        if (q.contains("继电器") || lower.contains("relay")) {
            return "relay";
        }
        if (q.contains("舵机") || lower.contains("servo")) {
            return "servo";
        }
        if (q.contains("PWM") || lower.contains("pwm")) {
            return "pwm";
        }
        // 默认为 LED
        return "led";
    }

    /**
     * 从问题中提取设置值：用于舵机角度(0-180)或PWM占空比(0-100)
     */
    private Integer extractValue(String q) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("(\\d{1,3})度|角度(\\d{1,3})|占空比(\\d{1,3})|(\\d{1,3})%");
        java.util.regex.Matcher m = p.matcher(q);
        if (m.find()) {
            for (int i = 1; i <= m.groupCount(); i++) {
                if (m.group(i) != null) {
                    try {
                        return Integer.parseInt(m.group(i));
                    } catch (Exception e) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    private String callSensorApi(String baseUrl, String question, String systemPrompt) {
        String sensor = detectSensorFromQuestion(question);
        if (sensor == null) return null;
        try {
            String url = baseUrl + "/plugin/sensor-data";
            // 优先从 question 提取 UUID，再从 systemPrompt 提取，最后使用默认值
            String uuid = extractUuidFromText(question);
            if (uuid == null && systemPrompt != null) {
                uuid = extractUuidFromText(systemPrompt);
            }
            if (uuid == null) uuid = defaultDeviceUuid;
            String fullUrl = UriComponentsBuilder.fromHttpUrl(url)
                    .queryParam("uuid", uuid)
                    .queryParam("sensor", sensor)
                    .toUriString();
            System.out.println("调用 IoT 传感器接口 URL: " + fullUrl);
            ResponseEntity<String> resp = restTemplate.getForEntity(fullUrl, String.class);
            String body = resp.getBody();
            System.out.println("IoT 传感器接口响应: " + body);
            return body;
        } catch (Exception e) {
            System.err.println("调用 IoT 传感器接口失败: " + e.getMessage());
            return null;
        }
    }

    private String callLedControlApi(String baseUrl, String question, String systemPrompt) {
        try {
            String url = baseUrl + "/plugin/control";
            
            // 从问题中提取设备类型、端口 ID、动作和可选值
            String portType = extractPortType(question);
            int portId = extractPortId(question);
            Integer value = extractValue(question);
            
            String lower = question.toLowerCase();
            String action;
            if (question.contains("关") || lower.contains("off")) {
                action = "off";
            } else if (question.contains("打开") || question.contains("开") || lower.contains("on")) {
                action = "on";
            } else if (value != null && (portType.equals("servo") || portType.equals("pwm"))) {
                // 舵机或 PWM 设置值
                action = "set";
            } else {
                // 默认视为打开
                action = "on";
            }

            // 优先从 question 提取 UUID，再从 systemPrompt 提取，最后使用默认值
            String uuid = extractUuidFromText(question);
            if (uuid == null && systemPrompt != null) {
                uuid = extractUuidFromText(systemPrompt);
            }
            if (uuid == null) uuid = defaultDeviceUuid;

            // 构建 JSON body
            StringBuilder jsonBuilder = new StringBuilder();
            jsonBuilder.append("{\"device_uuid\":\"").append(uuid)
                    .append("\",\"port_type\":\"").append(portType)
                    .append("\",\"port_id\":").append(portId)
                    .append(",\"action\":\"").append(action).append("\"");
            
            // 如果有设置值，添加到 JSON
            if (value != null && action.equals("set")) {
                jsonBuilder.append(",\"value\":").append(value);
            }
            jsonBuilder.append("}");
            
            String jsonBody = jsonBuilder.toString();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

            System.out.println("调用 IoT 控制接口 URL: " + url + " BODY: " + jsonBody);
            ResponseEntity<String> resp = restTemplate.postForEntity(url, entity, String.class);
            String body = resp.getBody();
            System.out.println("IoT 控制接口响应: " + body);
            return body;
        } catch (Exception e) {
            System.err.println("调用 IoT 控制接口失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 从文本中提取 UUID（简单正则匹配）。
     */
    private String extractUuidFromText(String text) {
        if (text == null) return null;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
        java.util.regex.Matcher m = p.matcher(text);
        if (m.find()) {
            return m.group();
        }
        return null;
    }

    /**
     * Public wrapper: 从 pluginsJson 中提取 baseUrl 并调用传感器查询接口，返回原始响应字符串
     */
    public String sensorQuery(String pluginsJson, String uuid, String sensor) {
        if (pluginsJson == null || pluginsJson.isEmpty()) return null;
        try {
            JsonNode root = objectMapper.readTree(pluginsJson);
            String baseUrl = extractBaseUrl(root);
            if (baseUrl == null || baseUrl.isEmpty()) return null;
            String url = baseUrl + "/plugin/sensor-data";
            String fullUrl = UriComponentsBuilder.fromHttpUrl(url)
                    .queryParam("uuid", uuid != null ? uuid : defaultDeviceUuid)
                    .queryParam("sensor", sensor)
                    .toUriString();
            ResponseEntity<String> resp = restTemplate.getForEntity(fullUrl, String.class);
            return resp.getBody();
        } catch (Exception e) {
            System.err.println("sensorQuery 调用失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * Public wrapper: 从 pluginsJson 中提取 baseUrl 并调用控制接口，body 为 JSON 字符串
     */
    public String controlDevice(String pluginsJson, String jsonBody) {
        if (pluginsJson == null || pluginsJson.isEmpty()) return null;
        try {
            JsonNode root = objectMapper.readTree(pluginsJson);
            String baseUrl = extractBaseUrl(root);
            if (baseUrl == null || baseUrl.isEmpty()) return null;

            String url = baseUrl + "/plugin/control";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
            ResponseEntity<String> resp = restTemplate.postForEntity(url, entity, String.class);
            return resp.getBody();
        } catch (Exception e) {
            System.err.println("controlDevice 调用失败: " + e.getMessage());
            return null;
        }
    }
}

