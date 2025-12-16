# 工作流引擎重写计划：图灵完备状态机

## 1. 全新JSON结构设计

### 1.1 工作流完整定义示例

```json
{
  "id": 1,
  "name": "示例工作流",
  "description": "图灵完备状态机示例",
  "version": "1.0.0",
  "globalVariables": [
    { "name": "counter", "type": "integer", "initialValue": 0 },
    { "name": "message", "type": "string", "initialValue": "" },
    { "name": "result", "type": "double", "initialValue": 0.0 }
  ],
  "nodes": [
    {
      "id": "start",
      "name": "开始节点",
      "type": "start",
      "config": {
        "inputs": [
          { "variableName": "counter", "initialValue": 0 },
          { "variableName": "message", "initialValue": "Hello" }
        ]
      },
      "outputs": [
        { "targetNodeId": "llm_call", "condition": "true" }
      ]
    },
    {
      "id": "llm_call",
      "name": "大模型调用",
      "type": "llm_call",
      "config": {
        "systemPrompt": "你是一个AI助手",
        "userPrompt": "请处理消息：${message}",
        "outputVariable": "result_message"
      },
      "outputs": [
        { "targetNodeId": "branch", "condition": "true" }
      ]
    },
    {
      "id": "branch",
      "name": "基础分支",
      "type": "basic_branch",
      "config": {
        "conditions": [
          { "expression": "counter < 5", "targetNodeId": "increment" },
          { "expression": "counter >= 5", "targetNodeId": "end" },
          { "expression": "default", "targetNodeId": "end" }
        ]
      }
    },
    {
      "id": "increment",
      "name": "数值运算",
      "type": "math_operation",
      "config": {
        "operation": "add",
        "leftOperand": "counter",
        "rightOperand": 1,
        "outputVariable": "counter"
      },
      "outputs": [
        { "targetNodeId": "llm_call", "condition": "true" }
      ]
    },
    {
      "id": "end",
      "name": "结束节点",
      "type": "end",
      "config": {
        "outputs": [
          { "variableName": "final_result", "sourceVariable": "result_message" },
          { "variableName": "final_count", "sourceVariable": "counter" }
        ]
      }
    }
  ]
}
```

### 1.2 节点通用结构

```json
{
  "id": "node_id",
  "name": "节点名称",
  "type": "node_type",
  "config": {
    // 节点特定配置
  },
  "outputs": [
    {
      "targetNodeId": "target_node_id",
      "condition": "condition_expression",
      "variableMappings": [
        { "source": "source_var", "target": "target_var" }
      ]
    }
  ]
}
```

## 2. 完全重写实现方案

### 2.1 后端重写

1. **数据模型全新设计**

   * 废弃原有Workflow、WorkflowNode、WorkflowEdge实体

   * 新设计：WorkflowDefinition、StateNode、Transition、GlobalVariable实体

   * 数据库表结构重新设计

2. **执行引擎全新实现**

   * 基于状态机理论的执行引擎

   * 支持循环执行（成环结构）

   * 全局变量上下文管理

   * 并行执行支持

   * 分支逻辑实现

3. **节点类型全新实现**

   * 开始节点（start）

   * 结束节点（end）

   * 大模型调用节点（llm\_call）

   * 简易并行化节点（parallel）

   * 基础分支节点（basic\_branch）

   * 大模型分支节点（llm\_branch）

   * 二元数值运算节点（math\_operation）

   * 工作流节点（workflow\_call）

   * HTTP调用节点（http\_call）

### 2.2 前端重写

1. **工作流设计器全新实现**

   * 支持状态机可视化编辑

   * 支持节点成环连接

   * 支持全局变量配置

   * 节点配置界面重新设计

2. **执行监控全新实现**

   * 实时显示当前执行节点

   * 全局变量实时更新

   * 执行日志可视化

## 3. 核心功能设计

### 3.1 状态机执行逻辑

* 从开始节点启动

* 执行当前节点，更新全局变量

* 根据输出条件选择下一个节点

* 支持循环执行，避免死循环（通过最大执行次数限制）

* 并行节点内部同时执行，全部完成后继续

### 3.2 全局变量系统

* 支持类型：string, integer, double

* 变量作用域：整个工作流实例

* 变量操作：读取、写入、运算

* 支持参数化字符串（${variableName}）

### 3.3 条件表达式引擎

* 支持数值比较：==, !=, >, <, >=, <=

* 支持字符串操作：contains, notContains, equals

* 支持逻辑运算：&&, ||, !

* 支持变量和常量混合使用

* 示例："counter < 10 && message.contains('success')"

## 4. 实现步骤

### 4.1 后端实现

1. 设计并创建全新数据库表结构
2. 实现核心实体类和Repository
3. 实现状态机执行引擎
4. 实现各节点类型的执行逻辑
5. 实现条件表达式解析器
6. 实现全局变量管理
7. 开发RESTful API

### 4.2 前端实现

1. 实现工作流设计器界面
2. 实现节点拖拽和连接功能
3. 实现节点配置弹窗
4. 实现全局变量配置界面
5. 实现工作流执行监控界面
6. 开发API调用层

## 5. 关键技术点

1. **图灵完备性**：支持循环和条件分支，实现图灵完备
2. **状态管理**：高效的状态转换和上下文管理
3. **并行执行**：多线程/异步执行支持
4. **表达式解析**：安全高效的表达式引擎
5. **可视化编辑**：直观的状态机设计界面
6. **类型安全**：严格的变量类型检查

## 6. 预期效果

* 完全图灵完备的工作流引擎

* 支持复杂业务逻辑和循环执行

* 直观的可视化设计界面

* 高效的执行性能

* 良好的扩展性，支持自定义节点类型

* 完善的监控和日志系统

