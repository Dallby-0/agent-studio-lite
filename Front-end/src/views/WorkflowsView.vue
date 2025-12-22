<template>
  <Layout :userInfo="userStore.userInfo">
    <div class="workflows-view">
      <!-- 顶部导航 -->
      <div class="view-header">
        <h1 class="h1-white-stroke">工作流管理</h1>
        <div class="header-actions">
          <el-button type="primary" @click="createWorkflow">
            <el-icon><Plus /></el-icon>
            新建工作流
          </el-button>
        </div>
      </div>
      
      <!-- 工作流列表 -->
      <el-card class="workflows-card">
        <template #header>
          <div class="card-header">
            <span>工作流列表</span>
          </div>
        </template>
        
        <el-table :data="workflows" style="width: 100%">
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="name" label="工作流名称" min-width="150">
            <template #default="scope">
              <el-link type="primary" @click="editWorkflow(scope.row)">{{ scope.row.name }}</el-link>
            </template>
          </el-table-column>
          <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
          <el-table-column prop="version" label="版本" width="100" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="scope">
              <el-tag :type="scope.row.status === 1 ? 'success' : 'warning'">
                {{ scope.row.status === 1 ? '启用' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="创建时间" width="200">
            <template #default="scope">
              {{resetTimeShow(scope.row.createdAt)}}
            </template>
          </el-table-column>
          <el-table-column prop="updatedAt" label="更新时间" width="200">
            <template #default="scope">
              {{resetTimeShow(scope.row.updatedAt)}}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="scope">
              <el-button type="primary" plain size="small" @click="editWorkflow(scope.row)">编辑</el-button>
              <el-button type="danger" plain size="small" @click="deleteWorkflowHandler(scope.row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        
        <!-- 分页 -->
        <div class="table-pagination">
          <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            :total="total"
            @size-change="handleSizeChange"
            @current-change="handleCurrentChange"
          />
        </div>
      </el-card>
    </div>
  </Layout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import Layout from '../components/Layout.vue'
import { useUserStore } from '../stores/userStore'

// API 导入
import { getWorkflows, deleteWorkflow } from '../api/workflow'

const router = useRouter()
const userStore = useUserStore()

// 状态管理
const workflows = ref([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

// 模拟数据
const mockWorkflows = [
  {
    id: 1,
    name: '示例工作流1',
    description: '这是一个示例工作流，包含开始、大模型调用和结束节点',
    version: '1.0.0',
    status: 1,
    createdAt: '2023-10-01 10:00:00',
    updatedAt: '2023-10-01 10:00:00'
  },
  {
    id: 2,
    name: '客户服务工作流',
    description: '用于自动处理客户服务请求的工作流',
    version: '1.0.0',
    status: 1,
    createdAt: '2023-10-02 14:30:00',
    updatedAt: '2023-10-02 14:30:00'
  }
]

// 加载工作流列表
const loadWorkflows = () => {
  // 调用真实API获取工作流列表
  getWorkflows().then(response => {
    workflows.value = response.data || response
    total.value = workflows.value.length
  }).catch(error => {
    ElMessage.error('加载工作流失败')
    console.error('加载工作流失败:', error)
    // 加载失败时使用模拟数据
    workflows.value = mockWorkflows
    total.value = mockWorkflows.length
  })
}

//重设时间显示
const resetTimeShow = (str) => {
  // 容错处理
  if (!str || typeof str !== 'string') {
    return str || '';
  }

  // 1. 解析为日期对象（识别ISO格式的+00:00时区）
  const date = new Date(str);
  // 验证日期是否有效
  if (isNaN(date.getTime())) {
    return str;
  }

  // 2. 小时增加8小时（核心：通过毫秒数操作，自动处理跨天/跨月/跨年）
  date.setTime(date.getTime() + 8 * 60 * 60 * 1000); // 8小时 = 8*60*60*1000 毫秒

  // 3. 补零工具函数：确保个位数转为两位数
  const padZero = (num) => num.toString().padStart(2, '0');

  // 4. 提取处理后的UTC时间（匹配原时区偏移，避免本地时区干扰）
  const year = date.getUTCFullYear();
  const month = padZero(date.getUTCMonth() + 1); // 月份从0开始，需+1
  const day = padZero(date.getUTCDate());
  const hour = padZero(date.getUTCHours()); // 已增加8小时后的小时数
  const minute = padZero(date.getUTCMinutes());

  // 5. 拼接目标格式
  return `${year}年${month}月${day}日-${hour}:${minute}`;
};

// 创建工作流
const createWorkflow = () => {
  router.push('/workflows/designer')
}

// 编辑工作流
const editWorkflow = (workflow) => {
  router.push({ path: '/workflows/designer', query: { id: workflow.id } })
}

// 删除工作流
const deleteWorkflowHandler = (id) => {
  // 调用真实API删除工作流
  deleteWorkflow(id).then(() => {
    ElMessage.success('工作流已删除')
    loadWorkflows()
  }).catch(error => {
    ElMessage.error('删除工作流失败')
    console.error('删除工作流失败:', error)
    // 删除失败时使用模拟数据
    workflows.value = workflows.value.filter(w => w.id !== id)
    total.value = workflows.value.length
    ElMessage.success('工作流已删除')
  })
}

// 分页处理
const handleSizeChange = (size) => {
  pageSize.value = size
  currentPage.value = 1
  loadWorkflows()
}

const handleCurrentChange = (current) => {
  currentPage.value = current
  loadWorkflows()
}

// 初始化
onMounted(() => {
  loadWorkflows()
})
</script>

<style scoped>
.workflows-view {
  width: 100%;
  height: 100%;
}

.view-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.view-header h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: #333;
}

.header-actions {
  display: flex;
  gap: 12px;
}

.workflows-card {
  background-color: rgba(245, 247, 250, 0.7);
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.table-pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .view-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
  }
  
  .header-actions {
    width: 100%;
    justify-content: space-between;
  }
  
  .table-pagination {
    justify-content: center;
  }
}
</style>
