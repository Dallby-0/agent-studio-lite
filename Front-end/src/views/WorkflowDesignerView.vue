<template>
  <Layout :userInfo="userStore.userInfo">
    <div class="workflow-designer-view">
      <!-- 顶部导航 -->
      <div class="view-header">
        <h1>{{ isEditing ? '编辑工作流' : '创建工作流' }}</h1>
      </div>
      
      <!-- 工作流设计器组件 -->
      <div class="designer-wrapper">
        <WorkflowDesigner
          :workflow="workflow"
          @save="onSaveWorkflow"
          @run="onRunWorkflow"
          @reset="resetWorkflow"
          @goBack="goBack"
        />
      </div>
      
      <!-- 工作流对话界面 -->
      <WorkflowChatDialog
        v-model="showChatDialog"
        :instance-id="currentInstanceId"
        @close="handleChatDialogClose"
      />
      
      <!-- 运行参数配置对话框 -->
      <el-dialog
        v-model="showRunParamsDialog"
        title="运行工作流"
        width="500px"
        :modal="false"
        center
      >
        <el-form label-position="top" size="small">
          <el-form-item label="工作流名称">
            <el-input v-model="runParams.name" placeholder="输入工作流实例名称"></el-input>
          </el-form-item>
          <el-form-item label="输入参数">
            <el-input 
              v-model="runParams.inputParams" 
              type="textarea" 
              :rows="6" 
              placeholder='输入JSON格式的参数，例如：{"question": "你好"}'
            ></el-input>
          </el-form-item>
        </el-form>
        <template #footer>
          <span class="dialog-footer">
            <el-button @click="showRunParamsDialog = false">取消</el-button>
            <el-button type="primary" @click="executeWorkflow">确定运行</el-button>
          </span>
        </template>
      </el-dialog>
      
      <!-- 执行结果对话框 -->
      <el-dialog
        v-model="showResultDialog"
        title="工作流执行结果"
        width="700px"
        center
        :z-index="2000"
      >
        <div class="execution-result">
          <!-- 执行状态 -->
          <div class="execution-status">
            <el-alert
              :type="executionStatus === 'completed' ? 'success' : (executionStatus === 'failed' ? 'error' : 'info')"
              :title="executionStatusText"
              show-icon
            ></el-alert>
          </div>
          
          <!-- 执行进度 -->
          <div class="execution-progress" v-if="executionStatus === 'running'">
            <el-progress :percentage="progress" :status="'warning'" :indeterminate="true"></el-progress>
            <div class="progress-text">当前执行节点：{{ currentNodeName || '未知' }}</div>
          </div>
          
          <!-- 执行日志 -->
          <div class="execution-logs">
            <h4>执行日志</h4>
            <el-scrollbar height="200px" class="logs-scrollbar">
              <div v-for="(log, index) in executionLogs" :key="index" class="log-item">
                <span class="log-time">{{ log.timestamp }}</span>
                <span class="log-node">{{ log.nodeName }}</span>
                <span :class="['log-status', log.status]">{{ log.status }}</span>
                <span class="log-message">{{ log.message }}</span>
              </div>
              <div v-if="executionLogs.length === 0" class="no-logs">暂无执行日志</div>
            </el-scrollbar>
          </div>
          
          <!-- 执行结果 -->
          <div class="execution-result-data" v-if="executionStatus !== 'running'">
            <h4>执行结果</h4>
            <el-card>
              <pre class="result-text">{{ formattedResult }}</pre>
            </el-card>
          </div>
        </div>
        
        <template #footer>
          <span class="dialog-footer">
            <el-button @click="closeResultDialog">关闭</el-button>
            <el-button type="primary" @click="viewInstanceDetails" v-if="currentInstanceId">查看详情</el-button>
          </span>
        </template>
      </el-dialog>
    </div>
  </Layout>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
// 导入Element Plus图标
import { ArrowLeft, VideoPlay } from '@element-plus/icons-vue'

import Layout from '../components/Layout.vue'
import WorkflowDesigner from '../components/WorkflowDesigner.vue'
import WorkflowChatDialog from '../components/WorkflowChatDialog.vue'
import { useUserStore } from '../stores/userStore'

// API 导入
import { getWorkflowById, createWorkflow, updateWorkflow, runWorkflow, getWorkflowInstanceLogs, getWorkflowInstanceById } from '../api/workflow'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

// 状态管理
const workflow = ref({
  id: null,
  name: '',
  description: '',
  version: '1.0.0',
  status: 1,
  definition: {
    nodes: [],
    transitions: [],
    globalVariables: []
  }
})
const isEditing = ref(false)
const isSaving = ref(false)
const isRunning = ref(false)
const showRunParamsDialog = ref(false)
const runParams = reactive({
  name: '',
  inputParams: '{}'
})

// 执行结果对话框状态
const showResultDialog = ref(false)
const currentInstanceId = ref(null)
const executionStatus = ref('running') // running, completed, failed
const executionStatusText = ref('工作流正在执行中...')
const currentNodeName = ref('')
const progress = ref(0)
const executionLogs = ref([])
const executionResult = ref({})
const formattedResult = ref('')
const showChatDialog = ref(false)

// 重置工作流
const resetWorkflow = () => {
  ElMessageBox.confirm('确定要重置工作流吗？这将清除当前所有设计内容。', '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    workflow.value = {
      id: null,
      name: '',
      description: '',
      version: '1.0.0',
      status: 1,
      definition: {
        nodes: [],
        transitions: [],
        globalVariables: []
      }
    }
    ElMessage.success('工作流已重置')
  }).catch(() => {
    // 取消操作，不做任何处理
  })
}

// 返回上一级
const goBack = () => {
  router.back()
}

// 加载工作流定义
const loadWorkflowDefinition = (id) => {
    console.log('=== 调试：开始加载工作流定义 ===')
    console.log('加载的工作流ID:', id)
    
    // 调用真实API获取工作流定义
    getWorkflowById(id).then(response => {
      console.log('=== 调试：获取到工作流定义 ===')
      console.log('API响应完整数据:', response)
      
      // 直接使用后端返回的完整工作流对象
      const workflowData = response.data || response
      console.log('workflowData:', workflowData)
      console.log('workflowData.hasOwnProperty(jsonDefinition):', workflowData.hasOwnProperty('jsonDefinition'))
      console.log('workflowData.jsonDefinition:', workflowData.jsonDefinition)
      console.log('workflowData.hasOwnProperty(definition):', workflowData.hasOwnProperty('definition'))
      console.log('workflowData.definition:', workflowData.definition)
      
      // 处理jsonDefinition字段，转换为definition字段
      if (workflowData.jsonDefinition) {
        try {
          // 如果jsonDefinition是字符串，将其解析为对象
          const parsedDefinition = typeof workflowData.jsonDefinition === 'string' 
            ? JSON.parse(workflowData.jsonDefinition) 
            : workflowData.jsonDefinition
          
          // 添加definition字段
          workflowData.definition = parsedDefinition
          console.log('解析后的definition:', workflowData.definition)
        } catch (error) {
          console.error('=== 调试：解析jsonDefinition失败 ===')
          console.error('错误信息:', error)
          // 如果解析失败，使用默认值
          workflowData.definition = {
            nodes: [],
            transitions: [],
            globalVariables: []
          }
        }
      } else {
        // 如果没有jsonDefinition字段，使用默认值
        workflowData.definition = {
          nodes: [],
          transitions: [],
          globalVariables: []
        }
      }
      
      // 更新workflow.value
      workflow.value = workflowData
      
      console.log('=== 调试：工作流数据赋值完成 ===')
      console.log('最终工作流数据:', workflow.value)
      console.log('最终definition:', workflow.value.definition)
      
      // 处理工作流定义
      if (workflow.value.definition) {
        // 确保definition结构正确
        if (!workflow.value.definition.transitions && workflow.value.definition.edges) {
          // 兼容旧数据结构
          workflow.value.definition.transitions = workflow.value.definition.edges
          delete workflow.value.definition.edges
        }
        
        if (!workflow.value.definition.globalVariables) {
          workflow.value.definition.globalVariables = []
        }
        
        console.log('最终节点数量:', workflow.value.definition.nodes?.length)
        console.log('最终转换数量:', workflow.value.definition.transitions?.length)
        console.log('最终全局变量数量:', workflow.value.definition.globalVariables?.length)
      }
    }).catch(error => {
      console.error('=== 调试：加载工作流失败 ===')
      console.error('错误信息:', error)
      ElMessage.error('加载工作流失败')
      console.error('加载工作流失败:', error)
      // 加载失败时使用模拟数据，包含definition字段
      workflow.value = {
        id: id,
        name: '示例工作流',
        description: '这是一个示例工作流',
        version: '1.0.0',
        status: 1,
        definition: {
          nodes: [
            {
              nodeKey: 'start_node',
              name: '开始节点',
              type: 'start',
              positionX: 100,
              positionY: 200,
              config: {}
            },
            {
              nodeKey: 'llm_call_1',
              name: '大模型调用',
              type: 'llm_call',
              positionX: 300,
              positionY: 200,
              config: {"systemPrompt": "你是一个AI助手", "userPrompt": "请回答：${question}", "outputVar": "answer"}
            },
            {
              nodeKey: 'end_node',
              name: '结束节点',
              type: 'end',
              positionX: 500,
              positionY: 200,
              config: {}
            }
          ],
          transitions: [
            {
              fromNodeKey: 'start_node',
              toNodeKey: 'llm_call_1',
              conditionExpression: 'true',
              variableMappings: '{}'
            },
            {
              fromNodeKey: 'llm_call_1',
              toNodeKey: 'end_node',
              conditionExpression: 'true',
              variableMappings: '{}'
            }
          ],
          globalVariables: [
            {
              name: 'question',
              type: 'string',
              initialValue: '请输入你的问题'
            },
            {
              name: 'answer',
              type: 'string',
              initialValue: ''
            }
          ]
        }
      }
    })
  }

// 保存工作流
const saveWorkflow = () => {
  if (!workflow.value.name) {
    ElMessage.warning('请输入工作流名称')
    return
  }
  onSaveWorkflow(workflow.value)
}

// 处理保存工作流事件
const onSaveWorkflow = (workflowData) => {
  isSaving.value = true

  console.log('=== 调试：开始保存工作流 ===')
  console.log('保存的工作流数据:', workflowData)

  // 调用真实API保存工作流
  const savePromise = isEditing.value
    ? updateWorkflow(workflowData.id, workflowData)
    : createWorkflow(workflowData)

  savePromise.then(response => {
    console.log('=== 调试：保存工作流成功 ===')
    console.log('API响应数据:', response)

    ElMessage.success('工作流已保存')

    // 直接使用后端返回的完整工作流对象，确保不为null
    const savedWorkflow = response.data || response
    if (savedWorkflow) {
      workflow.value = savedWorkflow
      isEditing.value = true
      console.log('=== 调试：工作流数据更新完成 ===')
      console.log('更新后的工作流数据:', workflow.value)
      console.log('更新后的definition:', workflow.value.definition)
    } else {
      console.log('=== 调试：工作流数据更新完成 ===')
      console.log('API返回数据为null，保持原有工作流数据')
    }
  }).catch(error => {
    console.error('=== 调试：保存工作流失败 ===')
    console.error('错误信息:', error)

    ElMessage.error('保存工作流失败')
    console.error('保存工作流失败:', error)
  }).finally(() => {
    isSaving.value = false
  })
}

// 运行工作流
const runWorkflowHandler = () => {
  showRunParamsDialog.value = true
  runParams.name = workflow.value.name + '_实例'
  runParams.inputParams = '{}'
}

// 处理运行工作流事件
const onRunWorkflow = (workflowData) => {
  runWorkflowHandler()
}

// 执行工作流
const executeWorkflow = () => {
  isRunning.value = true
  showRunParamsDialog.value = false
  
  // 检查工作流ID是否存在
  if (!workflow.value.id) {
    ElMessage.error('工作流未保存，无法运行')
    isRunning.value = false
    return
  }
  
  // 解析输入参数
  let inputParamsObj = {}
  try {
    inputParamsObj = JSON.parse(runParams.inputParams)
  } catch (e) {
    ElMessage.error('输入参数格式错误，请输入有效的JSON格式')
    isRunning.value = false
    return
  }
  
  // 立即打开对话界面（在API调用之前）
  showChatDialog.value = true
  // 先清空之前的消息
  currentInstanceId.value = null
  
  // 不立即打开执行结果对话框，只在执行完成后显示
  showResultDialog.value = false
  executionStatus.value = 'running'
  executionStatusText.value = '工作流正在启动...'
  currentNodeName.value = ''
  executionLogs.value = []
  executionResult.value = {}
  formattedResult.value = ''
  
  // 调用真实API运行工作流（异步，不阻塞）
  runWorkflow(workflow.value.id, inputParamsObj).then(response => {
    ElMessage.success('工作流已开始运行')
    
    // 获取实例ID
    const instance = response.data || response
    if (instance && instance.id) {
      currentInstanceId.value = instance.id
      
      // 更新执行结果对话框状态
      executionStatus.value = 'running'
      executionStatusText.value = '工作流正在执行中...'
      
      // 不再使用轮询，通过WebSocket接收实时更新
    } else {
      ElMessage.error('无法获取工作流实例ID')
      showChatDialog.value = false
      showResultDialog.value = false
    }
  }).catch(error => {
    ElMessage.error('运行工作流失败')
    console.error('运行工作流失败:', error)
    // 关闭对话框
    showChatDialog.value = false
    showResultDialog.value = false
  }).finally(() => {
    isRunning.value = false
  })
}

// 轮询功能已移除，改为通过WebSocket接收实时更新

// 关闭执行结果对话框
const closeResultDialog = () => {
  showResultDialog.value = false
  currentInstanceId.value = null
}

// 处理对话界面关闭
const handleChatDialogClose = () => {
  showChatDialog.value = false
}

// 查看工作流实例详情
const viewInstanceDetails = () => {
  stopPolling()
  showResultDialog.value = false
  // 跳转到工作流实例详情页
  router.push(`/workflow-instances/${currentInstanceId.value}`)
}

// 初始化
onMounted(() => {
  const workflowId = route.query.id
  if (workflowId) {
    isEditing.value = true
    loadWorkflowDefinition(Number(workflowId))
  } else {
    // 确保workflow.value具有正确的结构，包含definition字段
    workflow.value = {
      id: null,
      name: '',
      description: '',
      version: '1.0.0',
      status: 1,
      definition: {
        nodes: [],
        transitions: [],
        globalVariables: []
      }
    }
  }
})
</script>

<style scoped>
.workflow-designer-view {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.view-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.view-header h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: #333;
}

.header-actions {
  display: flex;
  gap: 12px;
}

.designer-wrapper {
  flex: 1;
  background-color: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  overflow: hidden;
  height: calc(100vh - 150px);
}

/* 执行结果对话框样式 */
.execution-result {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.execution-status {
  margin-bottom: 8px;
}

.execution-progress {
  margin: 16px 0;
}

.progress-text {
  text-align: center;
  margin-top: 8px;
  color: #606266;
  font-size: 14px;
}

.execution-logs h4 {
  margin: 0 0 8px 0;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.logs-scrollbar {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  background-color: #fafafa;
}

.log-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  font-size: 13px;
  border-bottom: 1px solid #f0f0f0;
}

.log-time {
  color: #909399;
  font-size: 12px;
  min-width: 120px;
}

.log-node {
  color: #303133;
  font-weight: 500;
  min-width: 100px;
}

.log-status {
  padding: 2px 6px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 500;
  min-width: 60px;
  text-align: center;
}

.log-status.success {
  background-color: #f0f9eb;
  color: #67c23a;
}

.log-status.failed {
  background-color: #fef0f0;
  color: #f56c6c;
}

.log-status.running {
  background-color: #ecf5ff;
  color: #409eff;
}

.log-message {
  color: #606266;
  flex: 1;
  word-break: break-all;
}

.no-logs {
  text-align: center;
  color: #909399;
  padding: 16px;
}

.execution-result-data h4 {
  margin: 0 0 8px 0;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.result-text {
  margin: 0;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  color: #303133;
  line-height: 1.5;
  white-space: pre-wrap;
  word-wrap: break-word;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .view-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
  }
  
  .header-actions {
    width: 100%;
    justify-content: space-between;
    flex-wrap: wrap;
  }
  
  .designer-wrapper {
    height: calc(100vh - 200px);
  }
  
  .execution-result {
    gap: 12px;
  }
  
  .log-item {
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
    padding: 8px;
  }
  
  .log-time,
  .log-node,
  .log-status {
    min-width: auto;
  }
}
</style>
