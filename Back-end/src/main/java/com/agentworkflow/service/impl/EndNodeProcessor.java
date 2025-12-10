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
public class EndNodeProcessor implements NodeProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EndNodeProcessor.class);

    @Override
    public String getNodeType() {
        return "end";
    }

    @Override
    public Map<String, Object> process(WorkflowNode node, WorkflowInstance instance, Map<String, Object> context) {
        logger.debug("执行结束节点，实例: {}, 节点: {}, 上下文: {}", 
                    instance.getId(), node.getId(), context);
        
        // 结束节点只需要返回上下文作为最终输出
        Map<String, Object> result = new HashMap<>();
        result.put("outputData", context);
        result.put("status", "success");
        // 结束节点表示工作流结束，不需要返回下一个节点
        result.put("nextNodeId", null);
        
        logger.debug("结束节点执行完成，实例: {}, 节点: {}, 最终输出: {}", 
                    instance.getId(), node.getId(), context);
        
        return result;
    }
}
