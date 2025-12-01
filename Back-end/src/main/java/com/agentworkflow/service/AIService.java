package com.agentworkflow.service;

import java.util.List;
import java.util.Map;

/**
 * AI服务接口 - 用于调用大语言模型API
 */
public interface AIService {

    /**
     * 发送聊天消息并获取AI响应（使用默认API配置）
     * @param systemPrompt 系统提示词
     * @param userMessage 用户消息
     * @param plugins 插件配置（JSON格式）
     * @return AI响应内容
     */
    String chat(String systemPrompt, String userMessage, String plugins);

    /**
     * 发送聊天消息并获取AI响应（带历史消息，使用默认API配置）
     * @param systemPrompt 系统提示词
     * @param messages 历史消息列表
     * @param plugins 插件配置（JSON格式）
     * @return AI响应内容
     */
    String chatWithHistory(String systemPrompt, List<Map<String, String>> messages, String plugins);

    /**
     * 发送聊天消息并获取AI响应（使用自定义API配置）
     * @param systemPrompt 系统提示词
     * @param userMessage 用户消息
     * @param plugins 插件配置（JSON格式）
     * @param apiConfig 自定义API配置（可包含api-key等）
     * @return AI响应内容
     */
    String chatWithConfig(String systemPrompt, String userMessage, String plugins, String apiConfig);

    /**
     * 发送聊天消息并获取AI响应（带历史消息，使用自定义API配置）
     * @param systemPrompt 系统提示词
     * @param messages 历史消息列表
     * @param plugins 插件配置（JSON格式）
     * @param apiConfig 自定义API配置（可包含api-key等）
     * @return AI响应内容
     */
    String chatWithHistoryAndConfig(String systemPrompt, List<Map<String, String>> messages, String plugins, String apiConfig);
}

