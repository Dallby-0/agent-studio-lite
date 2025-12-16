package com.agentworkflow.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 历史对话消息管理器
 * 用于管理工作流实例中的历史对话消息
 */
public class ChatHistoryManager {
    
    /**
     * 历史对话消息存储结构
     * key: 历史对话的key（如"default"）
     * value: 该key对应的历史消息列表，每个元素是一个包含昵称和内容的Map
     */
    private Map<String, List<Map<String, String>>> historyMap = new HashMap<>();
    
    /**
     * 添加历史对话消息
     * @param historyKey 历史对话的key（默认为"default"）
     * @param nickname 对话界面昵称
     * @param content 消息内容
     */
    public void addMessage(String historyKey, String nickname, String content) {
        if (historyKey == null || historyKey.trim().isEmpty()) {
            historyKey = "default";
        }
        
        // 获取或创建该key对应的历史消息列表
        List<Map<String, String>> historyList = historyMap.computeIfAbsent(historyKey, k -> new ArrayList<>());
        
        // 创建消息对象
        Map<String, String> message = new HashMap<>();
        message.put("nickname", nickname != null ? nickname : "");
        message.put("content", content != null ? content : "");
        
        // 添加到列表末尾
        historyList.add(message);
        
        System.out.println("已添加历史对话消息，key: " + historyKey + ", 昵称: " + nickname + ", 内容长度: " + 
            (content != null ? content.length() : 0));
    }
    
    /**
     * 获取指定key的历史对话消息，并组装为AI服务所需的格式
     * @param historyKey 历史对话的key
     * @param currentNickname 当前节点的昵称（用于判断role）
     * @return 组装后的消息列表，格式为List<Map<String, String>>，每个Map包含role和content
     */
    public List<Map<String, String>> getHistoryMessages(String historyKey, String currentNickname) {
        if (historyKey == null || historyKey.trim().isEmpty()) {
            historyKey = "default";
        }
        
        List<Map<String, String>> historyList = historyMap.get(historyKey);
        if (historyList == null || historyList.isEmpty()) {
            System.out.println("历史对话消息为空，key: " + historyKey);
            return new ArrayList<>();
        }
        
        List<Map<String, String>> messages = new ArrayList<>();
        for (Map<String, String> historyMessage : historyList) {
            String nickname = historyMessage.get("nickname");
            String content = historyMessage.get("content");
            
            // 判断role：如果昵称与当前节点相同，则为assistant，否则为user
            String role = (nickname != null && nickname.equals(currentNickname)) ? "assistant" : "user";
            
            // 格式化内容：只有user角色才需要添加"[xxx]说: "前缀，assistant角色直接使用原始内容
            String formattedContent;
            if ("assistant".equals(role)) {
                // assistant角色：直接使用原始内容，不添加"xxx说"格式
                formattedContent = content != null ? content : "";
            } else {
                // user角色：包装内容为 [xxx]说: 内容
                formattedContent = formatMessageContent(nickname, content);
            }
            
            Map<String, String> message = new HashMap<>();
            message.put("role", role);
            message.put("content", formattedContent);
            messages.add(message);
        }
        
        System.out.println("已组装历史对话消息，key: " + historyKey + ", 消息数量: " + messages.size());
        return messages;
    }
    
    /**
     * 格式化消息内容为 [xxx]说: 内容
     * @param nickname 昵称
     * @param content 内容
     * @return 格式化后的内容
     */
    private String formatMessageContent(String nickname, String content) {
        if (nickname == null || nickname.trim().isEmpty()) {
            nickname = "未知";
        }
        return "[" + nickname + "]说: " + (content != null ? content : "");
    }
    
    /**
     * 获取指定key的历史消息数量
     * @param historyKey 历史对话的key
     * @return 消息数量
     */
    public int getHistoryCount(String historyKey) {
        if (historyKey == null || historyKey.trim().isEmpty()) {
            historyKey = "default";
        }
        List<Map<String, String>> historyList = historyMap.get(historyKey);
        return historyList != null ? historyList.size() : 0;
    }
    
    /**
     * 清除指定key的历史消息
     * @param historyKey 历史对话的key
     */
    public void clearHistory(String historyKey) {
        if (historyKey == null || historyKey.trim().isEmpty()) {
            historyKey = "default";
        }
        historyMap.remove(historyKey);
        System.out.println("已清除历史对话消息，key: " + historyKey);
    }
    
    /**
     * 清除所有历史消息
     */
    public void clearAll() {
        historyMap.clear();
        System.out.println("已清除所有历史对话消息");
    }
    
    /**
     * 获取所有历史对话的key列表
     * @return key列表
     */
    public List<String> getAllKeys() {
        return new ArrayList<>(historyMap.keySet());
    }
}

