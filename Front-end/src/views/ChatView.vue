<template>
  <Layout :userInfo="userStore.userInfo">
    <div class="chat-page">
      <!-- 左侧Agent选择 -->
      <div class="agent-sidebar">
        <div class="sidebar-header">
          <h3>选择智能体</h3>
        </div>
        <div class="agent-list">
          <div
            v-for="agent in agents"
            :key="agent.id"
            :class="['agent-item', { active: selectedAgent?.id === agent.id }]"
            @click="selectAgent(agent)"
          >
            <div class="agent-avatar">{{ agent.name?.charAt(0) || 'A' }}</div>
            <div class="agent-info">
              <div class="agent-name">{{ agent.name }}</div>
              <div class="agent-type">{{ agent.type || '通用' }}</div>
            </div>
          </div>
          <div v-if="agents.length === 0" class="no-agents">
            暂无可用的智能体
          </div>
        </div>
      </div>

      <!-- 右侧聊天区域 -->
      <div class="chat-main">
        <template v-if="selectedAgent">
          <!-- 聊天头部 -->
          <div class="chat-header">
            <div class="current-agent">
              <div class="agent-avatar">{{ selectedAgent.name?.charAt(0) || 'A' }}</div>
              <div>
                <div class="agent-name">{{ selectedAgent.name }}</div>
                <div class="agent-desc">{{ selectedAgent.description || '智能助手' }}</div>
              </div>
            </div>
            <el-button size="small" @click="clearMessages">清空对话</el-button>
          </div>

          <!-- 消息列表 -->
          <div class="messages-container" ref="messagesContainer">
            <div v-if="messages.length === 0" class="empty-chat">
              <el-icon size="48"><ChatDotRound /></el-icon>
              <p>开始与 {{ selectedAgent.name }} 对话吧！</p>
            </div>
            <div
              v-for="(msg, index) in messages"
              :key="index"
              :class="['message', msg.role]"
            >
              <div class="message-avatar">
                {{ msg.role === 'user' ? '我' : selectedAgent.name?.charAt(0) }}
              </div>
              <div class="message-content">
                <div class="message-text">{{ msg.content }}</div>
                <div class="message-time">{{ formatTime(msg.timestamp) }}</div>
              </div>
            </div>
            <div v-if="loading" class="message assistant">
              <div class="message-avatar">{{ selectedAgent.name?.charAt(0) }}</div>
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
              placeholder="输入消息，按Enter发送..."
              @keydown.enter.exact.prevent="sendMessage"
              :disabled="loading"
            />
            <el-button
              type="primary"
              @click="sendMessage"
              :loading="loading"
              :disabled="!inputMessage.trim()"
            >
              发送
            </el-button>
          </div>
        </template>

        <!-- 未选择Agent时的提示 -->
        <div v-else class="no-agent-selected">
          <el-icon size="64"><ChatDotRound /></el-icon>
          <h2>请选择一个智能体开始对话</h2>
          <p>从左侧列表选择一个智能体，即可开始聊天</p>
        </div>
      </div>
    </div>
  </Layout>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { ChatDotRound } from '@element-plus/icons-vue'
import Layout from '../components/Layout.vue'
import { useUserStore } from '../stores/userStore'
import agentService from '../api/agent'
import agentChatService from '../api/agentChat'

const userStore = useUserStore()

// 数据
const agents = ref([])
const selectedAgent = ref(null)
const messages = ref([])
const inputMessage = ref('')
const loading = ref(false)
const messagesContainer = ref(null)

// 本地存储相关（按智能体分别保存聊天历史）
const STORAGE_PREFIX = 'agent_chat_history_'

const getStorageKey = (agentId) => {
  if (!agentId) return null
  return `${STORAGE_PREFIX}${agentId}`
}

const loadMessagesFromStorage = (agentId) => {
  const key = getStorageKey(agentId)
  if (!key) return
  try {
    const raw = localStorage.getItem(key)
    if (raw) {
      const parsed = JSON.parse(raw)
      if (Array.isArray(parsed)) {
        messages.value = parsed
        // 恢复后滚动到底部
        nextTick(() => {
          if (messagesContainer.value) {
            messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
          }
        })
        return
      }
    }
    // 没有历史或解析失败时，重置为空
    messages.value = []
  } catch (e) {
    console.error('加载聊天历史失败:', e)
    messages.value = []
  }
}

const saveMessagesToStorage = () => {
  if (!selectedAgent.value) return
  const key = getStorageKey(selectedAgent.value.id)
  if (!key) return
  try {
    localStorage.setItem(key, JSON.stringify(messages.value))
  } catch (e) {
    console.error('保存聊天历史失败:', e)
  }
}

// 获取智能体列表
const fetchAgents = async () => {
  try {
    const response = await agentService.getAgents()
    agents.value = response.data || response || []
  } catch (error) {
    console.error('获取智能体列表失败:', error)
    ElMessage.error('获取智能体列表失败')
  }
}

// 选择智能体
const selectAgent = (agent) => {
  if (selectedAgent.value?.id !== agent.id) {
    selectedAgent.value = agent
    loadMessagesFromStorage(agent.id)
    console.log('选择智能体:', agent.name)
  }
}

// 发送消息
const sendMessage = async () => {
  if (!inputMessage.value.trim() || loading.value || !selectedAgent.value) return

  const userMessage = inputMessage.value.trim()
  inputMessage.value = ''

  // 添加用户消息
  messages.value.push({
    role: 'user',
    content: userMessage,
    timestamp: new Date()
  })
  saveMessagesToStorage()

  scrollToBottom()
  loading.value = true

  try {
    // 构建历史消息
    const historyMessages = messages.value.map(m => ({
      role: m.role,
      content: m.content
    }))

    // 调用API
    const response = await agentChatService.sendMessage(
      selectedAgent.value.id,
      null,
      historyMessages
    )

    // 添加AI响应
    if (response.data?.response) {
      messages.value.push({
        role: 'assistant',
        content: response.data.response,
        timestamp: new Date()
      })
      saveMessagesToStorage()
    }
  } catch (error) {
    console.error('发送消息失败:', error)
    ElMessage.error('发送消息失败，请稍后重试')
    // 添加错误消息
    messages.value.push({
      role: 'assistant',
      content: '抱歉，发生了错误，请稍后重试。',
      timestamp: new Date()
    })
    saveMessagesToStorage()
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

// 清空消息
const clearMessages = () => {
  messages.value = []
  if (selectedAgent.value) {
    const key = getStorageKey(selectedAgent.value.id)
    if (key) {
      try {
        localStorage.removeItem(key)
      } catch (e) {
        console.error('清理聊天历史失败:', e)
      }
    }
  }
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

onMounted(async () => {
  if (!userStore.initialized) {
    await userStore.initializeUserInfo()
  }
  await fetchAgents()
})
</script>


<style scoped>
.chat-page {
  display: flex;
  border-radius: 20px;
  height: calc(85vh - 20px);
  background-color: rgba(245, 247, 250, 0.5);
}

/* 左侧侧边栏 */
.agent-sidebar {
  border-radius: 20px;
  width: 280px;
  background: rgba(255, 255, 255, 0.5);
  border-right: 3px solid rgba(228, 231, 237, 0.25);
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid rgba(228, 231, 237, 0.32);
}

.sidebar-header h3 {
  margin: 0;
  font-size: 16px;
  color: #303133;
}

.agent-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.agent-item {
  display: flex;
  align-items: center;
  padding: 12px;
  border-radius: 40px;
  cursor: pointer;
  transition: all 0.3s;
  margin-bottom: 4px;
}

.agent-item:hover {
  background-color: rgba(245, 247, 250, 0.2);
  border: 1px solid rgba(145, 145, 145, 0.4);
  scale: 105%;
}

.agent-item.active {
  background-color: rgba(236, 245, 255, 0.2);
  border: 1px solid rgba(145, 145, 145, 0.4);
  scale: 102%;
}

.agent-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: linear-gradient(135deg, #409eff, #67c23a);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  font-size: 16px;
  margin-right: 12px;
  flex-shrink: 0;
}

.agent-info {
  flex: 1;
  overflow: hidden;
}

.agent-name {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.agent-type {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.no-agents {
  text-align: center;
  color: #909399;
  padding: 24px;
}

/* 右侧聊天区域 */
.chat-main {
  border-radius: 16px;
  flex: 1;
  display: flex;
  flex-direction: column;
  background: rgba(255, 255, 255, 0.25);
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  border-bottom: 1px solid #e4e7ed;
}

.current-agent {
  display: flex;
  align-items: center;
}

.current-agent .agent-avatar {
  margin-right: 12px;
}

.current-agent .agent-name {
  font-size: 16px;
  font-weight: 600;
}

.current-agent .agent-desc {
  font-size: 12px;
  color: #909399;
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
  font-size: 14px;
}

.message.user .message-avatar {
  background: linear-gradient(135deg, #67c23a, #409eff);
  margin-left: 12px;
  margin-right: 0;
}

.message-content {
  max-width: 70%;
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
  display: flex;
  gap: 12px;
  padding: 16px 24px;
  border-top: 1px solid #e4e7ed;
  border-bottom-right-radius: 16px;
  background: rgba(255, 255, 255, 0.8);
}

.input-area .el-textarea {
  flex: 1;
}

.input-area .el-button {
  align-self: flex-end;
  height: 52px;
  width: 80px;
}

/* 未选择Agent提示 */
.no-agent-selected {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #909399;
}

.no-agent-selected h2 {
  margin-top: 24px;
  font-size: 20px;
  color: #606266;
}

.no-agent-selected p {
  margin-top: 8px;
  font-size: 14px;
}
</style>