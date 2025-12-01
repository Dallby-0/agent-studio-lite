import axios from 'axios'

// 创建axios实例
const apiClient = axios.create({
  timeout: 60000,  // 聊天可能需要更长时间
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器 - 添加token
apiClient.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => {
    console.error('请求错误:', error)
    return Promise.reject(error)
  }
)

// 响应拦截器
apiClient.interceptors.response.use(
  response => response.data,
  error => {
    console.error('响应错误:', error)
    return Promise.reject(error)
  }
)

export default {
  /**
   * 向Agent发送消息
   * @param {number} agentId Agent ID
   * @param {string} message 用户消息
   * @param {Array} messages 可选的历史消息
   * @returns {Promise} AI响应
   */
  async sendMessage(agentId, message, messages = null) {
    try {
      const data = messages ? { messages } : { message }
      const response = await apiClient.post(`/api/agent-chat/${agentId}`, data)
      console.log('Agent聊天响应:', response)
      return response
    } catch (error) {
      console.error('Agent聊天失败:', error)
      throw error
    }
  },

  /**
   * 获取Agent信息
   * @param {number} agentId Agent ID
   * @returns {Promise} Agent信息
   */
  async getAgentInfo(agentId) {
    try {
      const response = await apiClient.get(`/api/agent-chat/${agentId}/info`)
      return response
    } catch (error) {
      console.error('获取Agent信息失败:', error)
      throw error
    }
  }
}

