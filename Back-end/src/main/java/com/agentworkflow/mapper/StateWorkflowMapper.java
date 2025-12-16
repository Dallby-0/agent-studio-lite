package com.agentworkflow.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.agentworkflow.entity.StateWorkflowDefinition;
import com.agentworkflow.entity.StateNode;
import com.agentworkflow.entity.StateTransition;
import com.agentworkflow.entity.GlobalVariable;
import com.agentworkflow.entity.StateWorkflowInstance;
import com.agentworkflow.entity.StateExecutionLog;

@Mapper
public interface StateWorkflowMapper {
    // 工作流定义相关
    List<StateWorkflowDefinition> getAllWorkflows();
    List<StateWorkflowDefinition> getWorkflowsByCreatedBy(Long createdBy);
    StateWorkflowDefinition getWorkflowById(Long id);
    StateWorkflowDefinition getWorkflowByIdAndCreatedBy(Long id, Long createdBy);
    int insertWorkflow(StateWorkflowDefinition workflow);
    int updateWorkflow(StateWorkflowDefinition workflow);
    int deleteWorkflow(Long id);
    
    // 实例相关
    List<StateWorkflowInstance> getAllInstances();
    List<StateWorkflowInstance> getInstancesByWorkflowId(Long workflowId);
    StateWorkflowInstance getInstanceById(Long id);
    int insertInstance(StateWorkflowInstance instance);
    int updateInstance(StateWorkflowInstance instance);
    int deleteInstance(Long id);
    
    // 执行日志相关
    List<StateExecutionLog> getExecutionLogsByInstanceId(Long instanceId);
    int insertExecutionLog(StateExecutionLog log);
    int deleteExecutionLogsByInstanceId(Long instanceId);
}
