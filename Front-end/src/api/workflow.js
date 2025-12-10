// 工作流相关API请求
import axios from 'axios'

// 创建axios实例
const service = axios.create({
  baseURL: '/api', // 基础URL
  timeout: 10000 // 请求超时时间
})

// 请求拦截器
service.interceptors.request.use(
  config => {
    // 从localStorage获取token
    const token = localStorage.getItem('token')
    if (token) {
      // 将token添加到请求头
      config.headers.Authorization = `Bearer ${token}`
    }
    
    // 调试：打印请求数据（特别是工作流相关请求）
    if (config.url.includes('/workflows')) {
      console.log(`=== 调试：API请求 ${config.method.toUpperCase()} ${config.url} ===`)
      if (config.data) {
        console.log('请求体:', config.data)
        if (config.data.edges) {
          console.log('请求中的边数量:', config.data.edges.length)
          console.log('请求中的边数据:', config.data.edges)
        }
      }
    }
    
    return config
  },
  error => {
    // 处理请求错误
    console.error('请求错误:', error)
    return Promise.reject(error)
  }
)

// 响应拦截器
service.interceptors.response.use(
  response => {
    // 注意：根据前端处理http响应的规则，获取的response已经是data
    return response.data
  },
  error => {
    // 处理响应错误
    console.error('响应错误:', error)
    return Promise.reject(error)
  }
)

// API请求函数

// 获取工作流列表
export const getWorkflows = (params) => {
  return service.get('/workflows', { params })
}

// 获取单个工作流详情
export const getWorkflowById = (id) => {
  return service.get(`/workflows/${id}`)
}

// 获取工作流完整定义（包含节点和边）
export const getWorkflowDefinition = (id) => {
  return service.get(`/workflows/${id}/definition`)
}

// 创建工作流
export const createWorkflow = (workflowData) => {
  // 转换definition为JSON字符串
  const processedData = { ...workflowData }
  if (processedData.definition) {
    processedData.definition = JSON.stringify(processedData.definition)
  }
  return service.post('/workflows', processedData)
}

// 更新工作流
export const updateWorkflow = (id, workflowData) => {
  // 转换definition为JSON字符串
  const processedData = { ...workflowData }
  if (processedData.definition) {
    processedData.definition = JSON.stringify(processedData.definition)
  }
  return service.put(`/workflows/${id}`, processedData)
}

// 删除工作流
export const deleteWorkflow = (id) => {
  return service.delete(`/workflows/${id}`)
}

// 启动工作流
export const runWorkflow = (workflowId, inputParams) => {
  return service.post(`/workflow-instances/start/${workflowId}`, inputParams)
}

// 获取工作流实例列表
export const getWorkflowInstances = (params) => {
  return service.get('/workflow-instances', { params })
}

// 根据工作流ID获取实例
export const getInstancesByWorkflowId = (workflowId) => {
  return service.get(`/workflow-instances/workflow/${workflowId}`)
}

// 获取单个工作流实例详情
export const getWorkflowInstanceById = (id) => {
  return service.get(`/workflow-instances/${id}`)
}

// 获取工作流实例执行日志
export const getWorkflowInstanceLogs = (id) => {
  return service.get(`/workflow-instances/${id}/logs`)
}

export default service
