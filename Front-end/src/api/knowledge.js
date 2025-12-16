import axios from 'axios'
import { apiClient as baseClient } from './agent'

// 复用 agent.js 中的 apiClient 配置和拦截器
// 这里重新创建一个实例，主要用于文件上传等特殊场景
const apiClient = baseClient

export default {
  /**
   * 获取知识库列表
   */
  async listKnowledgeBases() {
    try {
      const response = await apiClient.get('/api/knowledge/bases')
      console.log('获取知识库列表成功:', response)
      return response
    } catch (error) {
      console.error('获取知识库列表失败:', error)
      throw error
    }
  },

  /**
   * 向量检索测试
   */
  async vectorSearch(query, knowledgeBaseId, topK = 5) {
    try {
      const response = await apiClient.get('/api/knowledge/vector-search', {
        params: {
          query,
          knowledgeBaseId,
          topK
        }
      })
      return response
    } catch (error) {
      console.error('向量检索失败:', error)
      throw error
    }
  },

  /**
   * 上传知识文件并（可选）绑定到指定 Agent
   * @param {Object} options
   * @param {File} options.file 知识文件
   * @param {number} [options.agentId] 可选，智能体ID
   * @param {string} [options.name] 可选，知识库名称
   * @param {string} [options.description] 可选，知识库描述
   */
  async uploadKnowledge({ file, agentId, name, description }) {
    const formData = new FormData()
    formData.append('file', file)
    if (agentId) formData.append('agentId', agentId)
    if (name) formData.append('name', name)
    if (description) formData.append('description', description)

    try {
      const response = await axios.post('/api/knowledge/upload', formData, {
        headers: {
          // 让浏览器自动设置 multipart 边界，只显式指定类型
          'Content-Type': 'multipart/form-data',
          Authorization: `Bearer ${localStorage.getItem('token') || ''}`
        },
        timeout: 600000 // 上传和向量化可能较慢，适当放宽超时
      })
      console.log('上传知识库成功:', response.data)
      return response.data
    } catch (error) {
      console.error('上传知识库失败:', error)
      throw error
    }
  },

  /**
   * 删除知识库
   * @param {number} knowledgeBaseId
   */
  async deleteKnowledgeBase(knowledgeBaseId) {
    try {
      return await apiClient.delete(`/api/knowledge/bases/${knowledgeBaseId}`)
    } catch (error) {
      console.error('删除知识库失败:', error)
      throw error
    }
  }
}

