<template>
  <el-dialog
    v-model="visible"
    title="工作流对话"
    width="800px"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :z-index="3000"
    @close="handleClose"
  >
    <div class="workflow-chat-dialog">
      <!-- 消息列表 -->
      <div class="messages-container" ref="messagesContainer">
        <div v-if="messages.length === 0" class="empty-chat">
          <el-icon size="48"><ChatDotRound /></el-icon>
          <p>工作流执行中，等待智能体响应...</p>
        </div>
        <div
          v-for="(msg, index) in messages"
          :key="index"
          :class="['message', msg.role]"
        >
          <div class="message-avatar">
            {{ msg.role === 'user' ? '我' : (msg.nickname || '智能体')?.charAt(0) }}
          </div>
          <div class="message-content">
            <div class="message-header" v-if="msg.role === 'assistant' && msg.nickname">
              <span class="message-nickname">{{ msg.nickname }}</span>
            </div>
            <div class="message-text">{{ msg.content }}</div>
            <div class="message-time">{{ formatTime(msg.timestamp) }}</div>
          </div>
        </div>
        <div v-if="loading" class="message assistant">
          <div class="message-avatar">智</div>
          <div class="message-content">
            <div class="message-text typing">正在思考中...</div>
          </div>
        </div>
      </div>
      
      <!-- 输入区域 -->
      <div class="input-area">
        <el-input
          v-model="inputMessage"
          type="textarea"
          :rows="2"
          :placeholder="waitingUserInput ? '请输入您的消息...' : '工作流执行中，等待进入用户输入节点...'"
          @keydown.enter.exact.prevent="sendMessage"
          :disabled="!waitingUserInput || !isWebSocketConnected"
        />
        <el-button
          type="primary"
          @click="sendMessage"
          :disabled="!canSendMessage"
        >
          发送
        </el-button>
      </div>
    </div>
    
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="clearMessages">清空对话</el-button>
        <el-button type="primary" @click="handleClose">关闭</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch, nextTick, onUnmounted, computed } from 'vue'
import { ChatDotRound } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  instanceId: {
    type: Number,
    default: null
  }
})

const emit = defineEmits(['update:modelValue', 'close'])

const visible = ref(props.modelValue)
const messages = ref([])
const loading = ref(false)
const messagesContainer = ref(null)
const inputMessage = ref('')
const isConnected = ref(false)
const waitingUserInput = ref(false) // 是否等待用户输入
let websocket = null

// 计算属性：检查WebSocket是否已连接
const isWebSocketConnected = computed(() => {
  return isConnected.value && websocket && websocket.readyState === WebSocket.OPEN
})

// 计算属性：是否可以发送消息（只有在等待用户输入时才能发送）
const canSendMessage = computed(() => {
  const result = isWebSocketConnected.value && waitingUserInput.value && inputMessage.value.trim().length > 0
  console.log('canSendMessage计算:', {
    isWebSocketConnected: isWebSocketConnected.value,
    waitingUserInput: waitingUserInput.value,
    inputMessageLength: inputMessage.value.trim().length,
    result: result
  })
  return result
})

// 监听modelValue变化
watch(() => props.modelValue, (newVal) => {
  visible.value = newVal
  if (newVal) {
    // 如果已有instanceId，立即连接；否则等待instanceId变化时连接
    if (props.instanceId) {
      setTimeout(() => {
        connectWebSocket()
      }, 100)
    }
  } else {
    disconnectWebSocket()
  }
})

// 监听instanceId变化
watch(() => props.instanceId, (newId, oldId) => {
  console.log('instanceId变化:', { oldId, newId, visible: visible.value })
  if (visible.value && newId && newId !== oldId) {
    console.log('断开旧连接并重新连接')
    disconnectWebSocket()
    messages.value = []
    // 延迟一下再连接，确保对话框已完全打开
    setTimeout(() => {
      connectWebSocket()
    }, 100)
  } else if (visible.value && newId && !oldId) {
    // 首次设置instanceId，立即连接
    console.log('首次设置instanceId，立即连接')
    setTimeout(() => {
      connectWebSocket()
    }, 100)
  }
})

// 连接WebSocket
const connectWebSocket = () => {
  if (!props.instanceId) {
    console.log('等待工作流实例ID...')
    return
  }
  
  // 如果已经连接，先断开
  if (websocket && websocket.readyState === WebSocket.OPEN) {
    console.log('WebSocket已连接，跳过重复连接')
    return
  }
  
  try {
    // 使用当前页面的协议和主机名，而不是硬编码的后端服务地址
    // 这样WebSocket连接会通过前端的nginx代理，与API请求保持一致
    const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const wsHost = window.location.host
    
    // 从localStorage获取token，添加到URL查询参数中
    const token = localStorage.getItem('token')
    let wsUrl = `${wsProtocol}//${wsHost}/api/workflow-instances/${props.instanceId}/chat`
    if (token) {
      wsUrl += `?token=${encodeURIComponent(token)}`
    }
    
    websocket = new WebSocket(wsUrl)
    
    websocket.onopen = () => {
      console.log('WebSocket连接已建立，实例ID:', props.instanceId, 'URL:', wsUrl)
      isConnected.value = true
    }
    
    websocket.onmessage = (event) => {
      try {
        console.log('收到WebSocket消息:', event.data)
        const data = JSON.parse(event.data)
        console.log('解析后的消息数据:', data)
        console.log('消息类型:', data.type)
        if (data.type === 'status') {
          console.log('状态消息详情:', {
            status: data.status,
            waitingUserInput: data.waitingUserInput,
            loading: data.loading
          })
        }
        handleMessage(data)
      } catch (e) {
        console.error('解析WebSocket消息失败:', e, '原始数据:', event.data)
      }
    }
    
    websocket.onerror = (error) => {
      console.error('WebSocket错误:', error)
      isConnected.value = false
    }
    
    websocket.onclose = (event) => {
      console.log('WebSocket连接已关闭', {
        code: event.code,
        reason: event.reason,
        wasClean: event.wasClean
      })
      isConnected.value = false
      waitingUserInput.value = false // 连接关闭时重置等待状态
    }
  } catch (e) {
    console.error('建立WebSocket连接失败:', e)
  }
}

// 断开WebSocket
const disconnectWebSocket = () => {
  if (websocket) {
    websocket.close()
    websocket = null
    isConnected.value = false
    waitingUserInput.value = false // 重置等待状态
  }
}

// 处理接收到的消息
const handleMessage = (data) => {
  console.log('处理消息:', data)
  console.log('当前waitingUserInput状态:', waitingUserInput.value)
  
  if (data.type === 'message') {
    const newMessage = {
      role: data.role || 'assistant',
      content: data.content || '',
      nickname: data.nickname || null,
      timestamp: new Date(data.timestamp || Date.now())
    }
    console.log('添加消息到列表:', newMessage)
    messages.value.push(newMessage)
    scrollToBottom()
    
    // 如果消息来自"系统"且内容是提示消息，自动启用输入
    if (data.nickname === '系统' && data.content && data.content.includes('请输入')) {
      console.log('检测到系统提示消息，自动启用用户输入')
      waitingUserInput.value = true
      console.log('waitingUserInput已设置为:', waitingUserInput.value)
    }
  } else if (data.type === 'status') {
    // 处理状态消息
    console.log('处理状态消息，data.waitingUserInput:', data.waitingUserInput)
    if (data.waitingUserInput !== undefined) {
      waitingUserInput.value = data.waitingUserInput
      console.log('等待用户输入状态已更新为:', waitingUserInput.value)
    }
    if (data.loading !== undefined) {
      loading.value = data.loading
    }
    console.log('收到状态消息:', data.status, 'waitingUserInput:', data.waitingUserInput)
    console.log('更新后的waitingUserInput状态:', waitingUserInput.value)
  } else {
    console.warn('未知的消息类型:', data.type)
  }
  
  console.log('处理消息后，waitingUserInput状态:', waitingUserInput.value)
  console.log('canSendMessage计算结果:', canSendMessage.value)
}

// 清空消息
const clearMessages = () => {
  messages.value = []
}

// 关闭对话框
const handleClose = () => {
  visible.value = false
  emit('update:modelValue', false)
  emit('close')
  disconnectWebSocket()
}

// 滚动到底部
const scrollToBottom = () => {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}

// 格式化时间
const formatTime = (date) => {
  if (!date) return ''
  const d = new Date(date)
  return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

// 发送用户输入消息
const sendMessage = () => {
  if (!canSendMessage.value) {
    return
  }
  
  const userMessage = inputMessage.value.trim()
  inputMessage.value = ''
  
  // 立即禁用输入，防止重复发送
  waitingUserInput.value = false
  
  // 添加用户消息到界面
  messages.value.push({
    role: 'user',
    content: userMessage,
    timestamp: new Date()
  })
  scrollToBottom()
  
  // 通过WebSocket发送用户输入
  try {
    const messageData = {
      type: 'user_input',
      content: userMessage
    }
    websocket.send(JSON.stringify(messageData))
    console.log('已发送用户输入:', userMessage)
    console.log('已禁用输入，等待工作流继续执行')
  } catch (e) {
    console.error('发送用户输入失败:', e)
    // 如果发送失败，恢复等待状态
    waitingUserInput.value = true
  }
}

// 组件卸载时断开连接
onUnmounted(() => {
  disconnectWebSocket()
})
</script>

<style scoped>
.workflow-chat-dialog {
  height: 500px;
  display: flex;
  flex-direction: column;
}

/* 消息区域 */
.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  background-color: #f9fafb;
}

.empty-chat {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #909399;
}

.empty-chat p {
  margin-top: 16px;
  font-size: 14px;
}

.message {
  display: flex;
  margin-bottom: 24px;
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.message.user {
  flex-direction: row-reverse;
}

.message .message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: linear-gradient(135deg, #409eff, #67c23a);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: bold;
  flex-shrink: 0;
  margin-right: 12px;
}

.message.user .message-avatar {
  background: linear-gradient(135deg, #67c23a, #409eff);
  margin-left: 12px;
  margin-right: 0;
}

.message-content {
  max-width: 70%;
}

.message-header {
  margin-bottom: 4px;
}

.message-nickname {
  font-size: 12px;
  color: #909399;
  font-weight: 500;
}

.message-text {
  padding: 12px 16px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
  white-space: pre-wrap;
}

.message.user .message-text {
  background-color: #409eff;
  color: #fff;
  border-bottom-right-radius: 4px;
}

.message.assistant .message-text {
  background-color: #fff;
  color: #303133;
  border: 1px solid #e4e7ed;
  border-bottom-left-radius: 4px;
}

.message-time {
  font-size: 11px;
  color: #909399;
  margin-top: 4px;
  text-align: right;
}

.message.user .message-time {
  text-align: left;
}

.typing {
  color: #909399 !important;
}

/* 输入区域 */
.input-area {
  padding: 16px;
  border-top: 1px solid #e4e7ed;
  display: flex;
  gap: 12px;
  align-items: flex-end;
  background-color: #fff;
}

.input-area .el-textarea {
  flex: 1;
}

.input-area .el-button {
  flex-shrink: 0;
}
</style>

