<template>
  <div class="workflow-designer">
    <!-- 工具栏 -->
    <div class="designer-toolbar">
      <!-- 工作流名称输入 -->
      <div class="workflow-name-input">
        <el-input 
          v-model="workflowName" 
          placeholder="输入工作流名称" 
          size="small"
          style="width: 200px; margin-right: 12px;"
        ></el-input>
      </div>
      <el-button type="primary" @click="saveWorkflow">保存工作流</el-button>
      <el-button @click="runWorkflow">运行工作流</el-button>
      <div class="toolbar-right">
        <el-button @click="zoomIn" size="small">放大</el-button>
        <el-button @click="zoomOut" size="small">缩小</el-button>
        <el-button @click="resetZoom" size="small">重置缩放</el-button>
      </div>
    </div>
    
    <!-- 设计器主体 -->
    <div class="designer-container">
      <!-- 左侧节点面板 -->
      <div class="node-palette">
        <h3>节点类型</h3>
        <div class="node-list">
          <div 
            v-for="nodeType in nodeTypes" 
            :key="nodeType.type"
            class="node-item"
            draggable="true"
            @dragstart="onDragStart($event, nodeType)"
          >
            <div class="node-icon">{{ nodeType.icon }}</div>
            <div class="node-label">{{ nodeType.label }}</div>
          </div>
        </div>
      </div>
      
      <!-- 中间画布区域 -->
      <div 
        class="canvas-container"
        ref="canvasContainer"
        @dragover="onDragOver"
        @drop="onDrop"
      >
        <div 
          class="canvas"
          :style="{ transform: `scale(${zoomLevel})`, transformOrigin: 'center' }"
          @mousedown="onCanvasMouseDown"
        >
          <!-- 网格背景 -->
          <div class="grid-background"></div>
          
          <!-- 连接线 -->
          <svg class="connections-layer" ref="connectionsLayer">
            <line 
              v-for="edge in edges" 
              :key="edge.id"
              :x1="getNodePosition(edge.fromNodeId).x + 75"
              :y1="getNodePosition(edge.fromNodeId).y + 30"
              :x2="getNodePosition(edge.toNodeId).x"
              :y2="getNodePosition(edge.toNodeId).y + 30"
              class="connection-line"
            />
          </svg>
          
          <!-- 节点 -->
          <div 
            v-for="node in nodes" 
            :key="node.id"
            class="workflow-node"
            :class="node.type"
            :style="{ left: `${node.positionX}px`, top: `${node.positionY}px` }"
            @mousedown="onNodeMouseDown($event, node)"
            @click="selectNode(node)"
          >
            <div class="node-header">
              <div class="node-type-icon">{{ getNodeTypeIcon(node.type) }}</div>
              <div class="node-name">{{ node.name }}</div>
              <div class="node-actions">
                <el-button 
                  type="danger" 
                  size="small" 
                  circle 
                  @click.stop="deleteNode(node)"
                >
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
            </div>
            <div class="node-type">{{ getNodeTypeLabel(node.type) }}</div>
            <div class="node-ports">
              <div 
                class="node-port port-input"
                :data-node-id="node.id"
                @mousedown="onPortMouseDown($event, node, 'input')"
              ></div>
              <div 
                class="node-port port-output"
                :data-node-id="node.id"
                @mousedown="onPortMouseDown($event, node, 'output')"
              ></div>
            </div>
          </div>
          
          <!-- 连接线绘制临时线 -->
          <svg v-if="isConnecting" class="temp-connection-layer">
            <line 
              :x1="connectStart.x + 75"
              :y1="connectStart.y + 30"
              :x2="connectEnd.x"
              :y2="connectEnd.y"
              class="temp-connection-line"
            />
          </svg>
        </div>
      </div>
      
      <!-- 右侧属性面板 -->
      <div class="properties-panel">
        <!-- 工作流属性编辑（当没有选中节点时显示） -->
        <div v-if="!selectedNode" class="workflow-properties">
          <h3>工作流属性</h3>
          <el-form label-position="top" size="small">
            <el-form-item label="工作流名称">
              <el-input v-model="workflowName" @input="updateWorkflowName"></el-input>
            </el-form-item>
            <el-form-item label="工作流描述">
              <el-input 
                v-model="workflowDescription" 
                type="textarea" 
                :rows="3"
                @input="updateWorkflowDescription"
              ></el-input>
            </el-form-item>
            <el-form-item label="工作流版本">
              <el-input v-model="workflowVersion" @input="updateWorkflowVersion"></el-input>
            </el-form-item>
          </el-form>
        </div>
        <!-- 节点属性编辑（当选中节点时显示） -->
        <div v-else class="properties-content">
          <h3>节点属性</h3>
          <el-form label-position="top" size="small">
            <el-form-item label="节点名称">
              <el-input v-model="selectedNode.name" @input="updateNode"></el-input>
            </el-form-item>
            <el-form-item label="节点类型">
              <el-input v-model="selectedNode.type" disabled></el-input>
            </el-form-item>
            
            <!-- 大模型调用节点配置 -->
            <template v-if="selectedNode.type === 'llm_call'">
              <el-form-item label="系统提示词">
                <el-input 
                  v-model="selectedNodeConfig.systemPrompt" 
                  type="textarea" 
                  :rows="4" 
                  @input="updateNodeConfig"
                ></el-input>
              </el-form-item>
              <el-form-item label="用户提示词模板">
                <el-input 
                  v-model="selectedNodeConfig.userPrompt" 
                  type="textarea" 
                  :rows="4" 
                  @input="updateNodeConfig"
                  placeholder="使用${变量名}来引用上下文变量"
                ></el-input>
              </el-form-item>
              <el-form-item label="输出变量名">
                <el-input v-model="selectedNodeConfig.outputVar" @input="updateNodeConfig"></el-input>
              </el-form-item>
            </template>
          </el-form>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Delete } from '@element-plus/icons-vue'

// Props
const props = defineProps({
  workflow: {
    type: Object,
    default: () => ({})
  }
})

// Emits
const emit = defineEmits(['save', 'run'])

// 节点类型定义
const nodeTypes = [
  { type: 'start', label: '开始节点', icon: '▶️' },
  { type: 'end', label: '结束节点', icon: '⏹️' },
  { type: 'llm_call', label: '大模型调用', icon: '🤖' }
]

// 状态管理
const nodes = ref([])
const edges = ref([])
const selectedNode = ref(null)
const zoomLevel = ref(1)
const isConnecting = ref(false)
const connectStart = ref({ x: 0, y: 0 })
const connectEnd = ref({ x: 0, y: 0 })
let currentConnection = null
const canvasContainer = ref(null)
const connectionsLayer = ref(null)
const selectedNodeConfig = ref({})

// 工作流基本信息状态
const workflowName = ref(props.workflow.name || '')
const workflowDescription = ref(props.workflow.description || '')
const workflowVersion = ref(props.workflow.version || '1.0.0')

// 计算属性
const getNodeTypeLabel = (type) => {
  const nodeType = nodeTypes.find(nt => nt.type === type)
  return nodeType ? nodeType.label : type
}

const getNodeTypeIcon = (type) => {
  const nodeType = nodeTypes.find(nt => nt.type === type)
  return nodeType ? nodeType.icon : '📦'
}

// 方法
const getNodePosition = (nodeId) => {
  const node = nodes.value.find(n => n.id === nodeId)
  return node ? { x: node.positionX, y: node.positionY } : { x: 0, y: 0 }
}

// 拖拽事件处理
const onDragStart = (event, nodeType) => {
  event.dataTransfer.setData('application/json', JSON.stringify(nodeType))
}

const onDragOver = (event) => {
  event.preventDefault()
}

const onDrop = (event) => {
  event.preventDefault()
  const nodeTypeData = event.dataTransfer.getData('application/json')
  if (nodeTypeData) {
    const nodeType = JSON.parse(nodeTypeData)
    const canvasRect = canvasContainer.value.getBoundingClientRect()
    const x = (event.clientX - canvasRect.left) / zoomLevel.value - 75
    const y = (event.clientY - canvasRect.top) / zoomLevel.value - 30
    
    // 创建新节点 - 使用较小的临时ID，后端会重新生成正式ID
    const newNode = {
      id: -Date.now() % 1000000, // 使用负数的临时ID，避免与后端生成的正整数ID冲突
      name: `${nodeType.label} ${nodes.value.length + 1}`,
      type: nodeType.type,
      positionX: x,
      positionY: y,
      configJson: JSON.stringify({})
    }
    
    nodes.value.push(newNode)
  }
}

// 节点选择
const selectNode = (node) => {
  selectedNode.value = node
  selectedNodeConfig.value = node.configJson ? JSON.parse(node.configJson) : {}
}

// 节点更新
const updateNode = () => {
  // 节点基本信息已通过v-model双向绑定
}

const updateNodeConfig = () => {
  if (selectedNode.value) {
    selectedNode.value.configJson = JSON.stringify(selectedNodeConfig.value)
  }
}

// 工作流基本信息更新方法
const updateWorkflowName = () => {
  // 工作流名称已通过v-model双向绑定
}

const updateWorkflowDescription = () => {
  // 工作流描述已通过v-model双向绑定
}

const updateWorkflowVersion = () => {
  // 工作流版本已通过v-model双向绑定
}

// 节点删除
const deleteNode = (node) => {
  // 删除节点
  nodes.value = nodes.value.filter(n => n.id !== node.id)
  // 删除相关边
  edges.value = edges.value.filter(e => e.fromNodeId !== node.id && e.toNodeId !== node.id)
  // 取消选择
  if (selectedNode.value?.id === node.id) {
    selectedNode.value = null
    selectedNodeConfig.value = {}
  }
  ElMessage.success('节点已删除')
}

// 连接线绘制
const onPortMouseDown = (event, node, portType) => {
  event.stopPropagation()
  if (portType === 'input') return // 只允许从输出端口开始连接
  
  isConnecting.value = true
  currentConnection = { from: node.id }
  const rect = event.target.getBoundingClientRect()
  connectStart.value = {
    x: node.positionX,
    y: node.positionY
  }
  connectEnd.value = {
    x: node.positionX + 150,
    y: node.positionY + 30
  }
  
  // 添加鼠标移动和释放事件监听
  document.addEventListener('mousemove', onMouseMove)
  document.addEventListener('mouseup', onMouseUp)
}

const onMouseMove = (event) => {
  if (!isConnecting.value) return
  
  const canvasRect = canvasContainer.value.getBoundingClientRect()
  connectEnd.value = {
    x: (event.clientX - canvasRect.left) / zoomLevel.value,
    y: (event.clientY - canvasRect.top) / zoomLevel.value
  }
}

const onMouseUp = (event) => {
  if (!isConnecting.value) return
  
  // 检查是否连接到了另一个节点的输入端口
  const target = event.target.closest('.port-input')
  if (target) {
    const toNodeId = parseInt(target.dataset.nodeId)
    if (currentConnection.from !== toNodeId) {
      // 创建新连接 - 使用较小的临时ID，后端会重新生成正式ID
      const newEdge = {
        id: -Date.now() % 1000000, // 使用负数的临时ID，避免与后端生成的正整数ID冲突
        fromNodeId: currentConnection.from,
        toNodeId: toNodeId
      }
      edges.value.push(newEdge)
      console.log('=== 调试：边创建成功 ===')
      console.log('新创建的边:', newEdge)
      console.log('当前边数量:', edges.value.length)
      console.log('所有边数据:', edges.value)
      ElMessage.success('连接已创建')
    } else {
      ElMessage.warning('不能连接到自身')
    }
  }
  
  // 清理
  isConnecting.value = false
  currentConnection = null
  document.removeEventListener('mousemove', onMouseMove)
  document.removeEventListener('mouseup', onMouseUp)
}

// 画布拖拽
let isDraggingCanvas = false
let dragStart = { x: 0, y: 0 }
let canvasOffset = { x: 0, y: 0 }

const onCanvasMouseDown = (event) => {
  if (event.target === canvasContainer.value || event.target === event.currentTarget) {
    isDraggingCanvas = true
    dragStart = { x: event.clientX, y: event.clientY }
  }
}

const onNodeMouseDown = (event, node) => {
  event.stopPropagation()
  let isDragging = false
  let startX = event.clientX
  let startY = event.clientY
  let startNodeX = node.positionX
  let startNodeY = node.positionY
  
  const onMouseMove = (e) => {
    if (!isDragging) {
      // 检查是否超过最小拖拽距离
      const dx = e.clientX - startX
      const dy = e.clientY - startY
      if (Math.sqrt(dx * dx + dy * dy) > 5) {
        isDragging = true
      }
    }
    
    if (isDragging) {
      const dx = (e.clientX - startX) / zoomLevel.value
      const dy = (e.clientY - startY) / zoomLevel.value
      
      // 通过nodes.value数组修改节点位置，确保响应式更新
  const nodeIndex = nodes.value.findIndex(n => n.id === node.id)
  if (nodeIndex !== -1) {
    nodes.value[nodeIndex].positionX = startNodeX + dx
    nodes.value[nodeIndex].positionY = startNodeY + dy
    
    // 调试：记录节点移动
    console.log('=== 调试：节点移动 ===')
    console.log('移动的节点ID:', node.id)
    console.log('新位置:', { x: nodes.value[nodeIndex].positionX, y: nodes.value[nodeIndex].positionY })
    console.log('当前边数量:', edges.value.length)
    console.log('边数据:', edges.value)
  }
    }
  }
  
  const onMouseUp = () => {
    document.removeEventListener('mousemove', onMouseMove)
    document.removeEventListener('mouseup', onMouseUp)
  }
  
  document.addEventListener('mousemove', onMouseMove)
  document.addEventListener('mouseup', onMouseUp)
}

// 缩放控制
const zoomIn = () => {
  zoomLevel.value = Math.min(zoomLevel.value + 0.1, 2)
}

const zoomOut = () => {
  zoomLevel.value = Math.max(zoomLevel.value - 0.1, 0.5)
}

const resetZoom = () => {
  zoomLevel.value = 1
}

// 保存工作流
const saveWorkflow = () => {
  // 验证工作流
  const startNodes = nodes.value.filter(n => n.type === 'start')
  const endNodes = nodes.value.filter(n => n.type === 'end')
  
  // 新增：验证工作流名称
  if (!workflowName.value.trim()) {
    ElMessage.warning('请输入工作流名称')
    return
  }
  
  if (startNodes.length === 0) {
    ElMessage.warning('工作流必须包含一个开始节点')
    return
  }
  
  if (endNodes.length === 0) {
    ElMessage.warning('工作流必须包含一个结束节点')
    return
  }
  
  // 重新编排节点和边的ID，确保连续且从1开始自增
  const newNodes = [...nodes.value]
  const newEdges = [...edges.value]
  
  // 创建节点ID映射（旧ID -> 新ID）
  const nodeIdMap = new Map()
  newNodes.forEach((node, index) => {
    const newId = index + 1
    nodeIdMap.set(node.id, newId)
    node.id = newId
  })
  
  // 更新边的节点ID并重新编排边ID
  newEdges.forEach((edge, index) => {
    edge.id = index + 1
    edge.fromNodeId = nodeIdMap.get(edge.fromNodeId)
    edge.toNodeId = nodeIdMap.get(edge.toNodeId)
  })
  
  // 构建工作流数据 - 直接构建definition字段
  const workflowData = {
    id: props.workflow?.id,
    name: workflowName.value,
    description: workflowDescription.value,
    version: workflowVersion.value,
    // 确保status不为null，默认为1
    status: props.workflow?.status !== undefined && props.workflow?.status !== null ? props.workflow.status : 1,
    createdBy: props.workflow?.createdBy,
    createdAt: props.workflow?.createdAt,
    updatedAt: new Date().toISOString(),
    // 确保isDeleted不为null，默认为0
    isDeleted: props.workflow?.isDeleted !== undefined && props.workflow?.isDeleted !== null ? props.workflow.isDeleted : 0,
    // 直接构建definition字段，包含重新编排ID后的nodes和edges
    definition: {
      nodes: newNodes,
      edges: newEdges
    }
  }
  
  console.log('=== 调试：保存工作流数据 ===')
  console.log('保存的边数量:', workflowData.definition.edges.length)
  console.log('保存的边数据:', workflowData.definition.edges)
  console.log('完整工作流数据:', workflowData)
  
  emit('save', workflowData)
  ElMessage.success('工作流已保存')
}

// 运行工作流
const runWorkflow = () => {
  emit('run', {
    nodes: nodes.value,
    edges: edges.value
  })
  ElMessage.success('工作流已开始运行')
}

// 初始化工作流
onMounted(() => {
  console.log('=== 调试：WorkflowDesigner组件初始化 ===')
  console.log('接收到的props.workflow:', props.workflow)
  
  // 初始化工作流基本信息
  if (props.workflow) {
    workflowName.value = props.workflow.name || ''
    workflowDescription.value = props.workflow.description || ''
    workflowVersion.value = props.workflow.version || '1.0.0'
    
    // 从definition字段获取节点和边数据
    if (props.workflow.definition && props.workflow.definition.nodes && props.workflow.definition.edges) {
      console.log('=== 调试：从props.workflow.definition中获取到节点和边数据 ===')
      console.log('definition中的节点数量:', props.workflow.definition.nodes.length)
      console.log('definition中的边数量:', props.workflow.definition.edges.length)
      console.log('definition中的边数据:', props.workflow.definition.edges)
      
      // 使用深拷贝避免引用关系
      nodes.value = JSON.parse(JSON.stringify(props.workflow.definition.nodes))
      edges.value = JSON.parse(JSON.stringify(props.workflow.definition.edges))
      
      console.log('=== 调试：深拷贝完成 ===')
      console.log('深拷贝后的节点数量:', nodes.value.length)
      console.log('深拷贝后的边数量:', edges.value.length)
      console.log('深拷贝后的边数据:', edges.value)
    } else if (props.workflow.nodes && props.workflow.edges) {
      // 兼容旧格式，从直接属性获取（用于向后兼容）
      console.log('=== 调试：从props.workflow直接属性中获取到节点和边数据 ===')
      console.log('props中的节点数量:', props.workflow.nodes.length)
      console.log('props中的边数量:', props.workflow.edges.length)
      console.log('props中的边数据:', props.workflow.edges)
      
      // 使用深拷贝避免引用关系
      nodes.value = JSON.parse(JSON.stringify(props.workflow.nodes))
      edges.value = JSON.parse(JSON.stringify(props.workflow.edges))
      
      console.log('=== 调试：深拷贝完成 ===')
      console.log('深拷贝后的节点数量:', nodes.value.length)
      console.log('深拷贝后的边数量:', edges.value.length)
      console.log('深拷贝后的边数据:', edges.value)
    }
  }
  
  console.log('=== 调试：WorkflowDesigner组件初始化完成 ===')
})

// 监听工作流变化 - 当工作流对象变化时更新，包括保存后的数据更新（深度监听）
watch(() => props.workflow, (newWorkflow) => {
  console.log('=== 调试：工作流对象变化监听触发 ===')
  console.log('新的工作流数据:', newWorkflow)
  
  // 更新工作流基本信息
  workflowName.value = newWorkflow?.name || ''
  workflowDescription.value = newWorkflow?.description || ''
  workflowVersion.value = newWorkflow?.version || '1.0.0'
  
  // 从definition字段获取节点和边数据
  if (newWorkflow) {
    if (newWorkflow.definition && newWorkflow.definition.nodes && newWorkflow.definition.edges) {
      console.log('=== 调试：从变化后的props.workflow.definition中获取节点和边数据 ===')
      console.log('变化后的节点数量:', newWorkflow.definition.nodes.length)
      console.log('变化后的边数量:', newWorkflow.definition.edges.length)
      console.log('变化后的边数据:', newWorkflow.definition.edges)
      
      // 深拷贝节点数据
      nodes.value = JSON.parse(JSON.stringify(newWorkflow.definition.nodes))
      edges.value = JSON.parse(JSON.stringify(newWorkflow.definition.edges))
      
      console.log('=== 调试：工作流对象变化更新完成 ===')
      console.log('更新后的节点数量:', nodes.value.length)
      console.log('更新后的边数量:', edges.value.length)
      console.log('更新后的边数据:', edges.value)
    } else if (newWorkflow.nodes && newWorkflow.edges) {
      // 兼容旧格式，从直接属性获取（用于向后兼容）
      console.log('=== 调试：从变化后的props.workflow直接属性中获取节点和边数据 ===')
      console.log('变化后的节点数量:', newWorkflow.nodes.length)
      console.log('变化后的边数量:', newWorkflow.edges.length)
      console.log('变化后的边数据:', newWorkflow.edges)
      
      // 深拷贝节点数据
      nodes.value = JSON.parse(JSON.stringify(newWorkflow.nodes))
      edges.value = JSON.parse(JSON.stringify(newWorkflow.edges))
      
      console.log('=== 调试：工作流对象变化更新完成 ===')
      console.log('更新后的节点数量:', nodes.value.length)
      console.log('更新后的边数量:', edges.value.length)
      console.log('更新后的边数据:', edges.value)
    }
  } else {
    // 如果新工作流为null，清空节点和边
    console.log('=== 调试：新工作流为null，清空节点和边 ===')
    nodes.value = []
    edges.value = []
    console.log('=== 调试：工作流对象变化更新完成 ===')
    console.log('更新后的节点数量:', nodes.value.length)
    console.log('更新后的边数量:', edges.value.length)
    console.log('更新后的边数据:', edges.value)
  }
}, { deep: true })
</script>

<style scoped>
.workflow-designer {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background-color: #f5f7fa;
}

/* 工具栏 */
.designer-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 20px;
  background-color: #fff;
  border-bottom: 1px solid #e0e0e0;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

.toolbar-right {
  display: flex;
  gap: 8px;
}

/* 设计器主体 */
.designer-container {
  display: flex;
  flex: 1;
  overflow: hidden;
}

/* 节点面板 */
.node-palette {
  width: 200px;
  background-color: #fff;
  border-right: 1px solid #e0e0e0;
  padding: 16px;
  overflow-y: auto;
}

.node-palette h3 {
  margin: 0 0 16px 0;
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.node-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.node-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background-color: #f0f2f5;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  cursor: grab;
  transition: all 0.3s ease;
}

.node-item:hover {
  background-color: #e6f7ff;
  border-color: #91d5ff;
  transform: translateY(-2px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.node-item:active {
  cursor: grabbing;
}

.node-icon {
  font-size: 20px;
}

.node-label {
  font-size: 14px;
  font-weight: 500;
}

/* 画布容器 */
.canvas-container {
  flex: 1;
  background-color: #fafafa;
  overflow: auto;
  position: relative;
}

.canvas {
  position: relative;
  width: 2000px;
  height: 1500px;
  transform-origin: top left;
  transition: transform 0.2s ease;
}

.grid-background {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-image: 
    linear-gradient(rgba(0, 0, 0, 0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(0, 0, 0, 0.05) 1px, transparent 1px);
  background-size: 20px 20px;
}

/* 节点样式 */
.workflow-node {
  position: absolute;
  width: 150px;
  background-color: #fff;
  border: 2px solid #dcdfe6;
  border-radius: 8px;
  padding: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  cursor: move;
  transition: all 0.3s ease;
  z-index: 10;
}

.workflow-node:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
}

.workflow-node.selected {
  border-color: #409eff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2);
}

.workflow-node.start {
  border-color: #67c23a;
}

.workflow-node.end {
  border-color: #f56c6c;
}

.workflow-node.llm_call {
  border-color: #909399;
}

.node-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.node-type-icon {
  font-size: 18px;
  margin-right: 8px;
}

.node-name {
  flex: 1;
  font-size: 14px;
  font-weight: 600;
  color: #333;
  word-break: break-all;
}

.node-actions {
  display: flex;
  gap: 4px;
}

.node-type {
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
}

.node-ports {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}

.node-port {
  width: 10px;
  height: 10px;
  background-color: #409eff;
  border-radius: 50%;
  cursor: pointer;
  transition: all 0.3s ease;
}

.node-port:hover {
  transform: scale(1.5);
  box-shadow: 0 0 8px rgba(64, 158, 255, 0.5);
}

.port-input {
  cursor: pointer;
  opacity: 1;
}

/* 连接线 */
.connections-layer {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 5;
}

.connection-line {
  stroke: #409eff;
  stroke-width: 2;
  fill: none;
  marker-end: url(#arrowhead);
}

.temp-connection-layer {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 6;
}

.temp-connection-line {
  stroke: #67c23a;
  stroke-width: 2;
  fill: none;
  stroke-dasharray: 5, 5;
}

/* 属性面板 */
.properties-panel {
  width: 300px;
  background-color: #fff;
  border-left: 1px solid #e0e0e0;
  overflow-y: auto;
  padding: 16px;
}

.properties-content h3 {
  margin: 0 0 16px 0;
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.properties-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #909399;
  font-size: 14px;
}

/* 工作流属性样式 */
.workflow-properties h3 {
  margin: 0 0 16px 0;
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

/* 工具栏工作流名称输入样式 */
.workflow-name-input {
  display: inline-block;
  vertical-align: middle;
}

/* 响应式设计 */
@media (max-width: 1200px) {
  .properties-panel {
    width: 250px;
  }
  
  .node-palette {
    width: 180px;
  }
}

@media (max-width: 992px) {
  .designer-container {
    flex-direction: column;
  }
  
  .node-palette {
    width: 100%;
    height: 150px;
    border-right: none;
    border-bottom: 1px solid #e0e0e0;
  }
  
  .node-list {
    flex-direction: row;
    overflow-x: auto;
  }
  
  .properties-panel {
    width: 100%;
    height: 250px;
    border-left: none;
    border-top: 1px solid #e0e0e0;
  }
}
</style>
