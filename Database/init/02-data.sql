-- 从agent_workflow.sql备份的数据库数据
SET NAMES utf8mb4;
USE agent_workflow;

-- 插入管理员数据
INSERT INTO `admin` (`id`, `username`, `password`, `nickname`, `email`, `phone`, `avatar`, `role_id`, `status`, `last_login_time`, `created_at`, `updated_at`, `is_deleted`) VALUES
(3, 'admin', '$2a$10$MeiwruTQYcKArS7FiNBlHOvSZpjGwk.USJSYbG8dVvc7..zljjPFm', '管理员', 'admin@example.com', '13800138000', NULL, 1, 1, '2025-11-18 12:36:20', '2025-11-21 08:12:13', '2025-11-21 08:12:13', 0);

-- 插入智能体数据
INSERT INTO `agent` (`id`, `name`, `description`, `type`, `config_json`, `status`, `created_by`, `created_at`, `updated_at`, `is_deleted`) VALUES
(8, '更新的智能体名称', '这是更新后的描述', 'default', NULL, 1, 14, '2025-11-21 19:41:50', '2025-11-21 19:53:12', 0),
(9, '智能体01', '', 'knowledge', NULL, 1, 14, '2025-11-21 20:38:00', '2025-11-22 10:52:22', 0),
(10, '更新测试', '', 'general', NULL, 1, 14, '2025-11-22 10:51:18', '2025-11-22 10:51:56', 0),
(11, '删除测试', '123', 'general', NULL, 1, 14, '2025-11-22 10:52:35', '2025-11-22 02:52:39', 1),
(12, '123', '', 'general', NULL, 1, -3, '2025-11-22 10:53:20', '2025-11-22 10:53:20', 0),
(13, 'user1创建内容', '', 'creative', NULL, 1, 21, '2025-11-22 10:58:00', '2025-11-22 10:58:00', 0),
(14, '删除02', '', 'general', NULL, 1, 14, '2025-11-22 11:03:42', '2025-11-22 03:04:09', 1);

-- 插入聊天消息数据
INSERT INTO `chat_message` (`id`, `session_id`, `sender_type`, `content`, `content_type`, `created_at`, `is_deleted`) VALUES
(1, 1, 0, '你好，我想了解一下你们的服务', 'text', '2025-11-21 08:12:13', 0),
(2, 1, 1, '您好！我是客服智能助手，很高兴为您服务。请问有什么可以帮助您的吗？', 'text', '2025-11-21 08:12:13', 0),
(3, 2, 0, '能帮我看一下这段代码有什么问题吗？', 'text', '2025-11-21 08:12:13', 0),
(4, 2, 1, '当然可以，请您提供代码，我会帮您检查。', 'text', '2025-11-21 08:12:13', 0),
(5, 3, 0, '我想学习微积分，能给我一些建议吗？', 'text', '2025-11-21 08:12:13', 0),
(6, 3, 1, '您好！微积分是一门很重要的数学学科。我建议您从极限和导数的基本概念开始学习...', 'text', '2025-11-21 08:12:13', 0);

-- 插入聊天会话数据
INSERT INTO `chat_session` (`id`, `user_id`, `title`, `status`, `created_at`, `updated_at`, `is_deleted`) VALUES
(1, 14, '与客服智能体的对话', 1, '2025-11-21 08:12:13', '2025-11-21 08:12:13', 0),
(2, 14, '与开发助手的对话', 1, '2025-11-21 08:12:13', '2025-11-21 08:12:13', 0),
(3, 14, '与学习导师的对话', 1, '2025-11-21 08:12:13', '2025-11-21 08:12:13', 0);

-- 插入权限数据
INSERT INTO `permission` (`id`, `name`, `code`, `description`, `created_at`, `updated_at`) VALUES
(1, '用户管理', 'user:manage', '管理用户信息', '2025-11-21 08:12:13', '2025-11-21 08:12:13'),
(2, '智能体管理', 'agent:manage', '管理智能体', '2025-11-21 08:12:13', '2025-11-21 08:12:13'),
(3, '知识库管理', 'knowledge:manage', '管理知识库', '2025-11-21 08:12:13', '2025-11-21 08:12:13'),
(4, '插件管理', 'plugin:manage', '管理插件', '2025-11-21 08:12:13', '2025-11-21 08:12:13'),
(5, '聊天管理', 'chat:manage', '管理聊天记录', '2025-11-21 08:12:13', '2025-11-21 08:12:13'),
(6, '系统配置', 'system:config', '配置系统参数', '2025-11-21 08:12:13', '2025-11-21 08:12:13'),
(7, '数据迁移', 'data:migrate', '执行数据迁移操作', '2025-11-21 08:12:13', '2025-11-21 08:12:13');

-- 插入角色数据
INSERT INTO `role` (`id`, `name`, `description`, `status`, `created_at`, `updated_at`) VALUES
(1, '管理员', '系统管理员角色，拥有最高权限', 1, '2025-11-21 08:12:13', '2025-11-21 08:12:13'),
(2, '普通用户', '普通用户角色', 1, '2025-11-21 08:12:13', '2025-11-21 08:12:13');

-- 插入角色权限关联数据
INSERT INTO `role_permission` (`role_id`, `permission_id`) VALUES
(1, 1),
(1, 2),
(1, 3),
(1, 4),
(1, 5),
(1, 6),
(1, 7);

-- 插入用户数据
INSERT INTO `user` (`id`, `username`, `password`, `email`, `phone`, `nickname`, `avatar`, `gender`, `birthdate`, `status`, `created_at`, `updated_at`, `last_login_at`, `is_deleted`, `role_id`) VALUES
(14, 'user', '$2a$10$IJ1qcZw75SeXofL/yfHd1uTtx/2y7G.Y9L.gnUQlq7sBRg2fhKVfm', 'tttt@example.com', '13979999979', '李四', NULL, 1, '1997-03-01', 1, '2025-11-21 08:12:13', '2025-11-21 15:59:34', '2025-11-18 12:35:59', 0, 2),
(15, 'testuser123', '$2a$10$JQUrIpWGOlXEJ9HmBw4VxeESolCGvpi5zy4vPF5WW/fwOLMpEg2aW', 'test@example.com', '13800138000', '测试用户', NULL, NULL, '1414-01-01', 1, '2025-11-21 18:11:32', '2025-11-22 03:01:56', NULL, 0, 2),
(21, 'user1', '$2a$10$jsroc7l1F563vDUQxDFBtuNeF/l73haVkSUdW42Pd7yGqd/4iH42y', 'lehthe@gmail.com', '12312312312', '王五', NULL, NULL, '1990-01-01', 1, '2025-11-22 10:56:00', '2025-11-22 03:02:31', NULL, 0, 2);

-- 插入初始状态机工作流示例
INSERT INTO `state_workflow_definition` (`name`, `description`, `version`, `status`, `created_by`, `json_definition`) VALUES
('测试状态机工作流', '一个简单的状态机工作流示例', '1.0.0', 1, 14, '{"globalVariables": [{"name": "counter", "type": "integer", "initialValue": "0"}, {"name": "message", "type": "string", "initialValue": "hello"}, {"name": "value", "type": "double", "initialValue": "10.5"}], "nodes": [{"key": "start", "name": "开始节点", "type": "start", "config": {"inputs": [{"variableName": "counter", "initialValue": 0}]}, "positionX": 100, "positionY": 200}, {"key": "math_op", "name": "数值运算", "type": "math_operation", "config": {"operation": "add", "leftOperand": "counter", "rightOperand": "1", "outputVariable": "counter"}, "positionX": 300, "positionY": 200}, {"key": "branch", "name": "基础分支", "type": "basic_branch", "config": {"conditions": [{"expression": "counter < 3", "targetNodeId": "math_op"}, {"expression": "default", "targetNodeId": "end"}]}, "positionX": 500, "positionY": 200}, {"key": "end", "name": "结束节点", "type": "end", "config": {"outputs": [{"variableName": "final_count", "sourceVariable": "counter"}]}, "positionX": 700, "positionY": 200}], "transitions": [{"fromNode": "start", "toNode": "math_op", "condition": "true"}, {"fromNode": "math_op", "toNode": "branch", "condition": "true"}, {"fromNode": "branch", "toNode": "math_op", "condition": "counter < 3"}, {"fromNode": "branch", "toNode": "end", "condition": "default"}]}');


-- 插入示例工作流数据
INSERT INTO state_workflow_definition (name, description, version, status, created_by, json_definition, created_at, updated_at, is_deleted)
VALUES (
    '带分支的复杂工作流示例',
    '包含大模型调用、条件分支和多路径处理的复杂工作流',
    '1.0.0',
    1,
    1,
    '{"nodes": [{"key": "start_node", "name": "开始节点", "type": "start", "positionX": 100, "positionY": 200, "config": {}}, {"key": "llm_question", "name": "大模型问答", "type": "llm_call", "positionX": 300, "positionY": 200, "config": {"systemPrompt": "你是一个专业的AI助手，回答要简洁明了", "userPrompt": "请回答：${question}", "outputVar": "answer"}}, {"key": "branch_node", "name": "回答长度分支", "type": "basic_branch", "positionX": 500, "positionY": 200, "config": {"branchType": "conditional", "defaultBranch": "short_answer"}}, {"key": "long_answer", "name": "长回答处理", "type": "llm_call", "positionX": 700, "positionY": 100, "config": {"systemPrompt": "你是一个专业的AI助手，请将长回答总结为一句话", "userPrompt": "请总结：${answer}", "outputVar": "summary"}}, {"key": "short_answer", "name": "短回答处理", "type": "llm_call", "positionX": 700, "positionY": 300, "config": {"systemPrompt": "你是一个专业的AI助手，请将短回答润色得更生动", "userPrompt": "请润色：${answer}", "outputVar": "enhanced_answer"}}, {"key": "final_process", "name": "最终处理", "type": "llm_call", "positionX": 900, "positionY": 200, "config": {"systemPrompt": "你是一个专业的AI助手，请给出最终回复", "userPrompt": "最终结果：${summary || enhanced_answer || answer}", "outputVar": "final_result"}}, {"key": "end_node", "name": "结束节点", "type": "end", "positionX": 1100, "positionY": 200, "config": {}}], "transitions": [{"fromNode": "start_node", "toNode": "llm_question", "condition": "true"}, {"fromNode": "llm_question", "toNode": "branch_node", "condition": "true"}, {"fromNode": "branch_node", "toNode": "long_answer", "condition": "answer.length > 100"}, {"fromNode": "branch_node", "toNode": "short_answer", "condition": "answer.length <= 100"}, {"fromNode": "long_answer", "toNode": "final_process", "condition": "true"}, {"fromNode": "short_answer", "toNode": "final_process", "condition": "true"}, {"fromNode": "final_process", "toNode": "end_node", "condition": "true"}], "globalVariables": [{"name": "question", "type": "string", "initialValue": "请详细介绍人工智能的发展历程和未来趋势"}, {"name": "answer", "type": "string", "initialValue": ""}, {"name": "summary", "type": "string", "initialValue": ""}, {"name": "enhanced_answer", "type": "string", "initialValue": ""}, {"name": "final_result", "type": "string", "initialValue": ""}]}',
    NOW(),
    NOW(),
    0
);
