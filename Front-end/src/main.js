import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './style/ios-style.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import axios from 'axios'
import App from './App.vue'
import router from './router/index.js'

// 创建应用实例
const app = createApp(App)

// 全局配置axios
axios.defaults.baseURL = ''
axios.defaults.withCredentials = true // 允许携带cookie
axios.defaults.timeout = 10000 // 请求超时时间

// 请求拦截器
axios.interceptors.request.use(
  config => {
    // 可以在这里添加token等认证信息
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器
axios.interceptors.response.use(
  response => {
    // 保留完整响应对象，不直接返回data
    return response
  },
  error => {
    // 处理跨域等错误
    // 静默处理错误，不显示在控制台
    return Promise.reject(error)
  }
)

app.config.globalProperties.$axios = axios

// 注册Element Plus图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

// 使用插件
app.use(router)
app.use(createPinia())
app.use(ElementPlus)

// 挂载应用
app.mount('#app')