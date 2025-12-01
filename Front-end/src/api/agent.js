import axios from 'axios'

// 创建axios实例（导出以便其他模块复用）
export const apiClient = axios.create({
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器 - 添加token
apiClient.interceptors.request.use(
  config => {
    // 从localStorage获取token
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
  response => {
    // 直接返回响应数据
    return response.data
  },
  error => {
    console.error('响应错误:', error)
    // 处理常见错误
    if (error.response) {
      switch (error.response.status) {
        case 401:
          console.error('未授权，请重新登录')
          // 可以在这里触发登出逻辑
          break
        case 403:
          console.error('拒绝访问')
          break
        case 404:
          console.error('请求的资源不存在')
          break
        case 500:
          console.error('服务器错误')
          break
        default:
          console.error(`请求失败: ${error.response.data.message || '未知错误'}`)
      }
    } else if (error.request) {
      console.error('网络错误，请检查网络连接')
    }
    return Promise.reject(error)
  }
)

// Agent相关API
export default {
  /**
   * 获取用户自己的智能体列表
   * @param {Object} params 查询参数，包含分页信息
   * @param {number} params.page 当前页码
   * @param {number} params.pageSize 每页大小
   * @returns {Promise<Object>} 包含智能体列表和总数的响应
   */
  async getAgents(params = {}) {
    try {
      const response = await apiClient.get('/api/agents', { params })
      console.log('获取智能体列表成功:', response)
      return response
    } catch (error) {
      console.error('获取智能体列表失败:', error)
      throw error
    }
  },

  /**
   * 根据ID获取智能体详情
   * @param {string} id 智能体ID
   * @returns {Promise<Object>} 智能体详情
   */
  async getAgentById(id) {
    try {
      const response = await apiClient.get(`/api/agents/${id}`)
      console.log(`获取智能体 ${id} 详情成功:`, response)
      return response
    } catch (error) {
      console.error(`获取智能体 ${id} 详情失败:`, error)
      throw error
    }
  },

  /**
   * 创建新的智能体
   * @param {Object} agentData 智能体数据
   * @returns {Promise<Object>} 创建的智能体
   */
  async createAgent(agentData) {
    try {
      const response = await apiClient.post('/api/agents', agentData)
      console.log('创建智能体成功:', response)
      return response
    } catch (error) {
      console.error('创建智能体失败:', error)
      throw error
    }
  },

  /**
   * 更新智能体信息
   * @param {string} id 智能体ID
   * @param {Object} agentData 更新的智能体数据
   * @returns {Promise<Object>} 更新后的智能体
   */
  async updateAgent(id, agentData) {
    try {
      const response = await apiClient.put(`/api/agents/${id}`, agentData)
      console.log(`更新智能体 ${id} 成功:`, response)
      return response
    } catch (error) {
      console.error(`更新智能体 ${id} 失败:`, error)
      throw error
    }
  },

  /**
   * 软删除智能体
   * @param {string} id 智能体ID
   * @returns {Promise<Object>} 删除结果
   */
  async deleteAgent(id) {
    try {
      const response = await apiClient.delete(`/api/agents/${id}`)
      console.log(`删除智能体 ${id} 成功:`, response)
      return response
    } catch (error) {
      console.error(`删除智能体 ${id} 失败:`, error)
      throw error
    }
  },

  /**
   * 获取指定智能体绑定的知识库
   */
  async getAgentKnowledge(agentId) {
    try {
      const response = await apiClient.get(`/api/agents/${agentId}/knowledge`)
      console.log(`获取智能体 ${agentId} 知识库成功:`, response)
      return response
    } catch (error) {
      console.error(`获取智能体 ${agentId} 知识库失败:`, error)
      throw error
    }
  },

  /**
   * 为智能体绑定知识库
   */
  async addAgentKnowledge(agentId, knowledgeBaseId) {
    try {
      const response = await apiClient.post('/api/agents/knowledge', {
        agentId,
        knowledgeBaseId
      })
      console.log(`为智能体 ${agentId} 绑定知识库 ${knowledgeBaseId} 成功:`, response)
      return response
    } catch (error) {
      console.error(`为智能体 ${agentId} 绑定知识库 ${knowledgeBaseId} 失败:`, error)
      throw error
    }
  }
}