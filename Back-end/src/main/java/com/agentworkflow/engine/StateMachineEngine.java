package com.agentworkflow.engine;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import com.agentworkflow.entity.StateWorkflowDefinition;
import com.agentworkflow.entity.StateNode;
import com.agentworkflow.entity.StateTransition;
import com.agentworkflow.entity.GlobalVariable;
import com.agentworkflow.entity.StateWorkflowInstance;
import com.agentworkflow.entity.StateExecutionLog;
import com.agentworkflow.mapper.StateWorkflowMapper;
import com.agentworkflow.service.AIService;
import com.agentworkflow.service.WorkflowChatService;

public class StateMachineEngine {
    
    private StateWorkflowMapper workflowMapper;
    private ExpressionEngine expressionEngine = new ExpressionEngine();
    private ExpressionParser expressionParser = new ExpressionParser();
    private GlobalVariableManager variableManager = new GlobalVariableManager();
    private ChatHistoryManager chatHistoryManager = new ChatHistoryManager(); // 历史对话消息管理器
    private ExecutorService executorService = Executors.newFixedThreadPool(10);
    private ObjectMapper objectMapper = new ObjectMapper();
    private AIService aiService; // AI服务，用于大模型调用
    private WorkflowChatService chatService; // 对话服务，用于发送消息到对话界面
    
    // 存储等待用户输入的实例，key为instanceId，value为CompletableFuture<String>
    private final Map<Long, CompletableFuture<String>> pendingUserInputs = new ConcurrentHashMap<>();
    
    // 最大执行次数，防止死循环
    private static final int MAX_EXECUTION_COUNT = 1000;
    
    public StateMachineEngine(StateWorkflowMapper workflowMapper) {
        this.workflowMapper = workflowMapper;
    }
    
    // 设置AI服务（通过setter注入，避免循环依赖）
    public void setAIService(AIService aiService) {
        this.aiService = aiService;
    }
    
    // 设置对话服务（通过setter注入，避免循环依赖）
    public void setChatService(WorkflowChatService chatService) {
        System.out.println("\n========== StateMachineEngine.setChatService 被调用 ==========");
        System.out.println("chatService: " + (chatService != null ? "已设置" : "为null"));
        if (chatService != null) {
            System.out.println("chatService类型: " + chatService.getClass().getName());
        }
        this.chatService = chatService;
        System.out.println("this.chatService: " + (this.chatService != null ? "已设置" : "仍为null"));
        System.out.println("========== StateMachineEngine.setChatService 调用结束 ==========\n");
    }
    
    /**
     * 获取对话服务，如果为null则尝试从Spring上下文获取
     */
    private WorkflowChatService getChatService() {
        if (this.chatService != null) {
            return this.chatService;
        }
        
        // 如果 chatService 为 null，尝试从 Spring 上下文获取
        // 注意：这需要 StateMachineEngine 能够访问 ApplicationContext
        // 由于 StateMachineEngine 不是 Spring Bean，我们需要其他方式
        // 暂时返回 null，依赖配置类正确注入
        return null;
    }
    
    /**
     * 创建工作流实例（不执行）
     * @param workflowId 工作流ID
     * @param inputParams 输入参数
     * @return 工作流实例
     */
    public StateWorkflowInstance createWorkflowInstance(Long workflowId, Map<String, Object> inputParams) {
        System.out.println("========== 创建工作流实例 ==========");
        System.out.println("工作流ID: " + workflowId);
        System.out.println("输入参数: " + inputParams);
        
        // 获取工作流定义
        StateWorkflowDefinition workflow = workflowMapper.getWorkflowById(workflowId);
        if (workflow == null) {
            System.out.println("错误: 工作流未找到，ID: " + workflowId);
            throw new IllegalArgumentException("Workflow not found: " + workflowId);
        }
        
        System.out.println("工作流名称: " + workflow.getName());
        System.out.println("工作流版本: " + workflow.getVersion());
        
        // 创建工作流实例（不执行）
        StateWorkflowInstance instance = createInstance(workflow, inputParams);
        
        return instance;
    }
    
    /**
     * 异步执行工作流实例
     * @param instanceId 工作流实例ID
     */
    public void executeWorkflowAsync(Long instanceId) {
        executorService.submit(() -> {
            // 设置线程名称以便调试
            Thread.currentThread().setName("WorkflowExecutor-" + instanceId);
            
            // 强制刷新输出流，确保日志能立即显示
            System.out.flush();
            System.err.flush();
            
            try {
                System.out.println("========== 开始异步执行工作流实例 ==========");
                System.out.println("实例ID: " + instanceId);
                System.out.println("线程名称: " + Thread.currentThread().getName());
                System.out.println("【服务状态检查】");
                System.out.println("  - aiService: " + (aiService != null ? "已注入" : "未注入"));
                System.out.println("  - chatService: " + (chatService != null ? "已注入" : "未注入"));
                if (chatService != null) {
                    System.out.println("  - chatService类型: " + chatService.getClass().getName());
                }
                System.out.println("  - StateMachineEngine实例: " + this);
                System.out.flush();
                
                // 获取实例
                StateWorkflowInstance instance = workflowMapper.getInstanceById(instanceId);
                if (instance == null) {
                    System.err.println("错误: 工作流实例未找到，ID: " + instanceId);
                    return;
                }
                
                System.out.println("工作流实例状态: " + instance.getStatus());
                System.out.println("工作流ID: " + instance.getWorkflowId());
                
                // 获取工作流定义
                StateWorkflowDefinition workflow = workflowMapper.getWorkflowById(instance.getWorkflowId());
                if (workflow == null) {
                    System.err.println("错误: 工作流未找到，ID: " + instance.getWorkflowId());
                    return;
                }
                
                System.out.println("工作流名称: " + workflow.getName());
                
                // 获取完整的工作流定义（包括节点、转换和全局变量）
                loadWorkflowDefinition(workflow);
                System.out.println("节点数量: " + workflow.getNodes().size());
                System.out.println("转换数量: " + workflow.getTransitions().size());
                System.out.println("全局变量数量: " + workflow.getGlobalVariables().size());
                
                // 重新初始化全局变量（从实例中恢复）
                try {
                    Map<String, Object> inputParams = jsonToMap(instance.getInputParams());
                    variableManager.initialize(workflow.getGlobalVariables(), inputParams);
                    // 如果实例中已有全局变量，则合并
                    if (instance.getGlobalVariables() != null && !instance.getGlobalVariables().isEmpty()) {
                        Map<String, Object> existingVars = jsonToMap(instance.getGlobalVariables());
                        variableManager.mergeVariables(existingVars);
                    }
                    System.out.println("全局变量初始化完成: " + variableManager.getAllVariables());
                } catch (Exception e) {
                    System.err.println("初始化全局变量失败: " + e.getMessage());
                    e.printStackTrace();
                }
                
                // 初始化历史对话消息管理器（每个实例独立）
                chatHistoryManager.clearAll();
                System.out.println("历史对话消息管理器已初始化");
                
                // 执行工作流
                System.out.println("准备执行工作流实例...");
                System.out.flush();
                executeInstance(instance, workflow);
                System.out.println("工作流实例执行完成");
                System.out.flush();
            } catch (Throwable e) {  // 捕获所有异常，包括Error
                System.err.println("========== 异步执行工作流失败 ==========");
                System.err.println("实例ID: " + instanceId);
                System.err.println("线程名称: " + Thread.currentThread().getName());
                System.err.println("错误类型: " + e.getClass().getName());
                System.err.println("错误信息: " + e.getMessage());
                e.printStackTrace();
                
                // 更新实例状态为失败
                try {
                    StateWorkflowInstance instance = workflowMapper.getInstanceById(instanceId);
                    if (instance != null) {
                        instance.setStatus("failed");
                        instance.setFinishedAt(new Date());
                        instance.setUpdatedAt(new Date());
                        workflowMapper.updateInstance(instance);
                        System.err.println("已更新实例状态为失败");
                    }
                } catch (Exception updateError) {
                    System.err.println("更新实例状态失败: " + updateError.getMessage());
                    updateError.printStackTrace();
                }
            }
        });
    }
    
    /**
     * 执行工作流（同步，保留用于兼容）
     * @param workflowId 工作流ID
     * @param inputParams 输入参数
     * @return 工作流实例
     */
    public StateWorkflowInstance executeWorkflow(Long workflowId, Map<String, Object> inputParams) {
        StateWorkflowInstance instance = createWorkflowInstance(workflowId, inputParams);
        
        // 获取工作流定义
        StateWorkflowDefinition workflow = workflowMapper.getWorkflowById(workflowId);
        loadWorkflowDefinition(workflow);
        
        // 执行工作流
        return executeInstance(instance, workflow);
    }
    
    /**
     * 加载完整的工作流定义
     */
    private void loadWorkflowDefinition(StateWorkflowDefinition workflow) {
        try {
            // 从JSON解析工作流定义
            String jsonDefinition = workflow.getJsonDefinition();
            if (jsonDefinition == null || jsonDefinition.trim().isEmpty()) {
                throw new IllegalArgumentException("Workflow jsonDefinition is null or empty for workflow ID: " + workflow.getId());
            }
            System.out.println("解析工作流JSON定义，长度: " + jsonDefinition.length());
            Map<String, Object> workflowJson = objectMapper.readValue(jsonDefinition, new TypeReference<Map<String, Object>>() {});
            
            // 解析全局变量
            List<Map<String, Object>> globalVarsJson = (List<Map<String, Object>>) workflowJson.get("globalVariables");
            if (globalVarsJson == null) {
                globalVarsJson = new ArrayList<>();
            }
            List<GlobalVariable> globalVariables = globalVarsJson.stream().map(varJson -> {
                GlobalVariable var = new GlobalVariable();
                var.setWorkflowId(workflow.getId());
                var.setName((String) varJson.get("name"));
                var.setType((String) varJson.get("type"));
                var.setInitialValue((String) varJson.get("initialValue"));
                return var;
            }).collect(Collectors.toList());
            
            // 解析节点
            List<Map<String, Object>> nodesJson = (List<Map<String, Object>>) workflowJson.get("nodes");
            if (nodesJson == null) {
                throw new IllegalArgumentException("Workflow definition must contain 'nodes' field");
            }
            List<StateNode> nodes = nodesJson.stream().map(nodeJson -> {
                StateNode node = new StateNode();
                node.setWorkflowId(workflow.getId());
                // 兼容两种字段名：nodeKey 或 key
                String nodeKey = (String) nodeJson.get("nodeKey");
                if (nodeKey == null) {
                    nodeKey = (String) nodeJson.get("key");
                }
                node.setNodeKey(nodeKey);
                node.setName((String) nodeJson.get("name"));
                node.setType((String) nodeJson.get("type"));
                try {
                    node.setConfigJson(objectMapper.writeValueAsString(nodeJson.get("config")));
                } catch (Exception e) {
                    throw new RuntimeException("Failed to serialize node config to JSON", e);
                }
                node.setPositionX(convertToInteger(nodeJson.get("positionX")));
                node.setPositionY(convertToInteger(nodeJson.get("positionY")));
                return node;
            }).collect(Collectors.toList());
            
            // 解析转换
            List<Map<String, Object>> transitionsJson = (List<Map<String, Object>>) workflowJson.get("transitions");
            if (transitionsJson == null) {
                transitionsJson = new ArrayList<>();
            }
            List<StateTransition> transitions = transitionsJson.stream().map(transJson -> {
                StateTransition transition = new StateTransition();
                transition.setWorkflowId(workflow.getId());
                // 兼容两种字段名：fromNodeKey 或 fromNode
                String fromNodeKey = (String) transJson.get("fromNodeKey");
                if (fromNodeKey == null) {
                    fromNodeKey = (String) transJson.get("fromNode");
                }
                transition.setFromNodeKey(fromNodeKey);
                
                // 兼容两种字段名：toNodeKey 或 toNode
                String toNodeKey = (String) transJson.get("toNodeKey");
                if (toNodeKey == null) {
                    toNodeKey = (String) transJson.get("toNode");
                }
                transition.setToNodeKey(toNodeKey);
                
                // 兼容两种字段名：conditionExpression 或 condition
                // 如果都没有，则为null（非分支节点的连接线不需要条件表达式）
                String conditionExpression = (String) transJson.get("conditionExpression");
                if (conditionExpression == null) {
                    conditionExpression = (String) transJson.get("condition");
                }
                transition.setConditionExpression(conditionExpression);
                
                return transition;
            }).collect(Collectors.toList());
            
            // 构建节点的输出关系
            for (StateNode node : nodes) {
                List<StateTransition> nodeOutputs = transitions.stream()
                    .filter(t -> t.getFromNodeKey().equals(node.getNodeKey()))
                    .collect(Collectors.toList());
                node.setOutputs(nodeOutputs);
            }
            
            workflow.setNodes(nodes);
            workflow.setTransitions(transitions);
            workflow.setGlobalVariables(globalVariables);
        } catch (Exception e) {
            System.out.println("解析工作流定义失败，工作流ID: " + workflow.getId());
            System.out.println("错误信息: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to parse workflow definition JSON for workflow ID: " + workflow.getId() + ", error: " + e.getMessage(), e);
        }
    }
    
    /**
     * 创建工作流实例
     */
    public StateWorkflowInstance createInstance(StateWorkflowDefinition workflow, Map<String, Object> inputParams) {
        StateWorkflowInstance instance = new StateWorkflowInstance();
        instance.setWorkflowId(workflow.getId());
        instance.setName(workflow.getName() + "_" + System.currentTimeMillis());
        instance.setStatus("pending");
        instance.setInputParams(mapToJson(inputParams));
        instance.setCreatedAt(new Date());
        instance.setUpdatedAt(new Date());
        
        // 初始化全局变量
        variableManager.initialize(workflow.getGlobalVariables(), inputParams);
        instance.setGlobalVariables(mapToJson(variableManager.getAllVariables()));
        
        System.out.println("全局变量初始化完成: " + variableManager.getAllVariables());
        
        // 插入实例
        workflowMapper.insertInstance(instance);
        System.out.println("工作流实例已创建，实例ID: " + instance.getId());
        
        return instance;
    }
    
    /**
     * 执行工作流实例
     */
    private StateWorkflowInstance executeInstance(StateWorkflowInstance instance, StateWorkflowDefinition workflow) {
        System.out.println("\n========== 开始执行工作流实例 ==========");
        System.out.println("实例ID: " + instance.getId());
        
        instance.setStatus("running");
        instance.setStartedAt(new Date());
        instance.setUpdatedAt(new Date());
        workflowMapper.updateInstance(instance);
        
        // 查找开始节点
        StateNode currentNode = workflow.getNodes().stream()
            .filter(node -> "start".equals(node.getType()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Start node not found in workflow: " + workflow.getId()));
        
        System.out.println("找到开始节点: " + currentNode.getNodeKey() + " (" + currentNode.getName() + ")");
        
        instance.setCurrentNodeKey(currentNode.getNodeKey());
        workflowMapper.updateInstance(instance);
        
        int executionCount = 0;
        boolean completed = false;
        
        try {
            while (!completed && executionCount < MAX_EXECUTION_COUNT) {
                executionCount++;
                
                System.out.println("\n--- 执行步骤 #" + executionCount + " ---");
                System.out.println("当前节点: " + currentNode.getNodeKey() + " (" + currentNode.getName() + ")");
                System.out.println("节点类型: " + currentNode.getType());
                
                // 执行当前节点
                NodeExecutionResult result = executeNode(instance, currentNode);
                
                // 更新全局变量
                if (result.getUpdatedVariables() != null && !result.getUpdatedVariables().isEmpty()) {
                    System.out.println("节点执行结果 - 更新的变量: " + result.getUpdatedVariables());
                    variableManager.mergeVariables(result.getUpdatedVariables());
                    instance.setGlobalVariables(mapToJson(variableManager.getAllVariables()));
                    workflowMapper.updateInstance(instance);
                    System.out.println("当前全局变量: " + variableManager.getAllVariables());
                } else {
                    System.out.println("节点执行结果 - 无变量更新");
                }
                
                // 检查是否完成
                if ("end".equals(currentNode.getType())) {
                    System.out.println("到达结束节点，工作流执行完成");
                    completed = true;
                    break;
                }
                
                // 查找下一个节点
                System.out.println("查找下一个节点...");
                StateNode nextNode = findNextNode(currentNode, workflow, result);
                if (nextNode == null) {
                    System.out.println("未找到下一个节点，工作流结束");
                    System.out.println("当前节点类型: " + currentNode.getType());
                    System.out.println("当前节点的输出转换数量: " + (currentNode.getOutputs() != null ? currentNode.getOutputs().size() : 0));
                    // 没有下一个节点，工作流结束
                    completed = true;
                    break;
                }
                
                System.out.println("下一个节点: " + nextNode.getNodeKey() + " (" + nextNode.getName() + ")");
                System.out.println("下一个节点类型: " + nextNode.getType());
                currentNode = nextNode;
                instance.setCurrentNodeKey(currentNode.getNodeKey());
                workflowMapper.updateInstance(instance);
                System.out.println("已更新当前节点为: " + currentNode.getNodeKey());
            }
            
            // 处理执行结果
            if (executionCount >= MAX_EXECUTION_COUNT) {
                System.out.println("\n警告: 达到最大执行次数限制 (" + MAX_EXECUTION_COUNT + ")");
                instance.setStatus("failed");
                instance.setOutputParams(mapToJson("{\"error\": \"Maximum execution count exceeded\"}"));
            } else {
                System.out.println("\n========== 工作流执行成功 ==========");
                System.out.println("总执行步骤数: " + executionCount);
                System.out.println("最终全局变量: " + variableManager.getAllVariables());
                instance.setStatus("completed");
                instance.setOutputParams(mapToJson(variableManager.getAllVariables()));
            }
        } catch (Exception e) {
            System.out.println("\n========== 工作流执行失败 ==========");
            System.out.println("错误信息: " + e.getMessage());
            System.out.println("错误堆栈: ");
            e.printStackTrace();
            instance.setStatus("failed");
            instance.setOutputParams(mapToJson("{\"error\": \"" + e.getMessage() + "\"}"));
            logExecutionError(instance, currentNode, e);
        } finally {
            instance.setFinishedAt(new Date());
            instance.setUpdatedAt(new Date());
            workflowMapper.updateInstance(instance);
            System.out.println("工作流实例状态已更新: " + instance.getStatus());
        }
        
        return instance;
    }
    
    /**
     * 执行单个节点
     */
    private NodeExecutionResult executeNode(StateWorkflowInstance instance, StateNode node) {
        System.out.println(">>> 开始执行节点: " + node.getNodeKey() + " (" + node.getName() + ")");
        System.out.println("    节点类型: " + node.getType());
        
        long startTime = System.currentTimeMillis();
        NodeExecutionResult result = new NodeExecutionResult();
        
        try {
            // 根据节点类型执行不同的逻辑
            switch (node.getType()) {
                case "start":
                    result = executeStartNode(node);
                    break;
                case "end":
                    result = executeEndNode(node);
                    break;
                case "llm_call":
                    result = executeLlmCallNode(instance, node);
                    break;
                case "llm_assign":
                    result = executeLlmAssignNode(instance, node);
                    break;
                case "parallel":
                    result = executeParallelNode(instance, node);
                    break;
                case "basic_branch":
                    result = executeBasicBranchNode(node);
                    break;
                case "llm_branch":
                    result = executeLlmBranchNode(instance, node);
                    break;
                case "assign":
                    result = executeAssignNode(node);
                    break;
                case "workflow_call":
                    result = executeWorkflowCallNode(node);
                    break;
                case "http_call":
                    result = executeHttpCallNode(node);
                    break;
                case "user_input":
                    result = executeUserInputNode(instance, node);
                    break;
                case "info_output":
                    result = executeInfoOutputNode(instance, node);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported node type: " + node.getType());
            }
            
            long executionTime = System.currentTimeMillis() - startTime;
            System.out.println("<<< 节点执行成功，耗时: " + executionTime + "ms");
            if (result.getUpdatedVariables() != null && !result.getUpdatedVariables().isEmpty()) {
                System.out.println("    输出变量: " + result.getUpdatedVariables());
            }
            
            // 记录成功日志
            logExecutionSuccess(instance, node, result, executionTime);
        } catch (Exception e) {
            System.out.println("<<< 节点执行失败: " + e.getMessage());
            e.printStackTrace();
            // 记录失败日志
            logExecutionError(instance, node, e);
            throw e;
        }
        
        return result;
    }
    
    /**
     * 执行开始节点
     */
    private NodeExecutionResult executeStartNode(StateNode node) {
        NodeExecutionResult result = new NodeExecutionResult();
        // 开始节点不需要特殊处理，直接返回
        return result;
    }
    
    /**
     * 执行结束节点
     */
    private NodeExecutionResult executeEndNode(StateNode node) {
        NodeExecutionResult result = new NodeExecutionResult();
        // 结束节点不需要特殊处理，直接返回
        return result;
    }
    
    /**
     * 执行大模型调用节点
     */
    private NodeExecutionResult executeLlmCallNode(StateWorkflowInstance instance, StateNode node) {
        System.out.println("\n========== 开始执行大模型调用节点 ==========");
        System.out.println("节点Key: " + node.getNodeKey());
        System.out.println("节点名称: " + node.getName());
        System.out.println("实例ID: " + instance.getId());
        
        NodeExecutionResult result = new NodeExecutionResult();
        
        try {
            // 检查AI服务是否可用
            if (aiService == null) {
                System.err.println("错误: AI服务未注入，无法执行大模型调用节点");
                throw new IllegalStateException("AI service is not available");
            }
            
            // 解析节点配置
            System.out.println("解析节点配置JSON...");
            Map<String, Object> config = jsonToMap(node.getConfigJson());
            System.out.println("节点配置: " + config);
            
            // 获取配置参数
            String systemPrompt = (String) config.getOrDefault("systemPrompt", "");
            String userPromptTemplate = (String) config.getOrDefault("userPrompt", "");
            String plugins = (String) config.getOrDefault("plugins", "");
            // 兼容两种字段名：outputVar 和 outputVariable
            String outputVariable = (String) config.getOrDefault("outputVar", 
                (String) config.getOrDefault("outputVariable", "aiResponse"));
            
            // 历史对话相关配置
            String historyKey = (String) config.getOrDefault("historyKey", "default");
            Boolean useHistory = parseBoolean(config.get("useHistory"), false);
            Boolean saveToHistory = parseBoolean(config.get("saveToHistory"), false);
            
            // 获取对话界面昵称（用于历史对话）
            String chatNickname = (String) config.get("chatNickname");
            if (chatNickname == null || chatNickname.trim().isEmpty()) {
                chatNickname = node.getName(); // 如果未设置昵称，使用节点名称
            }
            
            System.out.println("\n--- 大模型节点输入参数 ---");
            System.out.println("系统提示词: " + systemPrompt);
            System.out.println("用户提示词模板: " + userPromptTemplate);
            System.out.println("插件配置: " + plugins);
            System.out.println("输出变量名: " + outputVariable);
            System.out.println("历史对话key: " + historyKey);
            System.out.println("是否引用历史对话: " + useHistory);
            System.out.println("是否保留输出至历史对话: " + saveToHistory);
            System.out.println("对话界面昵称: " + chatNickname);
            
            // 获取当前全局变量
            Map<String, Object> currentVariables = variableManager.getAllVariables();
            System.out.println("\n--- 当前全局变量 ---");
            System.out.println("变量数量: " + currentVariables.size());
            for (Map.Entry<String, Object> entry : currentVariables.entrySet()) {
                System.out.println("  " + entry.getKey() + " = " + entry.getValue() + " (类型: " + 
                    (entry.getValue() != null ? entry.getValue().getClass().getSimpleName() : "null") + ")");
            }
            
            // 替换提示词模板中的变量（系统提示词和用户提示词都支持变量替换）
            String systemPromptReplaced = replaceVariablesInTemplate(systemPrompt, currentVariables);
            String userPrompt = replaceVariablesInTemplate(userPromptTemplate, currentVariables);
            System.out.println("\n--- 变量替换后的系统提示词 ---");
            System.out.println(systemPromptReplaced);
            System.out.println("\n--- 变量替换后的用户提示词 ---");
            System.out.println(userPrompt);
            
            // 调用AI服务（根据是否引用历史对话选择不同的方法）
            System.out.println("\n--- 开始调用AI服务 ---");
            System.out.println("AI服务是否可用: " + (aiService != null ? "是" : "否"));
            long aiCallStartTime = System.currentTimeMillis();
            String aiResponse;
            long aiCallDuration;
            
            try {
            if (useHistory) {
                // 引用历史对话：获取历史消息并组装
                System.out.println("准备引用历史对话，key: " + historyKey);
                List<Map<String, String>> historyMessages = chatHistoryManager.getHistoryMessages(historyKey, chatNickname);
                System.out.println("引用历史对话，历史消息数量: " + historyMessages.size());
                
                // 输出历史消息的详细信息
                System.out.println("\n--- 历史对话消息详情 ---");
                for (int i = 0; i < historyMessages.size(); i++) {
                    Map<String, String> msg = historyMessages.get(i);
                    System.out.println("  消息 #" + (i + 1) + ":");
                    System.out.println("    role: " + msg.get("role"));
                    System.out.println("    content: " + (msg.get("content") != null && msg.get("content").length() > 200 
                        ? msg.get("content").substring(0, 200) + "... (截断)" : msg.get("content")));
                }
                
                // 将当前用户提示词添加到消息列表末尾（只有当用户提示词不为空时才添加）
                if (userPrompt != null && !userPrompt.trim().isEmpty()) {
                    Map<String, String> currentUserMessage = new HashMap<>();
                    currentUserMessage.put("role", "user");
                    currentUserMessage.put("content", userPrompt);
                    historyMessages.add(currentUserMessage);
                    System.out.println("已添加当前用户提示词到消息列表末尾");
                } else {
                    System.out.println("警告: 当前用户提示词为空，不添加到消息列表");
                }
                
                System.out.println("\n--- 最终发送给AI的消息列表 ---");
                System.out.println("系统提示词: " + (systemPromptReplaced != null && systemPromptReplaced.length() > 200 
                    ? systemPromptReplaced.substring(0, 200) + "... (截断)" : systemPromptReplaced));
                System.out.println("总消息数: " + historyMessages.size());
                for (int i = 0; i < historyMessages.size(); i++) {
                    Map<String, String> msg = historyMessages.get(i);
                    String content = msg.get("content");
                    String role = msg.get("role");
                    
                    // 检查消息是否为空
                    if (content == null || content.trim().isEmpty()) {
                        System.out.println("  消息 #" + (i + 1) + " [role: " + role + "]: <空消息，将被跳过>");
                        continue;
                    }
                    
                    System.out.println("  消息 #" + (i + 1) + " [role: " + role + "]:");
                    if (content.length() > 300) {
                        System.out.println("    " + content.substring(0, 300) + "... (截断，总长度: " + content.length() + " 字符)");
                    } else {
                        System.out.println("    " + content);
                    }
                }
                
                // 过滤掉空消息
                List<Map<String, String>> filteredMessages = new ArrayList<>();
                for (Map<String, String> msg : historyMessages) {
                    String content = msg.get("content");
                    if (content != null && !content.trim().isEmpty()) {
                        filteredMessages.add(msg);
                    }
                }
                
                if (filteredMessages.size() != historyMessages.size()) {
                    System.out.println("\n警告: 发现 " + (historyMessages.size() - filteredMessages.size()) + " 条空消息，已过滤");
                    historyMessages = filteredMessages;
                }
                
                System.out.println("\n调用带历史消息的AI服务，总消息数: " + historyMessages.size());
                // 调用带历史消息的AI服务（使用替换后的系统提示词）
                aiResponse = aiService.chatWithHistory(systemPromptReplaced, historyMessages, plugins);
            } else {
                // 不引用历史对话：使用普通调用（使用替换后的系统提示词）
                System.out.println("\n--- 最终发送给AI的消息列表（无历史消息） ---");
                System.out.println("系统提示词: " + (systemPromptReplaced != null && systemPromptReplaced.length() > 200 
                    ? systemPromptReplaced.substring(0, 200) + "... (截断)" : systemPromptReplaced));
                System.out.println("用户消息: " + (userPrompt != null && userPrompt.length() > 300 
                    ? userPrompt.substring(0, 300) + "... (截断，总长度: " + userPrompt.length() + " 字符)" : userPrompt));
                System.out.println("\n调用普通AI服务（无历史消息）");
                aiResponse = aiService.chat(systemPromptReplaced, userPrompt, plugins);
            }
                
                aiCallDuration = System.currentTimeMillis() - aiCallStartTime;
                System.out.println("AI服务调用完成，总耗时: " + aiCallDuration + "ms");
            } catch (Exception e) {
                aiCallDuration = System.currentTimeMillis() - aiCallStartTime;
                System.err.println("AI服务调用异常，耗时: " + aiCallDuration + "ms");
                System.err.println("异常类型: " + e.getClass().getName());
                System.err.println("异常信息: " + e.getMessage());
                e.printStackTrace();
                throw e; // 重新抛出异常，让上层处理
            }
            
            System.out.println("\n--- AI服务调用完成 ---");
            System.out.println("调用耗时: " + aiCallDuration + "ms");
            System.out.println("AI响应长度: " + (aiResponse != null ? aiResponse.length() : 0) + " 字符");
            System.out.println("AI响应内容: " + (aiResponse != null && aiResponse.length() > 500 
                ? aiResponse.substring(0, 500) + "... (截断)" : aiResponse));
            
            // 将AI响应保存到输出变量
            result.addUpdatedVariable(outputVariable, aiResponse);
            
            // 如果配置了保留输出至历史对话，则保存到历史对话
            if (saveToHistory) {
                String saveHistoryKey = (String) config.getOrDefault("saveHistoryKey", historyKey);
                chatHistoryManager.addMessage(saveHistoryKey, chatNickname, aiResponse);
                System.out.println("已保存输出至历史对话，key: " + saveHistoryKey + ", 昵称: " + chatNickname);
            }
            
            // 如果配置了打印到对话界面，则发送消息
            Boolean enableChatOutput = parseBoolean(config.get("enableChatOutput"), false);
            System.out.println("解析后的enableChatOutput: " + enableChatOutput);
            
            if (enableChatOutput) {
                System.out.println("准备发送消息到对话界面，实例ID: " + instance.getId() + ", 昵称: " + chatNickname);
                
                // 获取 chatService（如果为 null，尝试延迟获取）
                WorkflowChatService serviceToUse = getChatService();
                
                // 发送消息到对话界面
                System.out.println("检查chatService状态: " + (serviceToUse != null ? "已注入" : "未注入"));
                if (serviceToUse == null) {
                    System.err.println("【错误】对话服务未注入，无法发送消息！");
                    System.err.println("【调试信息】");
                    System.err.println("  - this.chatService: " + this.chatService);
                    System.err.println("  - 实例ID: " + instance.getId());
                    System.err.println("  - 线程名称: " + Thread.currentThread().getName());
                    System.err.println("  - StateMachineEngine实例: " + this);
                } else {
                    System.out.println("【调试信息】chatService已注入，准备调用sendChatMessage");
                    System.out.println("  - chatService类型: " + serviceToUse.getClass().getName());
                    System.out.println("  - 实例ID: " + instance.getId());
                    System.out.println("  - 线程名称: " + Thread.currentThread().getName());
                    serviceToUse.sendChatMessage(instance.getId(), "assistant", aiResponse, chatNickname);
                    System.out.println("已调用sendChatMessage，实例ID: " + instance.getId() + ", 昵称: " + chatNickname);
                }
            } else {
                System.out.println("未启用打印到对话界面，跳过消息发送");
            }
            
            System.out.println("\n--- 大模型节点输出结果 ---");
            System.out.println("输出变量名: " + outputVariable);
            System.out.println("输出变量值: " + (aiResponse != null && aiResponse.length() > 200 
                ? aiResponse.substring(0, 200) + "... (截断)" : aiResponse));
            System.out.println("更新的变量: " + result.getUpdatedVariables());
            
            System.out.println("\n========== 大模型调用节点执行成功 ==========\n");
            
        } catch (Exception e) {
            System.err.println("\n========== 大模型调用节点执行失败 ==========");
            System.err.println("节点Key: " + node.getNodeKey());
            System.err.println("节点名称: " + node.getName());
            System.err.println("错误类型: " + e.getClass().getName());
            System.err.println("错误信息: " + e.getMessage());
            System.err.println("错误堆栈: ");
            e.printStackTrace();
            throw new RuntimeException("Failed to execute LLM call node: " + node.getNodeKey(), e);
        }
        
        return result;
    }
    
    /**
     * 执行大模型赋值节点
     */
    private NodeExecutionResult executeLlmAssignNode(StateWorkflowInstance instance, StateNode node) {
        System.out.println("\n========== 开始执行大模型赋值节点 ==========");
        System.out.println("节点Key: " + node.getNodeKey());
        System.out.println("节点名称: " + node.getName());
        System.out.println("实例ID: " + instance.getId());
        
        NodeExecutionResult result = new NodeExecutionResult();
        
        try {
            // 检查AI服务是否可用
            if (aiService == null) {
                System.err.println("错误: AI服务未注入，无法执行大模型赋值节点");
                throw new IllegalStateException("AI service is not available");
            }
            
            // 解析节点配置
            System.out.println("解析节点配置JSON...");
            Map<String, Object> config = jsonToMap(node.getConfigJson());
            System.out.println("节点配置: " + config);
            
            // 获取配置参数
            String userPromptTemplate = (String) config.getOrDefault("userPrompt", "");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> assignVariables = (List<Map<String, Object>>) config.getOrDefault("assignVariables", new ArrayList<>());
            
            // 历史对话相关配置
            String historyKey = (String) config.getOrDefault("historyKey", "default");
            Boolean useHistory = parseBoolean(config.get("useHistory"), false);
            
            // 获取对话界面昵称（用于历史对话）
            String chatNickname = (String) config.get("chatNickname");
            if (chatNickname == null || chatNickname.trim().isEmpty()) {
                chatNickname = node.getName(); // 如果未设置昵称，使用节点名称
            }
            
            if (assignVariables == null || assignVariables.isEmpty()) {
                System.err.println("错误: 未配置要赋值的全局变量");
                throw new IllegalArgumentException("assignVariables is required for llm_assign node");
            }
            
            System.out.println("\n--- 大模型赋值节点输入参数 ---");
            System.out.println("用户提示词模板: " + userPromptTemplate);
            System.out.println("要赋值的变量数量: " + assignVariables.size());
            System.out.println("历史对话key: " + historyKey);
            System.out.println("是否引用历史对话: " + useHistory);
            System.out.println("对话界面昵称: " + chatNickname);
            for (Map<String, Object> var : assignVariables) {
                System.out.println("  变量: " + var.get("name") + " (类型: " + var.get("type") + ")");
            }
            
            // 获取当前全局变量
            Map<String, Object> currentVariables = variableManager.getAllVariables();
            System.out.println("\n--- 当前全局变量 ---");
            System.out.println("变量数量: " + currentVariables.size());
            for (Map.Entry<String, Object> entry : currentVariables.entrySet()) {
                System.out.println("  " + entry.getKey() + " = " + entry.getValue() + " (类型: " + 
                    (entry.getValue() != null ? entry.getValue().getClass().getSimpleName() : "null") + ")");
            }
            
            // 替换提示词模板中的变量
            String userPrompt = replaceVariablesInTemplate(userPromptTemplate, currentVariables);
            System.out.println("\n--- 变量替换后的用户提示词 ---");
            System.out.println(userPrompt);
            
            // 构建系统提示词，要求AI输出JSON格式
            StringBuilder systemPromptBuilder = new StringBuilder();
            systemPromptBuilder.append("你是一个专业的AI助手。请根据用户的提示词，输出一个JSON对象，包含以下变量的值：\n\n");
            for (Map<String, Object> var : assignVariables) {
                String varName = (String) var.get("name");
                String varType = (String) var.get("type");
                systemPromptBuilder.append("- ").append(varName);
                if (varType != null) {
                    systemPromptBuilder.append(" (类型: ").append(varType).append(")");
                }
                systemPromptBuilder.append("\n");
            }
            systemPromptBuilder.append("\n请严格按照以下JSON格式输出，不要包含任何其他文字或说明：\n");
            systemPromptBuilder.append("{\n");
            for (int i = 0; i < assignVariables.size(); i++) {
                Map<String, Object> var = assignVariables.get(i);
                String varName = (String) var.get("name");
                systemPromptBuilder.append("  \"").append(varName).append("\": <值>");
                if (i < assignVariables.size() - 1) {
                    systemPromptBuilder.append(",");
                }
                systemPromptBuilder.append("\n");
            }
            systemPromptBuilder.append("}\n\n");
            systemPromptBuilder.append("重要说明：\n");
            systemPromptBuilder.append("1. 变量名必须与上述列表中的名称完全一致（包括中文字符）。\n");
            systemPromptBuilder.append("2. 如果某个变量没有合适的值，或者用户明确说明只需要赋值其中部分变量，那么你可以在JSON中只包含需要赋值的变量，不需要包含所有变量。\n");
            systemPromptBuilder.append("3. 对于JSON中未包含的变量，系统将保留其原有值不变。\n");
            systemPromptBuilder.append("4. 只输出有效的、有意义的变量值，不要为了填满所有变量而输出无意义的值。");
            
            String systemPrompt = systemPromptBuilder.toString();
            System.out.println("\n--- 系统提示词 ---");
            System.out.println(systemPrompt);
            
            // 调用AI服务（根据是否引用历史对话选择不同的方法）
            System.out.println("\n--- 开始调用AI服务 ---");
            System.out.println("AI服务是否可用: " + (aiService != null ? "是" : "否"));
            long aiCallStartTime = System.currentTimeMillis();
            String aiResponse;
            long aiCallDuration;
            
            try {
                if (useHistory) {
                    // 引用历史对话：获取历史消息并组装
                    System.out.println("准备引用历史对话，key: " + historyKey);
                    List<Map<String, String>> historyMessages = chatHistoryManager.getHistoryMessages(historyKey, chatNickname);
                    System.out.println("引用历史对话，历史消息数量: " + historyMessages.size());
                    
                    // 输出历史消息的详细信息
                    System.out.println("\n--- 历史对话消息详情 ---");
                    for (int i = 0; i < historyMessages.size(); i++) {
                        Map<String, String> msg = historyMessages.get(i);
                        System.out.println("  消息 #" + (i + 1) + ":");
                        System.out.println("    role: " + msg.get("role"));
                        System.out.println("    content: " + (msg.get("content") != null && msg.get("content").length() > 200 
                            ? msg.get("content").substring(0, 200) + "... (截断)" : msg.get("content")));
                    }
                    
                    // 将当前用户提示词添加到消息列表末尾（只有当用户提示词不为空时才添加）
                    if (userPrompt != null && !userPrompt.trim().isEmpty()) {
                        Map<String, String> currentUserMessage = new HashMap<>();
                        currentUserMessage.put("role", "user");
                        currentUserMessage.put("content", userPrompt);
                        historyMessages.add(currentUserMessage);
                        System.out.println("已添加当前用户提示词到消息列表末尾");
                    } else {
                        System.out.println("警告: 当前用户提示词为空，不添加到消息列表");
                    }
                    
                    System.out.println("\n--- 最终发送给AI的消息列表 ---");
                    System.out.println("系统提示词: " + (systemPrompt != null && systemPrompt.length() > 200 
                        ? systemPrompt.substring(0, 200) + "... (截断)" : systemPrompt));
                    System.out.println("总消息数: " + historyMessages.size());
                    for (int i = 0; i < historyMessages.size(); i++) {
                        Map<String, String> msg = historyMessages.get(i);
                        String content = msg.get("content");
                        String role = msg.get("role");
                        
                        // 检查消息是否为空
                        if (content == null || content.trim().isEmpty()) {
                            System.out.println("  消息 #" + (i + 1) + " [role: " + role + "]: <空消息，将被跳过>");
                            continue;
                        }
                        
                        System.out.println("  消息 #" + (i + 1) + " [role: " + role + "]:");
                        if (content.length() > 300) {
                            System.out.println("    " + content.substring(0, 300) + "... (截断，总长度: " + content.length() + " 字符)");
                        } else {
                            System.out.println("    " + content);
                        }
                    }
                    
                    // 过滤掉空消息
                    List<Map<String, String>> filteredMessages = new ArrayList<>();
                    for (Map<String, String> msg : historyMessages) {
                        String content = msg.get("content");
                        if (content != null && !content.trim().isEmpty()) {
                            filteredMessages.add(msg);
                        }
                    }
                    
                    if (filteredMessages.size() != historyMessages.size()) {
                        System.out.println("\n警告: 发现 " + (historyMessages.size() - filteredMessages.size()) + " 条空消息，已过滤");
                        historyMessages = filteredMessages;
                    }
                    
                    System.out.println("\n调用带历史消息的AI服务，总消息数: " + historyMessages.size());
                    // 调用带历史消息的AI服务
                    aiResponse = aiService.chatWithHistory(systemPrompt, historyMessages, "");
                } else {
                    // 不引用历史对话：使用普通调用
                    System.out.println("\n--- 最终发送给AI的消息列表（无历史消息） ---");
                    System.out.println("系统提示词: " + (systemPrompt != null && systemPrompt.length() > 200 
                        ? systemPrompt.substring(0, 200) + "... (截断)" : systemPrompt));
                    System.out.println("用户消息: " + (userPrompt != null && userPrompt.length() > 300 
                        ? userPrompt.substring(0, 300) + "... (截断，总长度: " + userPrompt.length() + " 字符)" : userPrompt));
                    System.out.println("\n调用普通AI服务（无历史消息）");
                    aiResponse = aiService.chat(systemPrompt, userPrompt, "");
                }
                
                aiCallDuration = System.currentTimeMillis() - aiCallStartTime;
                System.out.println("AI服务调用完成，总耗时: " + aiCallDuration + "ms");
            } catch (Exception e) {
                aiCallDuration = System.currentTimeMillis() - aiCallStartTime;
                System.err.println("AI服务调用异常，耗时: " + aiCallDuration + "ms");
                System.err.println("异常类型: " + e.getClass().getName());
                System.err.println("异常信息: " + e.getMessage());
                e.printStackTrace();
                throw e;
            }
            
            System.out.println("\n--- AI响应 ---");
            System.out.println("响应长度: " + (aiResponse != null ? aiResponse.length() : 0) + " 字符");
            System.out.println("响应内容: " + (aiResponse != null && aiResponse.length() > 500 
                ? aiResponse.substring(0, 500) + "... (截断)" : aiResponse));
            
            // 从AI响应中提取JSON
            String jsonStr = extractJsonFromResponse(aiResponse);
            System.out.println("\n--- 提取的JSON ---");
            System.out.println(jsonStr);
            
            // 解析JSON
            Map<String, Object> jsonResult = jsonToMap(jsonStr);
            System.out.println("\n--- 解析后的JSON对象 ---");
            System.out.println(jsonResult);
            
            // 根据JSON更新全局变量（只更新JSON中存在的变量，不存在的保留原值）
            System.out.println("\n--- 开始更新全局变量 ---");
            int updatedCount = 0;
            int preservedCount = 0;
            
            for (Map<String, Object> var : assignVariables) {
                String varName = (String) var.get("name");
                String varType = (String) var.get("type");
                
                Object value = jsonResult.get(varName);
                if (value != null) {
                    // 使用GlobalVariableManager的updateVariable方法，它会根据类型进行转换
                    variableManager.updateVariable(varName, varType != null ? varType : "string", value);
                    result.addUpdatedVariable(varName, value);
                    System.out.println("  ✓ 更新变量: " + varName + " = " + value + " (类型: " + varType + ")");
                    updatedCount++;
                } else {
                    // JSON中未包含此变量，保留原值
                    Object originalValue = variableManager.getVariable(varName);
                    System.out.println("  ○ 保留原值: " + varName + " = " + originalValue + " (JSON中未包含此变量)");
                    preservedCount++;
                }
            }
            
            System.out.println("  统计: 更新 " + updatedCount + " 个变量，保留 " + preservedCount + " 个变量的原值");
            
            System.out.println("\n--- 大模型赋值节点输出结果 ---");
            System.out.println("更新的变量: " + result.getUpdatedVariables());
            System.out.println("\n========== 大模型赋值节点执行成功 ==========\n");
            
        } catch (Exception e) {
            System.err.println("\n========== 大模型赋值节点执行失败 ==========");
            System.err.println("节点Key: " + node.getNodeKey());
            System.err.println("节点名称: " + node.getName());
            System.err.println("错误类型: " + e.getClass().getName());
            System.err.println("错误信息: " + e.getMessage());
            System.err.println("错误堆栈: ");
            e.printStackTrace();
            throw new RuntimeException("Failed to execute LLM assign node: " + node.getNodeKey(), e);
        }
        
        return result;
    }
    
    /**
     * 从AI响应中提取JSON字符串
     * 尝试提取JSON对象，支持多种格式（纯JSON、代码块中的JSON等）
     */
    private String extractJsonFromResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            throw new IllegalArgumentException("AI response is empty");
        }
        
        String trimmed = response.trim();
        
        // 如果响应本身就是JSON对象（以{开头，以}结尾）
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            // 尝试找到完整的JSON对象
            int braceCount = 0;
            int startIndex = -1;
            int endIndex = -1;
            
            for (int i = 0; i < trimmed.length(); i++) {
                char c = trimmed.charAt(i);
                if (c == '{') {
                    if (startIndex == -1) {
                        startIndex = i;
                    }
                    braceCount++;
                } else if (c == '}') {
                    braceCount--;
                    if (braceCount == 0 && startIndex != -1) {
                        endIndex = i;
                        break;
                    }
                }
            }
            
            if (startIndex != -1 && endIndex != -1) {
                return trimmed.substring(startIndex, endIndex + 1);
            }
        }
        
        // 尝试从代码块中提取JSON（```json ... ``` 或 ``` ... ```）
        String[] codeBlockMarkers = {"```json", "```"};
        for (String marker : codeBlockMarkers) {
            int startMarker = trimmed.indexOf(marker);
            if (startMarker != -1) {
                int jsonStart = startMarker + marker.length();
                // 跳过可能的换行符
                while (jsonStart < trimmed.length() && 
                       (trimmed.charAt(jsonStart) == '\n' || trimmed.charAt(jsonStart) == '\r')) {
                    jsonStart++;
                }
                int endMarker = trimmed.indexOf("```", jsonStart);
                if (endMarker != -1) {
                    String jsonCandidate = trimmed.substring(jsonStart, endMarker).trim();
                    if (jsonCandidate.startsWith("{") && jsonCandidate.endsWith("}")) {
                        return jsonCandidate;
                    }
                }
            }
        }
        
        // 如果都找不到，尝试直接解析整个响应
        // 先验证是否是有效的JSON
        try {
            objectMapper.readValue(trimmed, Map.class);
            return trimmed;
        } catch (Exception e) {
            // 如果解析失败，抛出异常
            throw new IllegalArgumentException("无法从AI响应中提取有效的JSON。响应内容: " + 
                (trimmed.length() > 200 ? trimmed.substring(0, 200) + "..." : trimmed));
        }
    }
    
    /**
     * 替换模板中的变量占位符
     * 支持 ${variableName} 格式
     */
    private String replaceVariablesInTemplate(String template, Map<String, Object> variables) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        
        String result = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            String value = entry.getValue() != null ? String.valueOf(entry.getValue()) : "";
            result = result.replace(placeholder, value);
        }
        
        return result;
    }
    
    /**
     * 解析布尔值配置
     * @param value 配置值（可能是Boolean、String或其他类型）
     * @param defaultValue 默认值
     * @return 解析后的布尔值
     */
    private Boolean parseBoolean(Object value, Boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return Boolean.valueOf(value.toString());
    }
    
    /**
     * 执行并行节点
     */
    private NodeExecutionResult executeParallelNode(StateWorkflowInstance instance, StateNode node) {
        // TODO: 实现并行节点逻辑
        NodeExecutionResult result = new NodeExecutionResult();
        return result;
    }
    
    /**
     * 执行基础分支节点
     */
    private NodeExecutionResult executeBasicBranchNode(StateNode node) {
        System.out.println("\n========== 开始执行基础分支节点 ==========");
        System.out.println("节点Key: " + node.getNodeKey());
        System.out.println("节点名称: " + node.getName());
        System.out.println("节点配置: " + node.getConfigJson());
        
        NodeExecutionResult result = new NodeExecutionResult();
        
        // 基础分支节点不需要修改全局变量，只负责分支选择
        // 分支选择逻辑在findNextNode方法中实现
        
        System.out.println("基础分支节点执行完成，分支选择将在findNextNode中进行");
        System.out.println("========== 基础分支节点执行结束 ==========\n");
        
        return result;
    }
    
    /**
     * 执行大模型分支节点
     */
    private NodeExecutionResult executeLlmBranchNode(StateWorkflowInstance instance, StateNode node) {
        System.out.println("\n========== 开始执行大模型分支节点 ==========");
        System.out.println("节点Key: " + node.getNodeKey());
        System.out.println("节点名称: " + node.getName());
        System.out.println("实例ID: " + instance.getId());
        
        NodeExecutionResult result = new NodeExecutionResult();
        
        try {
            // 检查AI服务是否可用
            if (aiService == null) {
                System.err.println("错误: AI服务未注入，无法执行大模型分支节点");
                throw new IllegalStateException("AI service is not available");
            }
            
            // 解析节点配置
            System.out.println("解析节点配置JSON...");
            Map<String, Object> config = jsonToMap(node.getConfigJson());
            System.out.println("节点配置: " + config);
            
            // 获取配置参数
            String userPromptTemplate = (String) config.getOrDefault("userPrompt", "");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> branches = (List<Map<String, Object>>) config.getOrDefault("branches", new ArrayList<>());
            String defaultBranch = (String) config.getOrDefault("defaultBranch", "");
            
            // 历史对话相关配置
            String historyKey = (String) config.getOrDefault("historyKey", "default");
            Boolean useHistory = parseBoolean(config.get("useHistory"), false);
            
            // 获取对话界面昵称（用于历史对话）
            String chatNickname = (String) config.get("chatNickname");
            if (chatNickname == null || chatNickname.trim().isEmpty()) {
                chatNickname = node.getName(); // 如果未设置昵称，使用节点名称
            }
            
            if (branches == null || branches.isEmpty()) {
                System.err.println("错误: 未配置分支列表");
                throw new IllegalArgumentException("branches is required for llm_branch node");
            }
            
            // 如果未设置默认分支，使用最后一个分支作为默认分支
            if (defaultBranch == null || defaultBranch.trim().isEmpty()) {
                Map<String, Object> lastBranch = branches.get(branches.size() - 1);
                defaultBranch = (String) lastBranch.get("name");
                System.out.println("未设置默认分支，自动使用最后一个分支作为默认分支: " + defaultBranch);
            }
            
            System.out.println("\n--- 大模型分支节点输入参数 ---");
            System.out.println("用户提示词模板: " + userPromptTemplate);
            System.out.println("分支数量: " + branches.size());
            System.out.println("默认分支: " + defaultBranch);
            System.out.println("历史对话key: " + historyKey);
            System.out.println("是否引用历史对话: " + useHistory);
            System.out.println("对话界面昵称: " + chatNickname);
            for (int i = 0; i < branches.size(); i++) {
                Map<String, Object> branch = branches.get(i);
                String branchName = (String) branch.get("name");
                String branchDescription = (String) branch.getOrDefault("description", "");
                System.out.println("  分支 " + (i + 1) + ": " + branchName + 
                    (branchDescription != null && !branchDescription.isEmpty() ? " (" + branchDescription + ")" : ""));
            }
            
            // 获取当前全局变量
            Map<String, Object> currentVariables = variableManager.getAllVariables();
            System.out.println("\n--- 当前全局变量 ---");
            System.out.println("变量数量: " + currentVariables.size());
            for (Map.Entry<String, Object> entry : currentVariables.entrySet()) {
                System.out.println("  " + entry.getKey() + " = " + entry.getValue() + " (类型: " + 
                    (entry.getValue() != null ? entry.getValue().getClass().getSimpleName() : "null") + ")");
            }
            
            // 替换用户提示词模板中的变量
            String userPrompt = replaceVariablesInTemplate(userPromptTemplate, currentVariables);
            System.out.println("\n--- 变量替换后的用户提示词 ---");
            System.out.println(userPrompt);
            
            // 构建系统提示词，告知AI有哪些分支可选
            StringBuilder systemPromptBuilder = new StringBuilder();
            systemPromptBuilder.append("你是一个专业的AI助手。请根据用户的提示词，从以下分支中选择一个最合适的分支：\n\n");
            int lastIndex = branches.size() - 1;
            for (int i = 0; i < branches.size(); i++) {
                Map<String, Object> branch = branches.get(i);
                String branchName = (String) branch.get("name");
                String branchDescription = (String) branch.getOrDefault("description", "");
                systemPromptBuilder.append((i + 1)).append(". ").append(branchName);
                if (i == lastIndex) {
                    systemPromptBuilder.append(" (默认分支)");
                }
                if (branchDescription != null && !branchDescription.isEmpty()) {
                    systemPromptBuilder.append(" - ").append(branchDescription);
                }
                systemPromptBuilder.append("\n");
            }
            Map<String, Object> lastBranch = branches.get(lastIndex);
            systemPromptBuilder.append("\n注意：最后一个分支（").append(lastBranch.get("name"))
                .append("）是默认分支。如果以上分支都不合适，将自动使用默认分支。");
            systemPromptBuilder.append("\n\n请严格按照以下JSON格式输出你的选择，不要包含任何其他文字或说明：\n");
            systemPromptBuilder.append("{\n");
            systemPromptBuilder.append("  \"selectedBranch\": \"<分支名称>\"\n");
            systemPromptBuilder.append("}\n\n");
            systemPromptBuilder.append("重要说明：\n");
            systemPromptBuilder.append("1. selectedBranch 的值必须是上述分支列表中的某个分支名称（完全一致，包括中文字符）。\n");
            if (defaultBranch != null && !defaultBranch.isEmpty()) {
                systemPromptBuilder.append("2. 如果以上分支都不合适，可以使用默认分支：").append(defaultBranch).append("\n");
            }
            systemPromptBuilder.append("3. 只输出JSON，不要包含任何其他文字。");
            
            String systemPrompt = systemPromptBuilder.toString();
            System.out.println("\n--- 系统提示词 ---");
            System.out.println(systemPrompt);
            
            // 调用AI服务（根据是否引用历史对话选择不同的方法）
            System.out.println("\n--- 开始调用AI服务 ---");
            System.out.println("AI服务是否可用: " + (aiService != null ? "是" : "否"));
            long aiCallStartTime = System.currentTimeMillis();
            String aiResponse;
            long aiCallDuration;
            
            try {
                if (useHistory) {
                    // 引用历史对话：获取历史消息并组装
                    System.out.println("准备引用历史对话，key: " + historyKey);
                    List<Map<String, String>> historyMessages = chatHistoryManager.getHistoryMessages(historyKey, chatNickname);
                    System.out.println("引用历史对话，历史消息数量: " + historyMessages.size());
                    
                    // 输出历史消息的详细信息
                    System.out.println("\n--- 历史对话消息详情 ---");
                    for (int i = 0; i < historyMessages.size(); i++) {
                        Map<String, String> msg = historyMessages.get(i);
                        System.out.println("  消息 #" + (i + 1) + ":");
                        System.out.println("    role: " + msg.get("role"));
                        System.out.println("    content: " + (msg.get("content") != null && msg.get("content").length() > 200 
                            ? msg.get("content").substring(0, 200) + "... (截断)" : msg.get("content")));
                    }
                    
                    // 将当前用户提示词添加到消息列表末尾（只有当用户提示词不为空时才添加）
                    if (userPrompt != null && !userPrompt.trim().isEmpty()) {
                        Map<String, String> currentUserMessage = new HashMap<>();
                        currentUserMessage.put("role", "user");
                        currentUserMessage.put("content", userPrompt);
                        historyMessages.add(currentUserMessage);
                        System.out.println("已添加当前用户提示词到消息列表末尾");
                    } else {
                        System.out.println("警告: 当前用户提示词为空，不添加到消息列表");
                    }
                    
                    System.out.println("\n--- 最终发送给AI的消息列表 ---");
                    System.out.println("系统提示词: " + (systemPrompt != null && systemPrompt.length() > 200 
                        ? systemPrompt.substring(0, 200) + "... (截断)" : systemPrompt));
                    System.out.println("总消息数: " + historyMessages.size());
                    for (int i = 0; i < historyMessages.size(); i++) {
                        Map<String, String> msg = historyMessages.get(i);
                        String content = msg.get("content");
                        String role = msg.get("role");
                        
                        // 检查消息是否为空
                        if (content == null || content.trim().isEmpty()) {
                            System.out.println("  消息 #" + (i + 1) + " [role: " + role + "]: <空消息，将被跳过>");
                            continue;
                        }
                        
                        System.out.println("  消息 #" + (i + 1) + " [role: " + role + "]:");
                        if (content.length() > 300) {
                            System.out.println("    " + content.substring(0, 300) + "... (截断，总长度: " + content.length() + " 字符)");
                        } else {
                            System.out.println("    " + content);
                        }
                    }
                    
                    // 过滤掉空消息
                    List<Map<String, String>> filteredMessages = new ArrayList<>();
                    for (Map<String, String> msg : historyMessages) {
                        String content = msg.get("content");
                        if (content != null && !content.trim().isEmpty()) {
                            filteredMessages.add(msg);
                        }
                    }
                    
                    if (filteredMessages.size() != historyMessages.size()) {
                        System.out.println("\n警告: 发现 " + (historyMessages.size() - filteredMessages.size()) + " 条空消息，已过滤");
                        historyMessages = filteredMessages;
                    }
                    
                    System.out.println("\n调用带历史消息的AI服务，总消息数: " + historyMessages.size());
                    // 调用带历史消息的AI服务
                    aiResponse = aiService.chatWithHistory(systemPrompt, historyMessages, "");
                } else {
                    // 不引用历史对话：使用普通调用
                    System.out.println("\n--- 最终发送给AI的消息列表（无历史消息） ---");
                    System.out.println("系统提示词: " + (systemPrompt != null && systemPrompt.length() > 200 
                        ? systemPrompt.substring(0, 200) + "... (截断)" : systemPrompt));
                    System.out.println("用户消息: " + (userPrompt != null && userPrompt.length() > 300 
                        ? userPrompt.substring(0, 300) + "... (截断，总长度: " + userPrompt.length() + " 字符)" : userPrompt));
                    System.out.println("\n调用普通AI服务（无历史消息）");
                    aiResponse = aiService.chat(systemPrompt, userPrompt, "");
                }
                
                aiCallDuration = System.currentTimeMillis() - aiCallStartTime;
                System.out.println("AI服务调用完成，总耗时: " + aiCallDuration + "ms");
            } catch (Exception e) {
                aiCallDuration = System.currentTimeMillis() - aiCallStartTime;
                System.err.println("AI服务调用异常，耗时: " + aiCallDuration + "ms");
                System.err.println("异常类型: " + e.getClass().getName());
                System.err.println("异常信息: " + e.getMessage());
                e.printStackTrace();
                throw e;
            }
            
            System.out.println("\n--- AI响应 ---");
            System.out.println("响应长度: " + (aiResponse != null ? aiResponse.length() : 0) + " 字符");
            System.out.println("响应内容: " + (aiResponse != null && aiResponse.length() > 500 
                ? aiResponse.substring(0, 500) + "... (截断)" : aiResponse));
            
            // 从AI响应中提取JSON
            String jsonStr = extractJsonFromResponse(aiResponse);
            System.out.println("\n--- 提取的JSON ---");
            System.out.println(jsonStr);
            
            // 解析JSON
            Map<String, Object> jsonResult = jsonToMap(jsonStr);
            System.out.println("\n--- 解析后的JSON对象 ---");
            System.out.println(jsonResult);
            
            // 获取选择的分支名称
            String selectedBranchName = (String) jsonResult.get("selectedBranch");
            if (selectedBranchName == null || selectedBranchName.trim().isEmpty()) {
                System.err.println("错误: AI返回的JSON中未包含 selectedBranch 字段");
                throw new IllegalArgumentException("AI response must contain 'selectedBranch' field");
            }
            
            // 验证选择的分支是否在配置的分支列表中
            boolean isValidBranch = false;
            String lastBranchName = (String) branches.get(lastIndex).get("name");
            
            for (Map<String, Object> branch : branches) {
                String branchName = (String) branch.get("name");
                if (selectedBranchName.equals(branchName)) {
                    isValidBranch = true;
                    break;
                }
            }
            
            // 如果不是有效分支，检查是否是默认分支或最后一个分支（默认分支）
            if (!isValidBranch) {
                if (defaultBranch != null && !defaultBranch.isEmpty() && selectedBranchName.equals(defaultBranch)) {
                    isValidBranch = true;
                    System.out.println("选择的是默认分支: " + defaultBranch);
                } else if (selectedBranchName.equals(lastBranchName)) {
                    // 最后一个分支就是默认分支
                    isValidBranch = true;
                    System.out.println("选择的是最后一个分支（默认分支）: " + lastBranchName);
                } else {
                    System.err.println("错误: AI选择的分支 '" + selectedBranchName + "' 不在配置的分支列表中");
                    System.err.println("配置的分支列表: " + branches.stream()
                        .map(b -> (String) b.get("name"))
                        .collect(Collectors.toList()));
                    System.err.println("默认分支（最后一个分支）: " + lastBranchName);
                    throw new IllegalArgumentException("Selected branch '" + selectedBranchName + "' is not in the configured branch list");
                }
            }
            
            // 存储选择的分支名称
            result.setSelectedBranch(selectedBranchName);
            System.out.println("\n--- 大模型分支节点输出结果 ---");
            System.out.println("选择的分支: " + selectedBranchName);
            System.out.println("\n========== 大模型分支节点执行成功 ==========\n");
            
        } catch (Exception e) {
            System.err.println("\n========== 大模型分支节点执行失败 ==========");
            System.err.println("节点Key: " + node.getNodeKey());
            System.err.println("节点名称: " + node.getName());
            System.err.println("错误类型: " + e.getClass().getName());
            System.err.println("错误信息: " + e.getMessage());
            System.err.println("错误堆栈: ");
            e.printStackTrace();
            throw new RuntimeException("Failed to execute LLM branch node: " + node.getNodeKey(), e);
        }
        
        return result;
    }
    
    /**
     * 执行赋值节点
     * 支持配置多条赋值语句，每条语句可以配置变量名和值（表达式）
     */
    private NodeExecutionResult executeAssignNode(StateNode node) {
        System.out.println("\n========== 开始执行赋值节点 ==========");
        System.out.println("节点Key: " + node.getNodeKey());
        System.out.println("节点名称: " + node.getName());
        System.out.println("节点配置: " + node.getConfigJson());
        
        NodeExecutionResult result = new NodeExecutionResult();
        
        try {
            // 解析节点配置
            Map<String, Object> config = jsonToMap(node.getConfigJson());
            
            // 获取赋值语句列表
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> assignments = (List<Map<String, Object>>) config.getOrDefault("assignments", new ArrayList<>());
            
            if (assignments == null || assignments.isEmpty()) {
                System.out.println("警告: 赋值节点没有配置任何赋值语句");
                System.out.println("========== 赋值节点执行结束 ==========\n");
                return result;
            }
            
            System.out.println("赋值语句数量: " + assignments.size());
            System.out.println("当前全局变量: " + variableManager.getAllVariables());
            
            // 依次执行每条赋值语句
            for (int i = 0; i < assignments.size(); i++) {
                Map<String, Object> assignment = assignments.get(i);
                String variableName = (String) assignment.get("variableName");
                String valueExpression = (String) assignment.get("valueExpression");
                
                if (variableName == null || variableName.trim().isEmpty()) {
                    System.err.println("  赋值语句 " + (i + 1) + ": 变量名为空，跳过");
                    continue;
                }
                
                if (valueExpression == null || valueExpression.trim().isEmpty()) {
                    System.err.println("  赋值语句 " + (i + 1) + ": 值表达式为空，跳过");
                    continue;
                }
                
                System.out.println("\n  赋值语句 " + (i + 1) + ":");
                System.out.println("    变量名: " + variableName);
                System.out.println("    值表达式: " + valueExpression);
                
                try {
                    // 使用 ExpressionParser 解析值表达式
                    Object value = expressionParser.parse(valueExpression, variableManager.getAllVariables());
                    System.out.println("    表达式解析结果: " + value + " (类型: " + (value != null ? value.getClass().getSimpleName() : "null") + ")");
                    
                    // 更新变量
                    variableManager.setVariable(variableName, value);
                    result.addUpdatedVariable(variableName, value);
                    
                    System.out.println("    ✓ 变量 " + variableName + " 已赋值为: " + value);
                } catch (Exception e) {
                    System.err.println("    ❌ 赋值语句 " + (i + 1) + " 执行失败:");
                    System.err.println("      变量名: " + variableName);
                    System.err.println("      值表达式: " + valueExpression);
                    System.err.println("      错误: " + e.getMessage());
                    e.printStackTrace();
                    // 继续执行下一条赋值语句，不中断整个节点
                }
            }
            
            System.out.println("\n赋值节点执行完成");
            System.out.println("更新后的全局变量: " + variableManager.getAllVariables());
            System.out.println("========== 赋值节点执行结束 ==========\n");
            
        } catch (Exception e) {
            System.err.println("\n========== 赋值节点执行失败 ==========");
            System.err.println("节点Key: " + node.getNodeKey());
            System.err.println("节点名称: " + node.getName());
            System.err.println("错误类型: " + e.getClass().getName());
            System.err.println("错误信息: " + e.getMessage());
            System.err.println("错误堆栈: ");
            e.printStackTrace();
            throw new RuntimeException("Failed to execute assign node: " + node.getNodeKey(), e);
        }
        
        return result;
    }
    
    /**
     * 执行工作流调用节点
     */
    private NodeExecutionResult executeWorkflowCallNode(StateNode node) {
        // TODO: 实现工作流调用节点逻辑
        NodeExecutionResult result = new NodeExecutionResult();
        return result;
    }
    
    /**
     * 执行HTTP调用节点
     */
    private NodeExecutionResult executeHttpCallNode(StateNode node) {
        // TODO: 实现HTTP调用节点逻辑
        NodeExecutionResult result = new NodeExecutionResult();
        return result;
    }
    
    /**
     * 执行用户输入节点
     */
    private NodeExecutionResult executeUserInputNode(StateWorkflowInstance instance, StateNode node) {
        System.out.println("\n========== 开始执行用户输入节点 ==========");
        System.out.println("节点Key: " + node.getNodeKey());
        System.out.println("节点名称: " + node.getName());
        System.out.println("实例ID: " + instance.getId());
        
        NodeExecutionResult result = new NodeExecutionResult();
        
        try {
            // 解析节点配置
            System.out.println("解析节点配置JSON...");
            Map<String, Object> config = jsonToMap(node.getConfigJson());
            System.out.println("节点配置: " + config);
            
            // 获取配置参数
            String prompt = (String) config.getOrDefault("prompt", "请输入您的消息：");
            // 兼容两种字段名：outputVar 和 outputVariable
            String outputVariable = (String) config.getOrDefault("outputVar", 
                (String) config.getOrDefault("outputVariable", "userInput"));
            
            // 历史对话相关配置
            String historyKey = (String) config.getOrDefault("historyKey", "default");
            Boolean saveToHistory = parseBoolean(config.get("saveToHistory"), true);
            String historyNickname = (String) config.getOrDefault("historyNickname", "用户");
            
            System.out.println("\n--- 用户输入节点配置参数 ---");
            System.out.println("提示消息: " + prompt);
            System.out.println("输出变量名: " + outputVariable);
            System.out.println("历史对话key: " + historyKey);
            System.out.println("是否保存至历史对话: " + saveToHistory);
            System.out.println("历史对话昵称: " + historyNickname);
            
            // 获取当前全局变量，替换提示消息中的变量
            Map<String, Object> currentVariables = variableManager.getAllVariables();
            String promptReplaced = replaceVariablesInTemplate(prompt, currentVariables);
            System.out.println("\n--- 变量替换后的提示消息 ---");
            System.out.println(promptReplaced);
            
            // 发送提示消息到对话界面
            if (chatService != null) {
                System.out.println("准备发送提示消息到对话界面，实例ID: " + instance.getId());
                chatService.sendChatMessage(instance.getId(), "assistant", promptReplaced, "系统");
                System.out.println("已发送提示消息到对话界面");
                
                // 发送状态消息，告诉前端可以输入了
                chatService.sendStatus(instance.getId(), "waiting_user_input");
                System.out.println("已发送状态消息：waiting_user_input");
            } else {
                System.err.println("对话服务未注入，无法发送提示消息！");
            }
            
            // 更新实例状态为等待用户输入
            instance.setStatus("waiting");
            instance.setCurrentNodeKey(node.getNodeKey());
            instance.setUpdatedAt(new Date());
            workflowMapper.updateInstance(instance);
            System.out.println("已更新实例状态为 waiting，等待用户输入...");
            
            // 创建 CompletableFuture 来等待用户输入
            CompletableFuture<String> userInputFuture = new CompletableFuture<>();
            pendingUserInputs.put(instance.getId(), userInputFuture);
            System.out.println("已创建用户输入等待任务，实例ID: " + instance.getId());
            
            // 阻塞等待用户输入（最多等待30分钟）
            System.out.println("开始阻塞等待用户输入...");
            String userInput;
            try {
                userInput = userInputFuture.get(30, java.util.concurrent.TimeUnit.MINUTES);
                System.out.println("收到用户输入，长度: " + (userInput != null ? userInput.length() : 0));
            } catch (java.util.concurrent.TimeoutException e) {
                System.err.println("等待用户输入超时（30分钟）");
                // 移除等待任务
                pendingUserInputs.remove(instance.getId());
                throw new RuntimeException("等待用户输入超时", e);
            } catch (Exception e) {
                System.err.println("等待用户输入时发生异常: " + e.getMessage());
                // 移除等待任务
                pendingUserInputs.remove(instance.getId());
                throw new RuntimeException("等待用户输入失败", e);
            } finally {
                // 确保移除等待任务
                pendingUserInputs.remove(instance.getId());
            }
            
            // 格式化用户输入：添加【用户】说：前缀
            String nicknameForHistory = (historyNickname != null && !historyNickname.trim().isEmpty()) ? historyNickname : "用户";
            String formattedUserInput = userInput;
            System.out.println("格式化后的用户输入: " + formattedUserInput);
            
            // 如果配置了保存至历史对话，则保存到历史对话
            if (saveToHistory) {
                String saveHistoryKey = (String) config.getOrDefault("saveHistoryKey", historyKey);
                // 注意：这里保存的是格式化后的内容，role为user
                chatHistoryManager.addMessage(saveHistoryKey, nicknameForHistory, formattedUserInput);
                System.out.println("已保存用户输入至历史对话，key: " + saveHistoryKey + ", 昵称: " + nicknameForHistory);
            }
            
            // 将用户输入保存到输出变量（保存原始输入，不包含【用户】说：前缀）
            result.addUpdatedVariable(outputVariable, userInput);
            
            // 恢复实例状态为运行中
            instance.setStatus("running");
            instance.setUpdatedAt(new Date());
            workflowMapper.updateInstance(instance);
            System.out.println("已恢复实例状态为 running");
            
            // 发送状态消息，告诉前端禁用输入
            if (chatService != null) {
                chatService.sendStatus(instance.getId(), "running");
                System.out.println("已发送状态消息：running");
            }
            
            System.out.println("\n--- 用户输入节点输出结果 ---");
            System.out.println("输出变量名: " + outputVariable);
            System.out.println("输出变量值: " + (userInput != null && userInput.length() > 200 
                ? userInput.substring(0, 200) + "... (截断)" : userInput));
            System.out.println("更新的变量: " + result.getUpdatedVariables());
            
            System.out.println("\n========== 用户输入节点执行成功 ==========\n");
            
        } catch (Exception e) {
            System.err.println("\n========== 用户输入节点执行失败 ==========");
            System.err.println("节点Key: " + node.getNodeKey());
            System.err.println("节点名称: " + node.getName());
            System.err.println("错误类型: " + e.getClass().getName());
            System.err.println("错误信息: " + e.getMessage());
            System.err.println("错误堆栈: ");
            e.printStackTrace();
            
            // 确保移除等待任务
            pendingUserInputs.remove(instance.getId());
            
            // 恢复实例状态
            try {
                instance.setStatus("failed");
                instance.setUpdatedAt(new Date());
                workflowMapper.updateInstance(instance);
            } catch (Exception updateError) {
                System.err.println("更新实例状态失败: " + updateError.getMessage());
            }
            
            throw new RuntimeException("Failed to execute user input node: " + node.getNodeKey(), e);
        }
        
        return result;
    }
    
    /**
     * 提交用户输入，恢复工作流执行
     * @param instanceId 工作流实例ID
     * @param userInput 用户输入内容
     * @return 是否成功提交
     */
    public boolean submitUserInput(Long instanceId, String userInput) {
        System.out.println("\n========== 收到用户输入提交 ==========");
        System.out.println("实例ID: " + instanceId);
        System.out.println("用户输入长度: " + (userInput != null ? userInput.length() : 0));
        
        CompletableFuture<String> future = pendingUserInputs.get(instanceId);
        if (future == null) {
            System.err.println("错误: 实例 " + instanceId + " 没有等待用户输入的任务");
            return false;
        }
        
        // 完成 CompletableFuture，恢复工作流执行
        boolean completed = future.complete(userInput != null ? userInput : "");
        if (completed) {
            System.out.println("已成功提交用户输入，工作流将继续执行");
        } else {
            System.err.println("警告: CompletableFuture 已完成或已取消");
        }
        
        return completed;
    }
    
    /**
     * 执行信息输出节点
     * 仅向聊天界面输出一条信息，可选写入历史对话，不阻塞等待
     */
    private NodeExecutionResult executeInfoOutputNode(StateWorkflowInstance instance, StateNode node) {
        System.out.println("\n========== 开始执行信息输出节点 ==========");
        System.out.println("节点Key: " + node.getNodeKey());
        System.out.println("节点名称: " + node.getName());
        System.out.println("实例ID: " + instance.getId());
        
        NodeExecutionResult result = new NodeExecutionResult();
        
        try {
            // 解析节点配置
            System.out.println("解析节点配置JSON...");
            Map<String, Object> config = jsonToMap(node.getConfigJson());
            System.out.println("节点配置: " + config);
            
            // 获取配置参数
            String prompt = (String) config.getOrDefault("prompt", "");
            // 历史对话相关配置
            String historyKey = (String) config.getOrDefault("historyKey", "default");
            Boolean saveToHistory = parseBoolean(config.get("saveToHistory"), false);
            String historyNickname = (String) config.getOrDefault("historyNickname", "系统");
            
            System.out.println("\n--- 信息输出节点配置参数 ---");
            System.out.println("提示消息: " + prompt);
            System.out.println("历史对话key: " + historyKey);
            System.out.println("是否保存至历史对话: " + saveToHistory);
            System.out.println("历史对话昵称: " + historyNickname);
            
            // 获取当前全局变量，替换提示消息中的变量
            Map<String, Object> currentVariables = variableManager.getAllVariables();
            String promptReplaced = replaceVariablesInTemplate(prompt, currentVariables);
            System.out.println("\n--- 变量替换后的提示消息 ---");
            System.out.println(promptReplaced);
            
            // 发送提示消息到对话界面（作为assistant角色）
            if (chatService != null && promptReplaced != null && !promptReplaced.trim().isEmpty()) {
                System.out.println("准备发送信息输出到对话界面，实例ID: " + instance.getId());
                chatService.sendChatMessage(instance.getId(), "assistant", promptReplaced, historyNickname);
                System.out.println("已发送信息输出到对话界面");
            } else if (chatService == null) {
                System.err.println("对话服务未注入，无法发送信息输出消息！");
            } else {
                System.out.println("提示消息为空，跳过发送到对话界面");
            }
            
            // 写入历史对话（可选）
            if (saveToHistory && promptReplaced != null && !promptReplaced.trim().isEmpty()) {
                String nicknameForHistory = (historyNickname != null && !historyNickname.trim().isEmpty()) ? historyNickname : "系统";
                String formattedContent = "【" + nicknameForHistory + "】说：" + promptReplaced;
                String saveHistoryKey = (String) config.getOrDefault("saveHistoryKey", historyKey);
                chatHistoryManager.addMessage(saveHistoryKey, nicknameForHistory, formattedContent);
                System.out.println("已保存信息输出至历史对话，key: " + saveHistoryKey + ", 昵称: " + nicknameForHistory);
            }
            
            System.out.println("========== 信息输出节点执行结束 ==========\n");
        } catch (Exception e) {
            System.err.println("\n========== 信息输出节点执行失败 ==========");
            System.err.println("节点Key: " + node.getNodeKey());
            System.err.println("节点名称: " + node.getName());
            System.err.println("错误类型: " + e.getClass().getName());
            System.err.println("错误信息: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to execute info output node: " + node.getNodeKey(), e);
        }
        
        return result;
    }
    
    /**
     * 查找下一个节点
     */
    private StateNode findNextNode(StateNode currentNode, StateWorkflowDefinition workflow, NodeExecutionResult result) {
        System.out.println("\n>>> findNextNode 被调用");
        System.out.println("    当前节点Key: " + currentNode.getNodeKey());
        System.out.println("    当前节点类型: " + currentNode.getType());
        System.out.println("    当前节点名称: " + currentNode.getName());
        
        List<StateTransition> transitions = currentNode.getOutputs();
        if (transitions == null || transitions.isEmpty()) {
            System.out.println("    当前节点没有输出转换");
            return null;
        }
        
        System.out.println("    当前节点有 " + transitions.size() + " 个输出转换");
        
        // 判断当前节点是否是分支节点
        boolean isBranchNode = "basic_branch".equals(currentNode.getType()) || 
                               "llm_branch".equals(currentNode.getType());
        System.out.println("    是否为分支节点: " + isBranchNode);
        
        // 如果是非分支节点，直接返回第一个连接的目标节点（不检查条件表达式）
        if (!isBranchNode) {
            StateTransition firstTransition = transitions.get(0);
            System.out.println("    非分支节点，直接使用第一个转换 -> " + firstTransition.getToNodeKey());
            StateNode nextNode = workflow.getNodes().stream()
                .filter(node -> node.getNodeKey().equals(firstTransition.getToNodeKey()))
                .findFirst()
                .orElse(null);
            if (nextNode != null) {
                System.out.println("    找到目标节点: " + nextNode.getNodeKey() + " (" + nextNode.getName() + ")");
            }
            return nextNode;
        }
        
        // 大模型分支节点：根据AI选择的分支名称匹配转换
        if ("llm_branch".equals(currentNode.getType())) {
            String selectedBranch = result != null ? result.getSelectedBranch() : null;
            if (selectedBranch == null || selectedBranch.trim().isEmpty()) {
                System.err.println("    错误: 大模型分支节点未返回选择的分支名称");
                return null;
            }
            
            System.out.println("    大模型分支节点，AI选择的分支: " + selectedBranch);
            System.out.println("    开始匹配转换...");
            
            // 解析节点配置，获取分支列表
            Map<String, Object> config = jsonToMap(currentNode.getConfigJson());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> branches = (List<Map<String, Object>>) config.getOrDefault("branches", new ArrayList<>());
            String configDefaultBranch = (String) config.getOrDefault("defaultBranch", "");
            
            // 获取最后一个分支名称（默认分支）
            String lastBranchName = !branches.isEmpty() ? (String) branches.get(branches.size() - 1).get("name") : null;
            
            // 如果未设置默认分支，使用最后一个分支作为默认分支
            final String defaultBranch;
            if (configDefaultBranch == null || configDefaultBranch.trim().isEmpty()) {
                if (!branches.isEmpty()) {
                    defaultBranch = lastBranchName;
                    System.out.println("    未设置默认分支，使用最后一个分支作为默认分支: " + defaultBranch);
                } else {
                    defaultBranch = "";
                }
            } else {
                defaultBranch = configDefaultBranch;
            }
            
            // 输出所有分支名称，用于调试
            System.out.println("    配置的分支列表:");
            for (int i = 0; i < branches.size(); i++) {
                Map<String, Object> branch = branches.get(i);
                String branchName = (String) branch.get("name");
                System.out.println("      分支[" + i + "]: " + branchName + (i == branches.size() - 1 ? " (默认分支)" : ""));
            }
            System.out.println("    AI选择的分支: " + selectedBranch);
            System.out.println("    默认分支: " + defaultBranch);
            System.out.println("    最后一个分支名称: " + lastBranchName);
            
            // 查找匹配的转换：转换的 conditionExpression 应该等于分支名称
            StateTransition matchedTransition = null;
            StateTransition defaultTransition = null;
            
            for (int i = 0; i < transitions.size(); i++) {
                StateTransition transition = transitions.get(i);
                String condition = transition.getConditionExpression();
                
                System.out.println("\n    转换[" + i + "]: " + transition.getFromNodeKey() + " -> " + transition.getToNodeKey());
                System.out.println("      条件表达式: " + (condition != null ? condition : "(null)"));
                
                // 如果条件表达式是 "true"，尝试从分支列表中获取正确的分支名称
                if ("true".equals(condition) && !branches.isEmpty()) {
                    // 根据转换的索引，尝试从分支列表中获取对应的分支名称
                    if (i < branches.size()) {
                        String expectedBranchName = (String) branches.get(i).get("name");
                        System.out.println("      警告: 条件表达式为 'true'，尝试使用分支列表中的分支名称: " + expectedBranchName);
                        condition = expectedBranchName; // 使用分支名称进行匹配
                    } else {
                        // 如果索引超出范围，可能是最后一个分支
                        String expectedBranchName = lastBranchName;
                        System.out.println("      警告: 条件表达式为 'true'，索引超出范围，使用最后一个分支名称: " + expectedBranchName);
                        condition = expectedBranchName;
                    }
                }
                
                // 检查是否是默认分支（"default" 或配置的默认分支名称，或最后一个分支）
                if ("default".equals(condition) || 
                    (defaultBranch != null && !defaultBranch.isEmpty() && condition != null && condition.equals(defaultBranch)) ||
                    (lastBranchName != null && condition != null && condition.equals(lastBranchName))) {
                    defaultTransition = transition;
                    System.out.println("      标记为默认转换");
                    // 如果选择的就是默认分支，直接使用
                    if (selectedBranch.equals(defaultBranch) || selectedBranch.equals(lastBranchName)) {
                        matchedTransition = transition;
                        System.out.println("      ✓ 匹配成功（默认分支）！");
                        break;
                    }
                    System.out.println("      不是当前选择的分支，继续查找");
                    continue;
                }
                
                // 检查条件表达式是否等于选择的分支名称
                if (condition != null && condition.equals(selectedBranch)) {
                    matchedTransition = transition;
                    System.out.println("      ✓ 匹配成功！条件表达式 '" + condition + "' 等于选择的分支 '" + selectedBranch + "'");
                    break;
                } else {
                    System.out.println("      ✗ 不匹配: 条件表达式 '" + condition + "' != 选择的分支 '" + selectedBranch + "'");
                }
            }
            
            // 如果找到匹配的转换，返回目标节点
            if (matchedTransition != null) {
                final String targetNodeKey = matchedTransition.getToNodeKey();
                StateNode nextNode = workflow.getNodes().stream()
                    .filter(node -> node.getNodeKey().equals(targetNodeKey))
                    .findFirst()
                    .orElse(null);
                if (nextNode != null) {
                    System.out.println("    找到匹配的目标节点: " + nextNode.getNodeKey() + " (" + nextNode.getName() + ")");
                }
                return nextNode;
            }
            
            // 如果没有匹配的转换，使用默认转换（最后一个分支）
            if (defaultTransition != null) {
                final String defaultTargetNodeKey = defaultTransition.getToNodeKey();
                System.out.println("    未找到匹配的转换，使用默认转换（最后一个分支） -> " + defaultTargetNodeKey);
                StateNode nextNode = workflow.getNodes().stream()
                    .filter(node -> node.getNodeKey().equals(defaultTargetNodeKey))
                    .findFirst()
                    .orElse(null);
                if (nextNode != null) {
                    System.out.println("    找到默认目标节点: " + nextNode.getNodeKey() + " (" + nextNode.getName() + ")");
                }
                return nextNode;
            }
            
            // 如果还是没有找到，尝试使用最后一个转换（对应最后一个分支/默认分支）
            if (!transitions.isEmpty()) {
                StateTransition lastTransition = transitions.get(transitions.size() - 1);
                final String lastTargetNodeKey = lastTransition.getToNodeKey();
                System.out.println("    使用最后一个转换（对应最后一个分支/默认分支） -> " + lastTargetNodeKey);
                StateNode nextNode = workflow.getNodes().stream()
                    .filter(node -> node.getNodeKey().equals(lastTargetNodeKey))
                    .findFirst()
                    .orElse(null);
                if (nextNode != null) {
                    System.out.println("    找到目标节点: " + nextNode.getNodeKey() + " (" + nextNode.getName() + ")");
                }
                return nextNode;
            }
            
            System.err.println("    错误: 未找到匹配的转换，也没有默认转换");
            return null;
        }
        
        // 基础分支节点：依次检查每个分支中的表达式结果转化为整型后是否为非零
        System.out.println("\n========== 基础分支节点：开始评估条件表达式 ==========");
        System.out.println("    当前节点Key: " + currentNode.getNodeKey());
        System.out.println("    当前全局变量: " + variableManager.getAllVariables());
        System.out.println("    分支总数: " + transitions.size());
        
        // 输出所有transitions的详细信息
        System.out.println("    ----------------------------------------");
        System.out.println("    所有分支列表:");
        for (int idx = 0; idx < transitions.size(); idx++) {
            StateTransition t = transitions.get(idx);
            System.out.println("      分支[" + idx + "]: " + t.getFromNodeKey() + " -> " + t.getToNodeKey() + 
                             ", 条件表达式: " + (t.getConditionExpression() != null ? t.getConditionExpression() : "(null)"));
        }
        System.out.println("    ----------------------------------------");
        
        // 最后一个分支固定为默认分支，无需检查条件
        StateTransition defaultTransition = null;
        if (!transitions.isEmpty()) {
            defaultTransition = transitions.get(transitions.size() - 1);
            System.out.println("    默认分支（最后一个，索引 " + (transitions.size() - 1) + "）: " + 
                             defaultTransition.getFromNodeKey() + " -> " + defaultTransition.getToNodeKey() +
                             ", 条件表达式: " + (defaultTransition.getConditionExpression() != null ? defaultTransition.getConditionExpression() : "(null)"));
        }
        
        System.out.println("    ----------------------------------------");
        
        // 依次检查每个分支（除了最后一个默认分支）
        for (int i = 0; i < transitions.size() - 1; i++) {
            StateTransition transition = transitions.get(i);
            String condition = transition.getConditionExpression();
            
            System.out.println("\n    [分支 " + (i + 1) + "]");
            System.out.println("    目标节点: " + transition.getToNodeKey());
            
            // 如果条件表达式为null或空，跳过
            if (condition == null || condition.trim().isEmpty()) {
                System.out.println("    表达式: (空)");
                System.out.println("    表达式结果: N/A");
                System.out.println("    转换为整型: N/A");
                System.out.println("    是否执行分支: 否 (表达式为空)");
                continue;
            }
            
            System.out.println("    表达式: " + condition);
            System.out.println("    开始解析表达式...");
            
            try {
                // 使用 ExpressionParser 解析表达式
                System.out.println("    调用 ExpressionParser.parse()，表达式: \"" + condition + "\"");
                System.out.println("    变量上下文: " + variableManager.getAllVariables());
                Object expressionResult = expressionParser.parse(condition, variableManager.getAllVariables());
                String resultType = expressionResult != null ? expressionResult.getClass().getSimpleName() : "null";
                System.out.println("    表达式结果: " + expressionResult + " (类型: " + resultType + ")");
                
                // 将结果转化为整型
                long intValue = convertExpressionResultToInt(expressionResult);
                System.out.println("    转换为整型: " + intValue);
                
                // 判断是否为非零
                boolean shouldExecute = (intValue != 0);
                System.out.println("    是否执行分支: " + (shouldExecute ? "是 (非零)" : "否 (为零)"));
                
                if (shouldExecute) {
                    System.out.println("    ✓ 选择该分支执行");
                // 查找目标节点
                    final String targetNodeKey = transition.getToNodeKey();
                StateNode nextNode = workflow.getNodes().stream()
                        .filter(node -> node.getNodeKey().equals(targetNodeKey))
                    .findFirst()
                    .orElse(null);
                if (nextNode != null) {
                        System.out.println("    目标节点信息: " + nextNode.getNodeKey() + " (" + nextNode.getName() + ")");
                }
                    System.out.println("========== 基础分支节点：评估完成，执行分支 " + (i + 1) + " ==========\n");
                return nextNode;
                } else {
                    System.out.println("    ✗ 跳过该分支，继续检查下一个");
                }
            } catch (Exception e) {
                System.err.println("\n    ❌ 表达式解析错误!");
                System.err.println("    分支索引: " + i);
                System.err.println("    表达式: \"" + condition + "\"");
                System.err.println("    错误类型: " + e.getClass().getSimpleName());
                System.err.println("    错误消息: " + e.getMessage());
                System.err.println("    表达式结果: 解析失败");
                System.err.println("    转换为整型: N/A");
                System.err.println("    是否执行分支: 否 (解析错误)");
                System.err.println("    完整错误堆栈:");
                e.printStackTrace();
                // 表达式解析失败，跳过该分支
                continue;
            }
        }
        
        System.out.println("\n    ----------------------------------------");
        // 如果没有匹配的条件，使用默认分支（最后一个分支）
        if (defaultTransition != null) {
            final String defaultTargetNodeKey = defaultTransition.getToNodeKey();
            System.out.println("    [默认分支]");
            System.out.println("    表达式: (默认分支，无需条件)");
            System.out.println("    表达式结果: N/A");
            System.out.println("    转换为整型: N/A");
            System.out.println("    是否执行分支: 是 (所有条件分支都不满足)");
            System.out.println("    ✓ 执行默认分支");
            StateNode nextNode = workflow.getNodes().stream()
                .filter(node -> node.getNodeKey().equals(defaultTargetNodeKey))
                .findFirst()
                .orElse(null);
            if (nextNode != null) {
                System.out.println("    目标节点信息: " + nextNode.getNodeKey() + " (" + nextNode.getName() + ")");
            }
            System.out.println("========== 基础分支节点：评估完成，执行默认分支 ==========\n");
            return nextNode;
        }
        
        System.out.println("    ✗ 未找到匹配的转换，也没有默认分支");
        System.out.println("========== 基础分支节点：评估完成，无可用分支 ==========\n");
        return null;
    }
    
    /**
     * 将表达式结果转换为整型
     * 规则：
     * 1. 如果是数字，直接转换为 long
     * 2. 如果是字符串，尝试解析为数字，如果失败则返回 0
     * 3. 如果是布尔值，true 返回 1，false 返回 0
     * 4. 其他情况返回 0
     */
    private long convertExpressionResultToInt(Object result) {
        if (result == null) {
            return 0L;
        }
        
        if (result instanceof Number) {
            return ((Number) result).longValue();
        }
        
        if (result instanceof Boolean) {
            return ((Boolean) result) ? 1L : 0L;
        }
        
        if (result instanceof String) {
            String str = (String) result;
            // 检查是否是 'true'
            if (str.equalsIgnoreCase("true")) {
                return 1L;
            }
            // 尝试解析为整数
            try {
                return Long.parseLong(str);
            } catch (NumberFormatException e) {
                // 不是整数，继续尝试浮点数
            }
            // 尝试解析为浮点数
            try {
                return (long) Double.parseDouble(str);
            } catch (NumberFormatException e) {
                // 不是浮点数，返回0
                return 0L;
            }
        }
        
        // 其他类型转为0
        return 0L;
    }
    
    /**
     * 记录成功执行日志
     */
    private void logExecutionSuccess(StateWorkflowInstance instance, StateNode node, 
                                     NodeExecutionResult result, long executionTime) {
        StateExecutionLog log = new StateExecutionLog();
        log.setInstanceId(instance.getId());
        log.setNodeKey(node.getNodeKey());
        log.setNodeType(node.getType());
        log.setExecutionTime(executionTime);
        log.setStatus("success");
        log.setInputData(mapToJson(variableManager.getAllVariables()));
        log.setOutputData(mapToJson(result.getUpdatedVariables()));
        log.setCreatedAt(new Date());
        
        workflowMapper.insertExecutionLog(log);
    }
    
    /**
     * 记录失败执行日志
     */
    private void logExecutionError(StateWorkflowInstance instance, StateNode node, Exception e) {
        StateExecutionLog log = new StateExecutionLog();
        log.setInstanceId(instance.getId());
        log.setNodeKey(node.getNodeKey());
        log.setNodeType(node.getType());
        log.setExecutionTime(0L);
        log.setStatus("failed");
        log.setInputData(mapToJson(variableManager.getAllVariables()));
        log.setErrorMessage(e.getMessage());
        log.setCreatedAt(new Date());
        
        workflowMapper.insertExecutionLog(log);
    }
    
    /**
     * Map转JSON字符串
     */
    private String mapToJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize object to JSON", e);
        }
    }
    
    /**
     * JSON字符串转Map
     */
    private Map<String, Object> jsonToMap(String jsonStr) {
        try {
            return objectMapper.readValue(jsonStr, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize JSON to Map", e);
        }
    }
    
    /**
     * 安全地将Object转换为Integer
     * 处理Double、Integer、Long等不同的数字类型
     */
    private Integer convertToInteger(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Double) {
            return ((Double) value).intValue();
        }
        if (value instanceof Long) {
            return ((Long) value).intValue();
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }
    
    /**
     * 节点执行结果类
     */
    public static class NodeExecutionResult {
        private Map<String, Object> updatedVariables = new HashMap<>();
        private String selectedBranch; // 用于分支节点：存储选择的分支名称
        
        public Map<String, Object> getUpdatedVariables() {
            return updatedVariables;
        }
        
        public void setUpdatedVariables(Map<String, Object> updatedVariables) {
            this.updatedVariables = updatedVariables;
        }
        
        public void addUpdatedVariable(String name, Object value) {
            this.updatedVariables.put(name, value);
        }
        
        public String getSelectedBranch() {
            return selectedBranch;
        }
        
        public void setSelectedBranch(String selectedBranch) {
            this.selectedBranch = selectedBranch;
        }
    }
}
