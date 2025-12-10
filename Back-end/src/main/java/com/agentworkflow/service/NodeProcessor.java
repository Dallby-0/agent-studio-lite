package com.agentworkflow.service;

import com.agentworkflow.entity.WorkflowNode;
import com.agentworkflow.entity.WorkflowInstance;

import java.util.Map;

public interface NodeProcessor {
    /**
     * 获取支持的节点类型
     * @return 节点类型
     */
    String getNodeType();
    
    /**
     * 处理节点逻辑
     * @param node 节点信息
     * @param instance 工作流实例
     * @param context 上下文
     * @return 处理结果，包含输出数据和下一个节点ID
     */
    Map<String, Object> process(WorkflowNode node, WorkflowInstance instance, Map<String, Object> context);
}
