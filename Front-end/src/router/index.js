import { createRouter, createWebHistory } from 'vue-router'

// 导入视图组件
const LoginView = () => import('../views/LoginView.vue')
const HomeView = () => import('../views/HomeView.vue')
const ForbiddenView = () => import('../views/ForbiddenView.vue')
const TestRoleView = () => import('../views/TestRoleView.vue')

// 路由配置
const routes = [
  {
    path: '/',
    name: 'login',
    component: LoginView,
    meta: {
      title: '登录 - Agent Studio',
      requiresAuth: false
    }
  },
  {
    path: '/home',
    name: 'home',
    component: HomeView,
    meta: {
      title: '仪表盘 - Agent Studio',
      requiresAuth: true
    }
  },
  // 可以添加更多路由
  {
    path: '/chat',
    name: 'chat',
    component: () => import('../views/ChatView.vue'),
    meta: {
      title: '对话中心 - Agent Studio',
      requiresAuth: true
    }
  },
  {
    path: '/tasks',
    name: 'tasks',
    component: () => import('../views/TasksView.vue'),
    meta: {
      title: '任务管理 - Agent Studio',
      requiresAuth: true
    }
  },
  {
    path: '/agents',
    name: 'agents',
    component: () => import('../views/AgentsView.vue'),
    meta: {
      title: 'Agent配置 - Agent Studio',
      requiresAuth: true
    }
  },
  {
    path: '/knowledge',
    name: 'knowledge',
    component: () => import('../views/KnowledgeView.vue'),
    meta: {
      title: '资源库 - Agent Studio',
      requiresAuth: true
    }
  },
  {
    path: '/settings',
    name: 'settings',
    component: () => import('../views/SettingsView.vue'),
    meta: {
      title: '系统设置 - Agent Studio',
      requiresAuth: true
    }
  },
  {
    path: '/admin',
    name: 'user-management',
    component: () => import('../views/UserManagementView.vue'),
    meta: {
      title: '用户管理 - Agent Studio',
      requiresAuth: true,
      requiresAdmin: true // 标记需要管理员权限
    }
  },
  {
    path: '/403',
    name: 'forbidden',
    component: ForbiddenView,
    meta: {
      title: '权限不足 - Agent Studio',
      requiresAuth: false
    }
  },
  // 404页面
  {
    path: '/test-role',
    name: 'test-role',
    component: TestRoleView,
    meta: {
      title: '角色测试工具 - Agent Studio',
      requiresAuth: false
    }
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('../views/NotFoundView.vue'),
    meta: {
      title: '页面未找到 - Agent Studio',
      requiresAuth: false
    }
  }
]

// 创建路由实例
const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    // 保持滚动位置或回到顶部
    if (savedPosition) {
      return savedPosition
    } else {
      return { top: 0 }
    }
  }
})

// 路由守卫 - 处理页面标题和权限验证
router.beforeEach((to, from, next) => {
  // 设置页面标题
  document.title = to.meta.title || 'Agent Studio'
  
  // 从localStorage获取token
  const token = localStorage.getItem('token')
  
  // 如果是需要认证的页面但用户未登录，重定向到登录页
  if (to.meta.requiresAuth && !token) {
    next({ name: 'login' })
  } else if (to.meta.requiresAdmin) {
    // 检查用户是否有管理员权限
    // 从localStorage获取用户角色信息，实际应用中应该从token解析或从store获取
    const userRole = localStorage.getItem('userRole') || 'user'; // 默认普通用户
    
    // 如果需要管理员权限但用户不是管理员，重定向到403页面
    if (userRole !== 'admin') {
      next({ name: 'forbidden' })
    } else {
      // 管理员用户可以访问
      next()
    }
  } else {
    // 普通页面直接访问
    next()
  }
})

export default router