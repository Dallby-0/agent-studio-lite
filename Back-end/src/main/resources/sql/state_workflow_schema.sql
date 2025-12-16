-- 状态机工作流定义表
CREATE TABLE IF NOT EXISTS state_workflow_definition (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL COMMENT '工作流名称',
    description TEXT COMMENT '工作流描述',
    version VARCHAR(50) NOT NULL COMMENT '工作流版本',
    status INT NOT NULL DEFAULT 1 COMMENT '工作流状态：1-启用，0-禁用',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='状态机工作流定义表';

-- 状态节点表
CREATE TABLE IF NOT EXISTS state_node (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id BIGINT NOT NULL COMMENT '所属工作流ID',
    node_key VARCHAR(50) NOT NULL COMMENT '节点唯一标识',
    name VARCHAR(255) NOT NULL COMMENT '节点名称',
    type VARCHAR(50) NOT NULL COMMENT '节点类型：start, end, llm_call, llm_assign, parallel, basic_branch, llm_branch, math_operation, workflow_call, http_call',
    config_json TEXT NOT NULL COMMENT '节点配置JSON',
    position_x INT DEFAULT 0 COMMENT '节点位置X坐标',
    position_y INT DEFAULT 0 COMMENT '节点位置Y坐标',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (workflow_id) REFERENCES state_workflow_definition(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='状态节点表';

-- 状态转换表
CREATE TABLE IF NOT EXISTS state_transition (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id BIGINT NOT NULL COMMENT '所属工作流ID',
    from_node_key VARCHAR(50) NOT NULL COMMENT '源节点标识',
    to_node_key VARCHAR(50) NOT NULL COMMENT '目标节点标识',
    condition_expression VARCHAR(255) NOT NULL DEFAULT 'true' COMMENT '转换条件表达式',
    variable_mappings JSON COMMENT '变量映射配置',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (workflow_id) REFERENCES state_workflow_definition(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='状态转换表';

-- 全局变量表
CREATE TABLE IF NOT EXISTS state_global_variable (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id BIGINT NOT NULL COMMENT '所属工作流ID',
    variable_name VARCHAR(50) NOT NULL COMMENT '变量名称',
    variable_type VARCHAR(20) NOT NULL COMMENT '变量类型：string, integer, double',
    initial_value TEXT COMMENT '初始值',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (workflow_id) REFERENCES state_workflow_definition(id) ON DELETE CASCADE,
    UNIQUE KEY uk_workflow_variable (workflow_id, variable_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='全局变量表';

-- 工作流实例表
CREATE TABLE IF NOT EXISTS state_workflow_instance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id BIGINT COMMENT '所属工作流ID',
    name VARCHAR(255) NOT NULL COMMENT '实例名称',
    status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '实例状态：pending, running, completed, failed, canceled',
    input_params JSON COMMENT '输入参数',
    output_params JSON COMMENT '输出参数',
    current_node_key VARCHAR(50) COMMENT '当前执行节点标识',
    global_variables JSON NOT NULL COMMENT '全局变量当前值',
    started_at DATETIME DEFAULT NULL COMMENT '开始执行时间',
    finished_at DATETIME DEFAULT NULL COMMENT '结束执行时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (workflow_id) REFERENCES state_workflow_definition(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流实例表';

-- 执行日志表
CREATE TABLE IF NOT EXISTS state_execution_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    instance_id BIGINT NOT NULL COMMENT '所属实例ID',
    node_key VARCHAR(50) NOT NULL COMMENT '执行节点标识',
    node_type VARCHAR(50) NOT NULL COMMENT '节点类型',
    execution_time BIGINT NOT NULL COMMENT '执行耗时(毫秒)',
    status VARCHAR(20) NOT NULL COMMENT '执行状态：success, failed',
    input_data JSON COMMENT '输入数据',
    output_data JSON COMMENT '输出数据',
    error_message TEXT COMMENT '错误信息',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    FOREIGN KEY (instance_id) REFERENCES state_workflow_instance(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='执行日志表';
