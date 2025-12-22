<template>
  <Layout :userInfo="userStore.userInfo">
    <div class="agents-container">
      <div class="page-header">
        <h1 class="h1-white-stroke">我的智能体</h1>
        <el-button type="primary" @click="handleCreateAgent" :icon="Plus">创建智能体</el-button>
      </div>

      <!-- 智能体列表 -->
      <el-card class="agents-card">
        <div v-if="loading" class="loading-container">
          <el-skeleton :rows="5" animated />
        </div>
        <el-table
          v-else
          :data="agents"
          style="width: 100%"
          empty-text="暂无智能体数据"
        >
          <el-table-column prop="id" label="ID" width="180" />
          <el-table-column prop="name" label="名称" min-width="150">
            <template #default="scope">
              <div class="agent-name">
                <div class="name-text">{{ scope.row.name }}</div>
                <div class="agent-desc">{{ scope.row.description || '无描述' }}</div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="type" label="类型" width="100">
            <template #default="scope">
              <el-tag
                :type="getAgentTypeTag(scope.row.type)"
                effect="light"
              >
                {{ scope.row.type || '默认' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="创建时间" width="180">
            <template #default="scope">
              {{ formatDate(scope.row.createdAt) }}
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100">
            <template #default="scope">
              <el-tag
                :type="scope.row.status === 1 ? 'success' : 'danger'"
                effect="light"
              >
                {{ scope.row.status === 1 ? '启用' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="scope">
              <el-button
                type="primary"
                size="small"
                @click="handleEditAgent(scope.row)"
                :icon="Edit"
              >
                编辑
              </el-button>
              <el-button
                type="danger"
                size="small"
                @click="handleDeleteAgent(scope.row.id, scope.row.name)"
                :icon="Delete"
              >
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 分页 -->
        <div v-if="!loading && agents.length > 0" class="pagination-container">
          <el-pagination
            v-model:current-page="pagination.currentPage"
            v-model:page-size="pagination.pageSize"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            :total="pagination.total"
            @size-change="handleSizeChange"
            @current-change="handleCurrentChange"
          />
        </div>
      </el-card>

      <!-- 创建/编辑智能体弹窗 -->
      <el-dialog
        v-model="dialogVisible"
        :title="dialogType === 'create' ? '创建智能体' : '编辑智能体'"
        width="500px"
      >
        <el-form
          ref="agentFormRef"
          :model="agentForm"
          :rules="rules"
          label-width="100px"
        >
          <el-form-item label="名称" prop="name">
            <el-input
              v-model="agentForm.name"
              placeholder="请输入智能体名称"
              maxlength="50"
            />
          </el-form-item>
          <el-form-item label="描述" prop="description">
            <el-input
              v-model="agentForm.description"
              type="textarea"
              placeholder="请输入智能体描述"
              rows="3"
              maxlength="200"
            />
          </el-form-item>
          <el-form-item label="类型" prop="type">
            <el-select
              v-model="agentForm.type"
              placeholder="请选择智能体类型"
            >
              <el-option label="通用助手" value="general" />
              <el-option label="知识库助手" value="knowledge" />
              <el-option label="任务助手" value="task" />
              <el-option label="创意助手" value="creative" />
            </el-select>
          </el-form-item>
          <el-form-item label="API配置" prop="apiConfig">
            <div style="width: 100%;">
              <div style="margin-bottom: 8px; display: flex; align-items: center; gap: 8px; flex-wrap: wrap;">
                <el-upload
                  :show-file-list="false"
                  :before-upload="handleApiConfigUpload"
                  accept=".json,.txt"
                >
                  <el-button size="small" type="primary" :icon="Upload">导入配置文件</el-button>
                </el-upload>
                <el-button size="small" @click="openSaveApiTemplateDialog">保存为模板</el-button>
                <el-select
                  v-model="selectedApiTemplateName"
                  placeholder="选择模板应用"
                  clearable
                  style="width: 200px;"
                  @change="applyApiTemplate"
                >
                  <el-option
                    v-for="item in apiConfigTemplates"
                    :key="item.name"
                    :label="item.name"
                    :value="item.name"
                  />
                </el-select>
              </div>
              <span style="margin-left: 0; color: #909399; font-size: 12px; display: block; margin-bottom: 4px;">
                支持直接填写API Key，或JSON格式: {"apiKey": "sk-xxx", "model": "deepseek-chat"}
              </span>
              <el-input
                v-model="agentForm.apiConfig"
                type="textarea"
                placeholder="请输入API配置（直接填写API Key或JSON格式）"
                rows="3"
              />
            </div>
          </el-form-item>
          <el-form-item label="系统提示词" prop="systemPrompt">
            <el-input
              v-model="agentForm.systemPrompt"
              type="textarea"
              placeholder="请输入系统提示词"
              rows="4"
            />
          </el-form-item>
          <el-form-item label="插件配置" prop="pluginsJson">
            <div style="width: 100%;">
              <div style="margin-bottom: 8px; display: flex; align-items: center; gap: 8px; flex-wrap: wrap;">
                <el-upload
                  :show-file-list="false"
                  :before-upload="handlePluginsJsonUpload"
                  accept=".json"
                >
                  <el-button size="small" type="primary" :icon="Upload">导入JSON文件</el-button>
                </el-upload>
                <el-button size="small" @click="openSavePluginTemplateDialog">保存为模板</el-button>
                <el-select
                  v-model="selectedPluginTemplateName"
                  placeholder="选择模板应用"
                  clearable
                  style="width: 200px;"
                  @change="applyPluginTemplate"
                >
                  <el-option
                    v-for="item in pluginConfigTemplates"
                    :key="item.name"
                    :label="item.name"
                    :value="item.name"
                  />
                </el-select>
              </div>
              <el-input
                v-model="agentForm.pluginsJson"
                type="textarea"
                placeholder="请输入JSON格式的插件配置，或点击上方按钮导入JSON文件"
                rows="6"
              />
            </div>
          </el-form-item>
          <el-form-item label="状态" prop="status">
            <el-switch
              v-model="agentForm.status"
              :active-value="1"
              :inactive-value="0"
              active-text="启用"
              inactive-text="禁用"
            />
          </el-form-item>
          <el-form-item label="配置信息" prop="configJson">
            <el-input
              v-model="agentForm.configJson"
              type="textarea"
              placeholder="请输入JSON格式的配置信息"
              rows="4"
              maxlength="1000"
            />
          </el-form-item>
          <el-form-item label="知识库绑定">
            <div style="width: 100%;">
              <div style="margin-bottom: 8px; display: flex; align-items: center; gap: 8px; flex-wrap: wrap;">
                <el-select
                  v-model="selectedKnowledgeIdToBind"
                  placeholder="选择要绑定的知识库"
                  filterable
                  style="width: 260px;"
                >
                  <el-option
                    v-for="kb in allKnowledgeBases"
                    :key="kb.id"
                    :label="kb.name || `知识库 #${kb.id}`"
                    :value="kb.id"
                  />
                </el-select>
                <el-button
                  type="primary"
                  size="small"
                  :disabled="!selectedKnowledgeIdToBind || !currentAgentId"
                  @click="handleBindKnowledge"
                >
                  绑定到当前Agent
                </el-button>
                <span style="font-size: 12px; color: #909399;">需要先保存Agent，再绑定知识库</span>
              </div>
              <el-table
                v-if="currentAgentKnowledge.length > 0"
                :data="currentAgentKnowledge"
                size="small"
                border
                style="width: 100%; margin-top: 8px;"
              >
                <el-table-column prop="id" label="ID" width="80" />
                <el-table-column prop="name" label="名称" min-width="180" />
                <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
              </el-table>
              <div v-else style="font-size: 12px; color: #909399;">当前Agent暂未绑定任何知识库</div>
            </div>
          </el-form-item>
        </el-form>
        <template #footer>
          <span class="dialog-footer">
            <el-button @click="dialogVisible = false">取消</el-button>
            <el-button type="primary" @click="handleSubmit">确定</el-button>
          </span>
        </template>
      </el-dialog>
    </div>
  </Layout>
</template>

<script setup>
import { onMounted, ref, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Edit, Delete, Plus, Upload } from '@element-plus/icons-vue'
import Layout from '../components/Layout.vue'
import { useUserStore } from '../stores/userStore'
import agentService from '../api/agent'
import knowledgeApi from '../api/knowledge'

const userStore = useUserStore()

// localStorage 模板 key
const API_CONFIG_TEMPLATES_KEY = 'api_config_templates'
const PLUGIN_CONFIG_TEMPLATES_KEY = 'plugin_config_templates'

// 响应式数据
const agents = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const dialogType = ref('create') // 'create' 或 'edit'
const currentAgentId = ref(null)
const agentFormRef = ref(null)

// 知识库绑定相关
const allKnowledgeBases = ref([])
const currentAgentKnowledge = ref([])
const selectedKnowledgeIdToBind = ref(null)

// 模板相关
const apiConfigTemplates = ref([])
const pluginConfigTemplates = ref([])
const selectedApiTemplateName = ref('')
const selectedPluginTemplateName = ref('')
const saveApiTemplateDialogVisible = ref(false)
const savePluginTemplateDialogVisible = ref(false)
const apiTemplateName = ref('')
const pluginTemplateName = ref('')

// 分页数据
const pagination = reactive({
  currentPage: 1,
  pageSize: 10,
  total: 0
})

// 表单数据
const agentForm = reactive({
  name: '',
  description: '',
  type: 'general',
  apiConfig: '',  // API配置信息
  systemPrompt: '',  // 系统提示词
  pluginsJson: '',  // 插件配置（JSON格式）
  configJson: '{}',  // 配置信息
  status: 1 // 默认启用（数字类型）
})

// 表单验证规则
const rules = {
  name: [
    { required: true, message: '请输入智能体名称', trigger: 'blur' },
    { min: 2, max: 50, message: '名称长度在 2 到 50 个字符', trigger: 'blur' }
  ],
  description: [
    { max: 200, message: '描述不能超过 200 个字符', trigger: 'blur' }
  ],
  apiConfig: [
    { max: 500, message: 'API配置不能超过 500 个字符', trigger: 'blur' }
  ],
  systemPrompt: [
    { max: 2000, message: '系统提示词不能超过 2000 个字符', trigger: 'blur' }
  ],
  pluginsJson: [
    {
      validator: (rule, value, callback) => {
        if (value) {
          try {
            JSON.parse(value)
            callback()
          } catch (e) {
            callback(new Error('请输入有效的JSON格式'))
          }
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  configJson: [
    {
      validator: (rule, value, callback) => {
        if (value) {
          try {
            JSON.parse(value)
            callback()
          } catch (e) {
            callback(new Error('请输入有效的JSON格式'))
          }
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

// ===== 本地模板相关 =====
const loadApiConfigTemplates = () => {
  try {
    const raw = localStorage.getItem(API_CONFIG_TEMPLATES_KEY)
    return raw ? JSON.parse(raw) : []
  } catch (e) {
    console.error('加载 API 模板失败:', e)
    return []
  }
}

const loadPluginConfigTemplates = () => {
  try {
    const raw = localStorage.getItem(PLUGIN_CONFIG_TEMPLATES_KEY)
    return raw ? JSON.parse(raw) : []
  } catch (e) {
    console.error('加载插件模板失败:', e)
    return []
  }
}

const persistApiTemplates = () => {
  localStorage.setItem(API_CONFIG_TEMPLATES_KEY, JSON.stringify(apiConfigTemplates.value))
}

const persistPluginTemplates = () => {
  localStorage.setItem(PLUGIN_CONFIG_TEMPLATES_KEY, JSON.stringify(pluginConfigTemplates.value))
}

const openSaveApiTemplateDialog = () => {
  if (!agentForm.apiConfig) {
    ElMessage.warning('当前 API 配置为空，无法保存为模板')
    return
  }
  apiTemplateName.value = ''
  saveApiTemplateDialogVisible.value = true
}

const openSavePluginTemplateDialog = () => {
  if (!agentForm.pluginsJson) {
    ElMessage.warning('当前插件配置为空，无法保存为模板')
    return
  }
  pluginTemplateName.value = ''
  savePluginTemplateDialogVisible.value = true
}

const confirmSaveApiTemplate = () => {
  const name = apiTemplateName.value.trim()
  if (!name) {
    ElMessage.warning('请输入模板名称')
    return
  }
  // 如果重名，覆盖
  const existingIndex = apiConfigTemplates.value.findIndex(t => t.name === name)
  const item = { name, config: agentForm.apiConfig, createdAt: new Date().toISOString() }
  if (existingIndex >= 0) {
    apiConfigTemplates.value.splice(existingIndex, 1, item)
  } else {
    apiConfigTemplates.value.push(item)
  }
  persistApiTemplates()
  ElMessage.success('API 模板已保存到本地浏览器')
  saveApiTemplateDialogVisible.value = false
}

const confirmSavePluginTemplate = () => {
  const name = pluginTemplateName.value.trim()
  if (!name) {
    ElMessage.warning('请输入模板名称')
    return
  }
  const existingIndex = pluginConfigTemplates.value.findIndex(t => t.name === name)
  const item = { name, config: agentForm.pluginsJson, createdAt: new Date().toISOString() }
  if (existingIndex >= 0) {
    pluginConfigTemplates.value.splice(existingIndex, 1, item)
  } else {
    pluginConfigTemplates.value.push(item)
  }
  persistPluginTemplates()
  ElMessage.success('插件模板已保存到本地浏览器')
  savePluginTemplateDialogVisible.value = false
}

const applyApiTemplate = (name) => {
  const t = apiConfigTemplates.value.find(item => item.name === name)
  if (t) {
    agentForm.apiConfig = t.config
    ElMessage.success('已应用 API 模板')
  }
}

const applyPluginTemplate = (name) => {
  const t = pluginConfigTemplates.value.find(item => item.name === name)
  if (t) {
    agentForm.pluginsJson = t.config
    ElMessage.success('已应用插件模板')
  }
}

// ===== 知识库相关 =====
const fetchAllKnowledgeBases = async () => {
  try {
    const res = await knowledgeApi.listKnowledgeBases()
    allKnowledgeBases.value = Array.isArray(res) ? res : res.data || []
  } catch (e) {
    console.error('获取知识库列表失败:', e)
  }
}

const fetchCurrentAgentKnowledge = async () => {
  if (!currentAgentId.value) {
    currentAgentKnowledge.value = []
    return
  }
  try {
    const res = await agentService.getAgentKnowledge(currentAgentId.value)
    currentAgentKnowledge.value = Array.isArray(res) ? res : res.data || []
  } catch (e) {
    console.error('获取 Agent 知识库失败:', e)
    currentAgentKnowledge.value = []
  }
}

const handleBindKnowledge = async () => {
  if (!currentAgentId.value) {
    ElMessage.warning('请先保存 Agent，再绑定知识库')
    return
  }
  if (!selectedKnowledgeIdToBind.value) {
    ElMessage.warning('请选择要绑定的知识库')
    return
  }
  try {
    await agentService.addAgentKnowledge(currentAgentId.value, selectedKnowledgeIdToBind.value)
    ElMessage.success('绑定知识库成功')
    await fetchCurrentAgentKnowledge()
  } catch (e) {
    console.error('绑定知识库失败:', e)
    ElMessage.error('绑定知识库失败，请稍后重试')
  }
}

// 获取智能体列表
const fetchAgents = async () => {
  loading.value = true
  try {
    // 传递分页参数
    const params = {
      page: pagination.currentPage,
      pageSize: pagination.pageSize
    }
    const response = await agentService.getAgents(params)

    // 假设后端返回的格式为 { data: [...], total: number }
    if (response && response.data) {
      agents.value = Array.isArray(response.data) ? response.data : []
      // 使用后端返回的总数，而不是前端计算
      pagination.total = response.total || 0
    } else {
      // 兼容旧格式
      agents.value = Array.isArray(response) ? response : []
      pagination.total = agents.value.length
    }

    console.log('获取智能体列表成功:', agents.value)
  } catch (error) {
    console.error('获取智能体列表失败:', error)
    ElMessage.error('获取智能体列表失败，请稍后重试')
    agents.value = []
    pagination.total = 0
  } finally {
    loading.value = false
  }
}

// 创建智能体
const handleCreateAgent = () => {
  dialogType.value = 'create'
  currentAgentId.value = null
  // 重置表单
  Object.assign(agentForm, {
    name: '',
    description: '',
    type: 'general',
    apiConfig: '',
    systemPrompt: '',
    pluginsJson: '',
    configJson: '{}',
    status: 1 // 默认启用（数字类型）
  })
  currentAgentKnowledge.value = []
  selectedKnowledgeIdToBind.value = null
  if (agentFormRef.value) {
    agentFormRef.value.resetFields()
  }
  dialogVisible.value = true
}

// 编辑智能体
const handleEditAgent = async (agent) => {
  dialogType.value = 'edit'
  currentAgentId.value = agent.id

  try {
    // 获取智能体详情
    const response = await agentService.getAgentById(agent.id)
    const detail = response.data || response
    // 填充表单
    Object.assign(agentForm, {
      name: detail.name || '',
      description: detail.description || '',
      type: detail.type || 'general',
      apiConfig: detail.apiConfig || '',
      systemPrompt: detail.systemPrompt || '',
      pluginsJson: detail.pluginsJson || '',
      configJson: typeof detail.configJson === 'string' ? detail.configJson : JSON.stringify(detail.configJson || {}, null, 2),
      status: detail.status ?? 1 // 保持数字类型
    })
    await fetchCurrentAgentKnowledge()
    dialogVisible.value = true
  } catch (error) {
    console.error('获取智能体详情失败:', error)
    ElMessage.error('获取智能体详情失败，请稍后重试')
  }
}

// 删除智能体
const handleDeleteAgent = (id, name) => {
  ElMessageBox.confirm(
    `确定要删除智能体「${name}」吗？此操作不可恢复。`,
    '确认删除',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  )
    .then(async () => {
      try {
        await agentService.deleteAgent(id)
        ElMessage.success('删除成功')
        // 重新获取列表
        await fetchAgents()
      } catch (error) {
        console.error('删除智能体失败:', error)
        ElMessage.error('删除失败，请稍后重试')
      }
    })
    .catch(() => {
      // 取消删除
    })
}

// 提交表单
const handleSubmit = async () => {
  if (!agentFormRef.value) return

  await agentFormRef.value.validate(async (valid) => {
    if (valid) {
      try {
        // 处理表单数据，确保JSON字段格式正确
        const formData = {
          ...agentForm,
          // 如果pluginsJson为空字符串，设为null（数据库JSON类型不接受空字符串）
          pluginsJson: agentForm.pluginsJson || null,
          // 如果configJson为空字符串，设为null
          configJson: agentForm.configJson || null
        }

        if (dialogType.value === 'create') {
          const res = await agentService.createAgent(formData)
          ElMessage.success('创建成功')
          // 创建后刷新列表并尝试获取新建的 agentId
          await fetchAgents()
        } else {
          await agentService.updateAgent(currentAgentId.value, formData)
          ElMessage.success('更新成功')
          await fetchAgents()
        }

        dialogVisible.value = false
      } catch (error) {
        console.error(`${dialogType.value === 'create' ? '创建' : '更新'}智能体失败:`, error)
        ElMessage.error(`${dialogType.value === 'create' ? '创建' : '更新'}失败，请稍后重试`)
      }
    }
  })
}

// 处理API配置文件上传
const handleApiConfigUpload = (file) => {
  const reader = new FileReader()
  reader.onload = (e) => {
    const content = e.target.result.trim()
    agentForm.apiConfig = content
    ElMessage.success('API配置导入成功')
  }
  reader.onerror = () => {
    ElMessage.error('文件读取失败')
  }
  reader.readAsText(file)
  return false
}

// 处理插件配置JSON文件上传
const handlePluginsJsonUpload = (file) => {
  const reader = new FileReader()
  reader.onload = (e) => {
    try {
      const content = e.target.result
      // 验证是否为有效的JSON
      JSON.parse(content)
      agentForm.pluginsJson = content
      ElMessage.success('JSON文件导入成功')
    } catch (error) {
      ElMessage.error('无效的JSON文件，请检查文件格式')
    }
  }
  reader.onerror = () => {
    ElMessage.error('文件读取失败')
  }
  reader.readAsText(file)
  // 返回false阻止默认上传行为
  return false
}

// 分页处理
const handleSizeChange = (size) => {
  pagination.pageSize = size
  pagination.currentPage = 1
  // 重新获取数据
  fetchAgents()
}

const handleCurrentChange = (current) => {
  pagination.currentPage = current
  // 重新获取数据
  fetchAgents()
}

// 辅助函数：获取智能体类型标签样式
const getAgentTypeTag = (type) => {
  const typeMap = {
    'general': 'primary',
    'knowledge': 'success',
    'task': 'warning',
    'creative': 'info'
  }
  return typeMap[type] || 'default'
}

// 辅助函数：格式化日期
const formatDate = (dateString) => {
  if (!dateString) return '-'
  const date = new Date(dateString)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

// 初始化
onMounted(async () => {
  console.log('Agent管理页面已加载')
  // 确保用户信息已初始化
  if (!userStore.initialized) {
    await userStore.initializeUserInfo()
  }
  apiConfigTemplates.value = loadApiConfigTemplates()
  pluginConfigTemplates.value = loadPluginConfigTemplates()
  await fetchAgents()
  await fetchAllKnowledgeBases()
})
</script>

<style scoped>
.agents-container {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-header h1 {
  color: #303133;
  font-size: 24px;
  margin: 0;
}

.agents-card {
  background-color: rgba(255, 255, 255, 0.6);
}

.loading-container {
  padding: 20px 0;
}

.agent-name {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.name-text {
  font-weight: 500;
  color: #303133;
}

.agent-desc {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
  max-height: 34px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .agents-container {
    padding: 10px;
  }
  
  .page-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }
  
  .pagination-container {
    justify-content: center;
  }
}
</style>