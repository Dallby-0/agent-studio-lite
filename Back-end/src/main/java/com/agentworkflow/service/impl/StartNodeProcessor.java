package com.agentworkflow.service.impl;

import com.agentworkflow.entity.WorkflowNode;
import com.agentworkflow.entity.WorkflowInstance;
import com.agentworkflow.service.NodeProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.HashMap;

@Component
public class StartNodeProcessor implements NodeProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StartNodeProcessor.class);

    @Override
    public String getNodeType() {
        return "start";
    }

    @Override
    public Map<String, Object> process(WorkflowNode node, WorkflowInstance instance, Map<String, Object> context) {
        logger.debug("执行开始节点，实例: {}, 节点: {}, 上下文: {}", 
                    instance.getId(), node.getId(), context);
        
        // 开始节点只需要传递上下文，不需要执行特殊逻辑
        Map<String, Object> result = new HashMap<>();
        result.put("outputData", context);
        result.put("status", "success");
        
        logger.debug("开始节点执行完成，实例: {}, 节点: {}, 结果: {}", 
                    instance.getId(), node.getId(), result);
        
        // 开始节点没有输入数据，直接返回上下文作为输出
        return result;
    }
}
