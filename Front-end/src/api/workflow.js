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
  return service.get('/state-workflows', { params })
}

// 获取单个工作流详情
export const getWorkflowById = (id) => {
  return service.get(`/state-workflows/${id}`)
}

// 创建工作流
export const createWorkflow = (workflowData) => {
  // 简化数据处理，直接发送jsonDefinition
  const processedData = {
    name: workflowData.name,
    description: workflowData.description,
    version: workflowData.version,
    status: workflowData.status || 1,
    createdBy: 1, // 暂时使用默认用户ID，后续从userStore获取
    jsonDefinition: JSON.stringify(workflowData.definition)
  }
  return service.post('/state-workflows', processedData)
}

// 更新工作流
export const updateWorkflow = (id, workflowData) => {
  // 简化数据处理，直接发送jsonDefinition
  const processedData = {
    name: workflowData.name,
    description: workflowData.description,
    version: workflowData.version,
    status: workflowData.status || 1,
    jsonDefinition: JSON.stringify(workflowData.definition)
  }
  return service.put(`/state-workflows/${id}`, processedData)
}

// 删除工作流
export const deleteWorkflow = (id) => {
  return service.delete(`/state-workflows/${id}`)
}

// 启动工作流
export const runWorkflow = (workflowId, inputParams) => {
  return service.post(`/state-workflows/${workflowId}/execute`, inputParams)
}

// 获取工作流实例列表
export const getWorkflowInstances = (params) => {
  return service.get('/state-workflows/instances', { params })
}

// 根据工作流ID获取实例
export const getInstancesByWorkflowId = (workflowId) => {
  return service.get(`/state-workflows/${workflowId}/instances`)
}

// 获取单个工作流实例详情
export const getWorkflowInstanceById = (id) => {
  return service.get(`/state-workflows/instances/${id}`)
}

// 获取工作流实例执行日志
export const getWorkflowInstanceLogs = (id) => {
  return service.get(`/state-workflows/instances/${id}/logs`)
}

export default service