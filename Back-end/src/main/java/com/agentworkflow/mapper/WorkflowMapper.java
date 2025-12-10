package com.agentworkflow.mapper;

import com.agentworkflow.entity.Workflow;
import com.agentworkflow.entity.WorkflowInstance;
import com.agentworkflow.entity.WorkflowExecutionLog;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface WorkflowMapper {
    // 工作流定义相关方法
    List<Workflow> getAllWorkflows();
    List<Workflow> getWorkflowsByCreatedBy(Long createdBy);
    Workflow getWorkflowById(Long id);
    Workflow getWorkflowByIdAndCreatedBy(Long id, Long createdBy);
    int insertWorkflow(Workflow workflow);
    int updateWorkflow(Workflow workflow);
    int deleteWorkflow(Long id);

    // 工作流实例相关方法
    List<WorkflowInstance> getAllInstances();
    List<WorkflowInstance> getInstancesByWorkflowId(Long workflowId);
    WorkflowInstance getInstanceById(Long id);
    int insertInstance(WorkflowInstance instance);
    int updateInstance(WorkflowInstance instance);

    // 工作流执行日志相关方法
    List<WorkflowExecutionLog> getExecutionLogsByInstanceId(Long instanceId);
    int insertExecutionLog(WorkflowExecutionLog log);
}