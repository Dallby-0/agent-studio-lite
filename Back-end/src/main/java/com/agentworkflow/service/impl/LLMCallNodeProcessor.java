package com.agentworkflow.service.impl;

import com.agentworkflow.entity.WorkflowNode;
import com.agentworkflow.entity.WorkflowInstance;
import com.agentworkflow.service.AIService;
import com.agentworkflow.service.NodeProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.HashMap;

@Component
public class LLMCallNodeProcessor implements NodeProcessor {

    private static final Logger logger = LoggerFactory.getLogger(LLMCallNodeProcessor.class);

    @Autowired
    private AIService aiService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getNodeType() {
        return "llm_call";
    }

    @Override
    public Map<String, Object> process(WorkflowNode node, WorkflowInstance instance, Map<String, Object> context) {
        logger.debug("执行LLM调用节点，实例: {}, 节点: {}, 上下文: {}", 
                    instance.getId(), node.getId(), context);
        
        Map<String, Object> result = new HashMap<>();
        try {
            // 解析节点配置
            logger.debug("解析节点配置，配置JSON: {}", node.getConfigJson());
            Map<String, Object> nodeConfig = objectMapper.readValue(node.getConfigJson(), Map.class);
            logger.debug("解析完成节点配置: {}", nodeConfig);
            
            // 获取系统提示词和用户提示词模板
            String systemPrompt = (String) nodeConfig.getOrDefault("systemPrompt", "");
            String userPromptTemplate = (String) nodeConfig.getOrDefault("userPrompt", "");
            String plugins = (String) nodeConfig.getOrDefault("plugins", "");
            logger.debug("获取到提示词配置，系统提示词: {}, 用户提示词模板: {}, 插件: {}", 
                        systemPrompt, userPromptTemplate, plugins);
            
            // 替换提示词模板中的变量
            String userPrompt = replaceVariables(userPromptTemplate, context);
            logger.debug("替换变量后用户提示词: {}", userPrompt);
            
            // 调用AI服务
            logger.debug("开始调用AI服务，实例: {}, 节点: {}", instance.getId(), node.getId());
            String aiResponse = aiService.chat(systemPrompt, userPrompt, plugins);
            logger.debug("AI服务调用完成，响应: {}", aiResponse);
            
            // 将AI响应添加到上下文中
            Map<String, Object> outputData = new HashMap<>(context);
            outputData.put("aiResponse", aiResponse);
            // 如果配置了输出变量名，则使用配置的名称
            String outputVar = (String) nodeConfig.getOrDefault("outputVar", "aiResponse");
            outputData.put(outputVar, aiResponse);
            logger.debug("已将AI响应添加到上下文，输出变量: {}, 上下文: {}", outputVar, outputData);
            
            result.put("outputData", outputData);
            result.put("status", "success");
            logger.debug("LLM调用节点执行成功，实例: {}, 节点: {}, 结果: {}", 
                        instance.getId(), node.getId(), result);
        } catch (Exception e) {
            logger.error("LLM调用节点执行失败，实例: {}, 节点: {}, 错误: {}", 
                        instance.getId(), node.getId(), e.getMessage(), e);
            result.put("status", "failed");
            result.put("errorMessage", e.getMessage());
            logger.debug("LLM调用节点执行失败，实例: {}, 节点: {}, 结果: {}", 
                        instance.getId(), node.getId(), result);
        }
        return result;
    }
    
    /**
     * 替换提示词模板中的变量
     * @param template 模板字符串
     * @param context 上下文变量
     * @return 替换后的字符串
     */
    private String replaceVariables(String template, Map<String, Object> context) {
        String result = template;
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            result = result.replace(placeholder, String.valueOf(entry.getValue()));
        }
        return result;
    }
}
