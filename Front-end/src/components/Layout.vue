<template>
  <div class="app-layout">
    <!-- 侧边栏 -->
    <aside class="app-sidebar" :class="{ 'collapsed': isSidebarCollapsed }">
      <slot name="sidebar">
        <Sidebar 
          :menuItems="menuItems" 
          :collapsed="isSidebarCollapsed"
          @toggle="toggleSidebar"
        />
      </slot>
    </aside>
    
    <!-- 主内容区 -->
    <main class="app-main" :class="{ 'sidebar-collapsed': isSidebarCollapsed }">
      <!-- 顶部导航 -->
      <header v-if="hasHeader" class="app-header">
        <slot name="header">
          <!-- 默认的头部组件 -->
          <AppHeader 
            :userInfo="currentUserInfo"
            @toggle-sidebar="toggleSidebar"
            @logout="handleLogout"
          />
        </slot>
      </header>
      
      <!-- 页面内容 -->
      <div class="app-content">
        <slot></slot>
      </div>
      
      <!-- 页脚 -->
      <footer v-if="hasFooter" class="app-footer">
        <slot name="footer"></slot>
      </footer>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import Sidebar from './Sidebar.vue'
import AppHeader from './AppHeader.vue'
import { useUserStore } from '../stores/userStore'
// Element Plus icons are globally registered in main.js

// 用户状态管理
const userStore = useUserStore()
const router = useRouter()

// 组件属性
const props = defineProps({
  // 是否显示头部
  hasHeader: {
    type: Boolean,
    default: true
  },
  // 是否显示底部
  hasFooter: {
    type: Boolean,
    default: false
  },
  // 初始折叠状态
  collapsed: {
    type: Boolean,
    default: false
  },
  // 侧边栏宽度（展开状态）
  sidebarWidth: {
    type: String,
    default: '250px'
  },
  // 侧边栏宽度（折叠状态）
  sidebarCollapsedWidth: {
    type: String,
    default: '64px'
  },
  // 用户信息（优先级高于store中的信息）
  userInfo: {
    type: Object,
    default: null
  }
})

// 侧边栏折叠状态
const isSidebarCollapsed = ref(props.collapsed)

// 计算当前用户信息
const currentUserInfo = computed(() => {
  return props.userInfo || {
    name: userStore.displayName,
    role: userStore.role,
    avatar: userStore.userInfo.avatar || ''
  }
})

// 基础菜单数据
const baseMenuItems = [
  {
    label: '首页',
    icon: 'House',
    path: '/home'
  },
  {
    label: '对话中心',
    icon: 'ChatDotRound',
    path: '/chat'
  },
  {
    label: '任务管理',
    icon: 'List',
    path: '/tasks'
  },
  {
    label: 'Agent配置',
    icon: 'Key',
    path: '/agents'
  },
  {
    label: '资源库',
    icon: 'Document',
    path: '/knowledge'
  },
  {
    label: '系统设置',
    icon: 'Tools',
    path: '/settings'
  }
]

// 管理员菜单数据
const adminMenuItems = [
  {
    label: '用户管理',
    icon: 'User',
    path: '/admin',
    requiresAdmin: true
  }
]

// 动态计算导航菜单 - 基于用户权限
const menuItems = computed(() => {
  // 先添加基础菜单项
  const items = [...baseMenuItems]
  
  // 检查用户是否为管理员，如果是则添加管理员菜单项
  // 同时直接检查localStorage中的角色，确保即使store未完全更新也能正确显示
  const isAdmin = userStore.isAdmin || localStorage.getItem('userRole') === 'admin';
  if (isAdmin) {
    items.push(...adminMenuItems)
  }
  
  return items
})

// 监听用户角色变化，确保侧边栏菜单能正确更新
watch(() => [userStore.role, localStorage.getItem('userRole')], () => {
  // 强制重新计算菜单，确保角色变化时立即更新
  // 由于使用了computed，Vue会自动处理依赖更新，这里添加日志以确认
  console.log('用户角色可能已变更，菜单将自动更新:', userStore.role);
})

// 事件
const emit = defineEmits(['toggle-sidebar', 'update:collapsed', 'logout'])

// 切换侧边栏状态
const toggleSidebar = () => {
  isSidebarCollapsed.value = !isSidebarCollapsed.value
  emit('toggle-sidebar', isSidebarCollapsed.value)
  emit('update:collapsed', isSidebarCollapsed.value)
}

// 处理退出登录
const handleLogout = async () => {
  try {
    // 清除用户信息和状态
    userStore.clearUserInfo()
    
    // 彻底清除localStorage中的所有认证和用户相关信息
    localStorage.removeItem('token')
    localStorage.removeItem('userRole')
    localStorage.removeItem('userInfo')
    localStorage.removeItem('username')
    
    // 清除可能存在的其他用户相关信息
    Object.keys(localStorage).forEach(key => {
      if (key.toLowerCase().includes('user') || key.toLowerCase().includes('auth') || key.toLowerCase().includes('token')) {
        localStorage.removeItem(key)
      }
    })
    
    // 显示成功消息
    ElMessage.success('退出登录成功')
    
    // 确保重定向到登录页而不是首页
    router.push('/')
    
    // 发出退出登录事件，让父组件知道
    emit('logout')
  } catch (error) {
    console.error('退出登录失败:', error)
    ElMessage.error('退出登录失败，请重试')
  }
}



// 暴露方法给父组件
const expose = {
  toggleSidebar,
  isSidebarCollapsed
}

defineExpose(expose)
</script>

<style scoped>
.app-layout {
  display: flex;
  min-height: 100vh;
  background-color: #f5f7fa;
}

/* 侧边栏样式 */
.app-sidebar {
  background-color: #ffffff;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.1);
  transition: width 0.3s ease, transform 0.3s ease;
  position: relative;
  overflow: hidden;
  z-index: 100;
  /* 使用CSS变量以便响应式调整 */
  width: var(--sidebar-width, 250px);
  display: flex;
  flex-direction: column;
}

.app-sidebar.collapsed {
  width: var(--sidebar-collapsed-width, 64px);
}

/* 主内容区 */
.app-main {
  flex: 1;
  transition: margin-left 0.3s ease;
  min-width: 0;
}

.app-main.sidebar-collapsed {
  margin-left: var(--sidebar-collapsed-width, 64px);
}

/* 顶部导航 */
.app-header {
  background-color: #ffffff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  position: sticky;
  top: 0;
  z-index: 90;
}

/* 页面内容 */
.app-content {
  padding: 24px;
  min-height: calc(100vh - var(--header-height, 64px) - var(--footer-height, 0px));
}

/* 页脚 */
.app-footer {
  background-color: #ffffff;
  border-top: 1px solid #f0f0f0;
  padding: 16px 24px;
  text-align: center;
  color: #909399;
  font-size: 12px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .app-sidebar {
    position: fixed;
    left: 0;
    top: 0;
    height: 100%;
    transform: translateX(-100%);
    z-index: 1000;
  }
  
  .app-sidebar.collapsed {
    transform: translateX(0);
    box-shadow: 2px 0 12px rgba(0, 0, 0, 0.15);
  }
  
  .app-main.sidebar-collapsed {
    margin-left: 0;
  }
  
  .app-content {
    padding: 16px;
  }
}



/* 暗色主题支持 */
:global(.dark-theme) .app-layout {
  background-color: #1a1a1a;
}

:global(.dark-theme) .app-sidebar,
:global(.dark-theme) .app-header,
:global(.dark-theme) .app-footer {
  background-color: #2a2a2a;
  border-color: #3a3a3a;
}

:global(.dark-theme) .app-content {
  background-color: #1a1a1a;
}

:global(.dark-theme) .app-sidebar {
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.3);
}

:global(.dark-theme) .app-header {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
}

:global(.dark-theme) .app-footer {
  color: #cccccc;
}
</style>