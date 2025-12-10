<template>
  <Layout :userInfo="userStore.userInfo">
    <div class="resource-view">
      <el-card class="box-card">
        <template #header>
          <div class="card-header">
            <span>资源库</span>
          </div>
        </template>

        <el-tabs v-model="activeTab">
          <!-- API 模板 -->
          <el-tab-pane label="API 模板" name="api">
            <div class="tab-header">
              <el-button type="primary" @click="openApiTemplateDialog()">新建模板</el-button>
              <el-button @click="reloadApiTemplates">刷新</el-button>
            </div>
            <el-table
              v-if="apiTemplates.length > 0"
              :data="apiTemplates"
              size="small"
              style="width: 100%"
            >
              <el-table-column prop="name" label="名称" width="180" />
              <el-table-column label="内容预览" min-width="260">
                <template #default="scope">
                  <span class="config-preview">{{ previewConfig(scope.row.config) }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="createdAt" label="创建时间" width="180">
                <template #default="scope">
                  <span>{{ formatDate(scope.row.createdAt) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="160">
                <template #default="scope">
                  <el-button
                    type="primary"
                    link
                    size="small"
                    @click="openApiTemplateDialog(scope.row, scope.$index)"
                  >
                    编辑
                  </el-button>
                  <el-button
                    type="danger"
                    link
                    size="small"
                    @click="removeApiTemplate(scope.$index)"
                  >
                    删除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
            <div v-else class="empty-tip">
              暂无 API 模板，可以在「Agent配置」中保存，或点击上方「新建模板」。
            </div>
          </el-tab-pane>

          <!-- 插件模板 -->
          <el-tab-pane label="插件模板" name="plugin">
            <div class="tab-header">
              <el-button type="primary" @click="openPluginTemplateDialog()">新建模板</el-button>
              <el-button @click="reloadPluginTemplates">刷新</el-button>
            </div>
            <el-table
              v-if="pluginTemplates.length > 0"
              :data="pluginTemplates"
              size="small"
              style="width: 100%"
            >
              <el-table-column prop="name" label="名称" width="180" />
              <el-table-column label="内容预览" min-width="260">
                <template #default="scope">
                  <span class="config-preview">{{ previewConfig(scope.row.config) }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="createdAt" label="创建时间" width="180">
                <template #default="scope">
                  <span>{{ formatDate(scope.row.createdAt) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="160">
                <template #default="scope">
                  <el-button
                    type="primary"
                    link
                    size="small"
                    @click="openPluginTemplateDialog(scope.row, scope.$index)"
                  >
                    编辑
                  </el-button>
                  <el-button
                    type="danger"
                    link
                    size="small"
                    @click="removePluginTemplate(scope.$index)"
                  >
                    删除
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
            <div v-else class="empty-tip">
              暂无插件模板，可以在「Agent配置」中保存，或点击上方「新建模板」。
            </div>
          </el-tab-pane>

          <!-- 知识库 -->
          <el-tab-pane label="知识库" name="knowledge">
            <div class="tab-header">
              <el-button type="primary" @click="openUploadDialog">上传知识文件</el-button>
              <el-button @click="refreshList">刷新</el-button>
            </div>
            <el-table :data="knowledgeList" style="width: 100%" v-loading="loading">
              <el-table-column prop="id" label="ID" width="80" />
              <el-table-column prop="name" label="名称" min-width="180" />
              <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
              <el-table-column prop="createdAt" label="创建时间" width="180">
                <template #default="scope">
                  <span>{{ formatDate(scope.row.createdAt) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="120">
                <template #default="scope">
                  <el-button type="primary" link size="small" @click="openSearchDialog(scope.row)">
                    向量检索
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </el-card>

      <!-- API 模板编辑对话框 -->
      <el-dialog v-model="apiTemplateDialogVisible" title="编辑 API 模板" width="600px">
        <el-form label-width="80px">
          <el-form-item label="名称">
            <el-input v-model="editingApiTemplate.name" placeholder="请输入模板名称" />
          </el-form-item>
          <el-form-item label="内容">
            <el-input
              v-model="editingApiTemplate.config"
              type="textarea"
              :rows="8"
              placeholder="请输入 API 配置内容，可为纯文本或 JSON"
            />
          </el-form-item>
        </el-form>
        <template #footer>
          <span class="dialog-footer">
            <el-button @click="apiTemplateDialogVisible = false">取 消</el-button>
            <el-button type="primary" @click="saveApiTemplate">保 存</el-button>
          </span>
        </template>
      </el-dialog>

      <!-- 插件模板编辑对话框 -->
      <el-dialog v-model="pluginTemplateDialogVisible" title="编辑插件模板" width="600px">
        <el-form label-width="80px">
          <el-form-item label="名称">
            <el-input v-model="editingPluginTemplate.name" placeholder="请输入模板名称" />
          </el-form-item>
          <el-form-item label="内容">
            <el-input
              v-model="editingPluginTemplate.config"
              type="textarea"
              :rows="8"
              placeholder="请输入插件配置内容，建议为 JSON 格式"
            />
          </el-form-item>
        </el-form>
        <template #footer>
          <span class="dialog-footer">
            <el-button @click="pluginTemplateDialogVisible = false">取 消</el-button>
            <el-button type="primary" @click="savePluginTemplate">保 存</el-button>
          </span>
        </template>
      </el-dialog>

      <!-- 知识库上传对话框 -->
      <el-dialog v-model="uploadDialogVisible" title="上传知识文件" width="500px">
        <el-form :model="uploadForm" label-width="80px">
          <el-form-item label="文件">
            <el-upload
              class="upload-demo"
              drag
              :auto-upload="false"
              :limit="1"
              :file-list="fileList"
              :on-change="handleFileChange"
            >
              <i class="el-icon-upload" />
              <div class="el-upload__text">
                将文件拖到此处，或<em>点击上传</em>
              </div>
              <template #tip>
                <div class="el-upload__tip">
                  支持 PDF / Word / Markdown 等常见格式，单个文件不要太大。
                </div>
              </template>
            </el-upload>
          </el-form-item>

          <el-form-item label="名称">
            <el-input v-model="uploadForm.name" placeholder="可选，不填则使用文件名" />
          </el-form-item>

          <el-form-item label="描述">
            <el-input
              v-model="uploadForm.description"
              type="textarea"
              :rows="3"
              placeholder="可选，对知识库做一个简单说明"
            />
          </el-form-item>

          <el-form-item label="绑定Agent">
            <el-input
              v-model="uploadForm.agentId"
              placeholder="可选，填写要绑定的 Agent ID（暂时先用数字ID）"
            />
          </el-form-item>
        </el-form>

        <template #footer>
          <span class="dialog-footer">
            <el-button @click="uploadDialogVisible = false">取 消</el-button>
            <el-button type="primary" :loading="uploading" @click="submitUpload">上 传</el-button>
          </span>
        </template>
      </el-dialog>

      <!-- 向量检索测试对话框 -->
      <el-dialog v-model="searchDialogVisible" title="向量检索测试" width="700px">
        <div class="search-container">
          <div class="search-header" style="margin-bottom: 15px; font-weight: bold;">
            <span>当前知识库：{{ currentKnowledgeBase?.name }} (ID: {{ currentKnowledgeBase?.id }})</span>
          </div>
          <div class="search-input-area" style="margin-bottom: 20px;">
            <el-input
              v-model="searchQuery"
              placeholder="请输入要检索的文本..."
              @keyup.enter="handleVectorSearch"
              clearable
            >
              <template #append>
                <el-button @click="handleVectorSearch" :loading="searching">检索</el-button>
              </template>
            </el-input>
          </div>
          
          <div class="search-results" v-if="searchResults.length > 0">
            <div class="result-title" style="margin-bottom: 10px; font-weight: bold;">检索结果 (Top {{ searchResults.length }})：</div>
            <el-card v-for="(item, index) in searchResults" :key="index" class="result-item" shadow="hover" style="margin-bottom: 10px;">
              <div class="result-score" style="margin-bottom: 5px;">
                <el-tag size="small" :type="getScoreTagType(item.score)">距离: {{ item.score.toFixed(4) }}</el-tag>
              </div>
              <div class="result-content" style="white-space: pre-wrap; line-height: 1.5;">{{ item.content }}</div>
              <div class="result-meta" v-if="item.metadata" style="margin-top: 5px; color: #999; font-size: 12px;">元数据: {{ item.metadata }}</div>
            </el-card>
          </div>
          <div v-else-if="searched" class="empty-result" style="text-align: center; color: #999; padding: 20px;">
            未找到相关结果
          </div>
        </div>
      </el-dialog>
    </div>
  </Layout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import Layout from '../components/Layout.vue'
import { useUserStore } from '../stores/userStore'
import knowledgeApi from '../api/knowledge'

const userStore = useUserStore()

const API_CONFIG_TEMPLATES_KEY = 'api_config_templates'
const PLUGIN_CONFIG_TEMPLATES_KEY = 'plugin_config_templates'

const activeTab = ref('api')

// API 模板
const apiTemplates = ref([])
const apiTemplateDialogVisible = ref(false)
const editingApiTemplate = ref({ name: '', config: '' })
const editingApiIndex = ref(-1)

// 插件模板
const pluginTemplates = ref([])
const pluginTemplateDialogVisible = ref(false)
const editingPluginTemplate = ref({ name: '', config: '' })
const editingPluginIndex = ref(-1)

// 知识库列表
const knowledgeList = ref([])
const loading = ref(false)

// 向量检索
const searchDialogVisible = ref(false)
const currentKnowledgeBase = ref(null)
const searchQuery = ref('')
const searchResults = ref([])
const searching = ref(false)
const searched = ref(false)

const openSearchDialog = (kb) => {
  currentKnowledgeBase.value = kb
  searchQuery.value = ''
  searchResults.value = []
  searched.value = false
  searchDialogVisible.value = true
}

const handleVectorSearch = async () => {
  if (!searchQuery.value.trim()) {
    ElMessage.warning('请输入检索内容')
    return
  }
  searching.value = true
  searched.value = true
  searchResults.value = []
  try {
    const res = await knowledgeApi.vectorSearch(searchQuery.value, currentKnowledgeBase.value.id)
    // apiClient returns response.data (the body)
    if (res.code === 200) {
      searchResults.value = res.data
    } else {
      ElMessage.error(res.message || '检索失败')
    }
  } catch (error) {
    ElMessage.error('检索异常')
  } finally {
    searching.value = false
  }
}

const getScoreTagType = (score) => {
  if (score < 0.3) return 'success'
  if (score < 0.5) return 'warning'
  return 'danger'
}

// 知识库上传
const uploadDialogVisible = ref(false)
const uploadForm = ref({
  file: null,
  name: '',
  description: '',
  agentId: ''
})
const fileList = ref([])
const uploading = ref(false)

const loadTemplatesFromStorage = (key) => {
  try {
    const raw = localStorage.getItem(key)
    return raw ? JSON.parse(raw) : []
  } catch (e) {
    console.error('加载模板失败:', e)
    return []
  }
}

const saveTemplatesToStorage = (key, list) => {
  localStorage.setItem(key, JSON.stringify(list))
}

const reloadApiTemplates = () => {
  apiTemplates.value = loadTemplatesFromStorage(API_CONFIG_TEMPLATES_KEY)
}

const reloadPluginTemplates = () => {
  pluginTemplates.value = loadTemplatesFromStorage(PLUGIN_CONFIG_TEMPLATES_KEY)
}

const openApiTemplateDialog = (row, index) => {
  if (row) {
    editingApiTemplate.value = { ...row }
    editingApiIndex.value = index
  } else {
    editingApiTemplate.value = { name: '', config: '' }
    editingApiIndex.value = -1
  }
  apiTemplateDialogVisible.value = true
}

const saveApiTemplate = () => {
  const name = editingApiTemplate.value.name?.trim()
  if (!name) {
    ElMessage.warning('请输入模板名称')
    return
  }
  const item = {
    ...editingApiTemplate.value,
    name,
    createdAt: editingApiTemplate.value.createdAt || new Date().toISOString()
  }
  if (editingApiIndex.value >= 0) {
    apiTemplates.value.splice(editingApiIndex.value, 1, item)
  } else {
    const existIndex = apiTemplates.value.findIndex(t => t.name === name)
    if (existIndex >= 0) {
      apiTemplates.value.splice(existIndex, 1, item)
    } else {
      apiTemplates.value.push(item)
    }
  }
  saveTemplatesToStorage(API_CONFIG_TEMPLATES_KEY, apiTemplates.value)
  ElMessage.success('API 模板已保存到本地浏览器')
  apiTemplateDialogVisible.value = false
}

const removeApiTemplate = (index) => {
  ElMessageBox.confirm('确定要删除该 API 模板吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(() => {
      apiTemplates.value.splice(index, 1)
      saveTemplatesToStorage(API_CONFIG_TEMPLATES_KEY, apiTemplates.value)
      ElMessage.success('已删除 API 模板')
    })
    .catch(() => {})
}

const openPluginTemplateDialog = (row, index) => {
  if (row) {
    editingPluginTemplate.value = { ...row }
    editingPluginIndex.value = index
  } else {
    editingPluginTemplate.value = { name: '', config: '' }
    editingPluginIndex.value = -1
  }
  pluginTemplateDialogVisible.value = true
}

const savePluginTemplate = () => {
  const name = editingPluginTemplate.value.name?.trim()
  if (!name) {
    ElMessage.warning('请输入模板名称')
    return
  }
  const item = {
    ...editingPluginTemplate.value,
    name,
    createdAt: editingPluginTemplate.value.createdAt || new Date().toISOString()
  }
  if (editingPluginIndex.value >= 0) {
    pluginTemplates.value.splice(editingPluginIndex.value, 1, item)
  } else {
    const existIndex = pluginTemplates.value.findIndex(t => t.name === name)
    if (existIndex >= 0) {
      pluginTemplates.value.splice(existIndex, 1, item)
    } else {
      pluginTemplates.value.push(item)
    }
  }
  saveTemplatesToStorage(PLUGIN_CONFIG_TEMPLATES_KEY, pluginTemplates.value)
  ElMessage.success('插件模板已保存到本地浏览器')
  pluginTemplateDialogVisible.value = false
}

const removePluginTemplate = (index) => {
  ElMessageBox.confirm('确定要删除该插件模板吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(() => {
      pluginTemplates.value.splice(index, 1)
      saveTemplatesToStorage(PLUGIN_CONFIG_TEMPLATES_KEY, pluginTemplates.value)
      ElMessage.success('已删除插件模板')
    })
    .catch(() => {})
}

const formatDate = (val) => {
  if (!val) return ''
  try {
    return new Date(val).toLocaleString()
  } catch (e) {
    return val
  }
}

const previewConfig = (text) => {
  if (!text) return ''
  const trimmed = String(text).replace(/\s+/g, ' ')
  return trimmed.length > 80 ? `${trimmed.slice(0, 80)}...` : trimmed
}

const fetchKnowledgeList = async () => {
  loading.value = true
  try {
    const res = await knowledgeApi.listKnowledgeBases()
    knowledgeList.value = Array.isArray(res) ? res : res.data || []
  } catch (error) {
    console.error('加载知识库列表失败:', error)
    ElMessage.error('加载知识库列表失败')
  } finally {
    loading.value = false
  }
}

const openUploadDialog = () => {
  uploadForm.value = {
    file: null,
    name: '',
    description: '',
    agentId: ''
  }
  fileList.value = []
  uploadDialogVisible.value = true
}

const handleFileChange = (file, fileListVal) => {
  uploadForm.value.file = file.raw
  fileList.value = fileListVal.slice(-1)
  if (!uploadForm.value.name) {
    uploadForm.value.name = file.name
  }
}

const submitUpload = async () => {
  if (!uploadForm.value.file) {
    ElMessage.warning('请先选择要上传的文件')
    return
  }
  uploading.value = true
  try {
    const { file, name, description, agentId } = uploadForm.value
    await knowledgeApi.uploadKnowledge({
      file,
      name,
      description,
      agentId: agentId ? Number(agentId) : undefined
    })
    ElMessage.success('上传成功，系统会自动进行拆分和向量化')
    uploadDialogVisible.value = false
    fetchKnowledgeList()
  } catch (error) {
    console.error('上传知识库失败:', error)
    ElMessage.error('上传失败，请检查文件格式或稍后重试')
  } finally {
    uploading.value = false
  }
}

const refreshList = () => {
  fetchKnowledgeList()
}

onMounted(async () => {
  if (userStore && !userStore.initialized && userStore.initializeUserInfo) {
    try {
      await userStore.initializeUserInfo()
    } catch (e) {
      console.error('初始化用户信息失败:', e)
    }
  }
  reloadApiTemplates()
  reloadPluginTemplates()
  fetchKnowledgeList()
})
</script>

<style scoped>
.resource-view {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.tab-header {
  display: flex;
  justify-content: flex-start;
  gap: 8px;
  margin-bottom: 12px;
}

.config-preview {
  font-size: 12px;
  color: #606266;
}

.empty-tip {
  padding: 24px 0;
  text-align: center;
  color: #909399;
  font-size: 13px;
}
</style>

