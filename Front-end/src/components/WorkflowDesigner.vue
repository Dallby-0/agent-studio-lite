<template>
  <div class="workflow-designer">
    <!-- 工具栏 -->
    <div class="designer-toolbar">
      <div class="workflow-name-input">
        <el-input 
          v-model="workflowName" 
          placeholder="输入工作流名称" 
          size="small"
          style="width: 200px; margin-right: 12px;"
        ></el-input>
      </div>
      <el-button type="primary" @click="saveWorkflow">保存工作流</el-button>
      <el-button @click="runWorkflow">运行工作流</el-button>
      <div class="toolbar-right">
        <el-button @click="zoomIn" size="small">放大</el-button>
        <el-button @click="zoomOut" size="small">缩小</el-button>
        <el-button @click="resetZoom" size="small">重置缩放</el-button>
      </div>
    </div>
    
    <!-- 设计器主体 -->
    <div class="designer-container">
      <!-- 左侧节点面板 -->
      <div class="node-palette">
        <h3>节点类型</h3>
        <div class="node-list">
          <div 
            v-for="nodeType in nodeTypes" 
            :key="nodeType.type"
            class="node-item"
            draggable="true"
            @dragstart="onDragStart($event, nodeType)"
          >
            <div class="node-icon">{{ nodeType.icon }}</div>
            <div class="node-label">{{ nodeType.label }}</div>
          </div>
        </div>
      </div>
      
      <!-- 中间画布区域 -->
      <div 
        class="canvas-container"
        ref="canvasContainer"
        @dragover.prevent="onDragOver"
        @drop.prevent="onDrop"
        @mousedown="onCanvasMouseDown"
        @mousemove="onCanvasMouseMove"
        @mouseup="onCanvasMouseUp"
        @wheel.prevent="onCanvasWheel"
      >
        <div 
          class="canvas"
          :style="{ 
            transform: `translate(${canvasOffset.x}px, ${canvasOffset.y}px) scale(${zoomLevel})`, 
            transformOrigin: 'top left' 
          }"
        >
          <!-- 网格背景 -->
          <div class="grid-background"></div>
          
          <!-- SVG连接线层 -->
          <svg 
            class="connections-layer" 
            ref="connectionsLayer"
            :width="canvasWidth" 
            :height="canvasHeight"
          >
            <!-- 已创建的连接线（使用贝塞尔曲线） -->
            <g v-for="edge in edges" :key="edge.id">
              <path
                :d="getEdgePath(edge)"
              class="connection-line"
                :class="{ 
                  'connection-line-selected': selectedEdge?.id === edge.id,
                  'connection-line-reverse': isReverseConnection(edge)
                }"
                @mousedown.stop="selectEdge(edge)"
                @click.stop="selectEdge(edge)"
              />
              <!-- 连接线上的删除按钮（选中时显示） -->
              <circle
                v-if="selectedEdge?.id === edge.id"
                :cx="getEdgeMidX(edge)"
                :cy="getEdgeMidY(edge)"
                r="8"
                class="edge-delete-btn"
                @click.stop="deleteEdge(edge)"
              />
            </g>
            
            <!-- 正在创建的连接线预览 -->
            <path
              v-if="isConnecting"
              :d="getPreviewPath()"
              class="connection-line connection-line-preview"
            />
          </svg>
          
          <!-- 节点 -->
          <div 
            v-for="node in nodes" 
            :key="node.nodeKey"
            class="workflow-node"
            :class="{ 
              'node-selected': selectedNode?.nodeKey === node.nodeKey,
              'branch-node': isBranchNode(node.type)
            }"
            :style="{
              left: `${node.positionX}px`, 
              top: `${node.positionY}px`,
              width: `${nodeWidth}px`,
              minHeight: isBranchNode(node.type) ? `${getBranchNodeHeight(node)}px` : `${nodeHeight}px`
            }"
            @mousedown.stop="onNodeMouseDown($event, node)"
            @click.stop="selectNode(node)"
          >
            <!-- 输入端口（左侧） -->
              <div class="port-group port-group-input">
                <div 
                v-for="(port, index) in getInputPorts(node)" 
                  :key="`input-${index}`"
                  class="port port-input"
                  :data-node-key="node.nodeKey"
                  :data-port-index="index"
                :style="{ top: `${getInputPortY(node, index)}px` }"
                @mousedown.stop="onPortMouseDown($event, node, 'input', index)"
                  @click.stop="onPortClick($event, node, 'input', index)"
                >
                <div class="port-triangle port-triangle-input"></div>
                </div>
                </div>
            
            <!-- 节点内容 -->
            <div class="node-content">
              <div class="node-header">
                <div class="node-type-icon">{{ getNodeTypeIcon(node.type) }}</div>
                <div class="node-name">{{ node.name }}</div>
                  <el-button 
                    type="danger" 
                    size="small" 
                    circle 
                  text
                    @click.stop="deleteNode(node)"
                  >
                    <el-icon><Delete /></el-icon>
                  </el-button>
              </div>
              <div class="node-type">{{ getNodeTypeLabel(node.type) }}</div>
              <!-- 分支节点显示条件表达式 -->
              <div v-if="isBranchNode(node.type)" class="branch-conditions">
                <div 
                  v-for="(port, index) in getOutputPorts(node)" 
                  :key="`condition-${index}`"
                  class="branch-condition-item"
                  :style="{ top: `${getConditionItemY(node, index)}px` }"
                >
                  <span class="condition-label">分支{{ index + 1}}:</span>
                  <span class="condition-expression">{{ getBranchCondition(node, index) }}</span>
                </div>
              </div>
            </div>
            
            <!-- 输出端口（右侧） -->
              <div class="port-group port-group-output">
                <div 
                v-for="(port, index) in getOutputPorts(node)" 
                  :key="`output-${index}`"
                  class="port port-output"
                  :data-node-key="node.nodeKey"
                  :data-port-index="index"
                :style="{ top: `${getOutputPortY(node, index)}px` }"
                @mousedown.stop="onPortMouseDown($event, node, 'output', index)"
                  @click.stop="onPortClick($event, node, 'output', index)"
                >
                <div class="port-triangle port-triangle-output"></div>
                <!-- 分支节点输出端口的删除按钮 -->
                  <div 
                  v-if="isBranchNode(node.type) && getOutputPorts(node).length > 1"
                  class="port-delete-btn"
                  @click.stop="removeOutputPort(node, index)"
                  >
                  <el-icon><Close /></el-icon>
                  </div>
                </div>
              <!-- 分支节点添加输出端口按钮 -->
              <div 
                v-if="isBranchNode(node.type)"
                class="port-add-btn"
                :style="{ top: `${getAddPortY(node)}px` }"
                @click.stop="addOutputPort(node)"
              >
                <el-icon><Plus /></el-icon>
                </div>
              </div>
          </div>
        </div>
      </div>
      
      <!-- 右侧属性面板 -->
      <div class="properties-panel">
        <el-tabs v-model="activeTab" size="small">
          <!-- 节点属性标签 -->
          <el-tab-pane label="节点属性" name="node">
            <div v-if="selectedNode" class="node-properties">
              <el-form label-position="top" size="small">
                <el-form-item label="节点名称">
                  <el-input v-model="selectedNode.name" @input="updateNode"></el-input>
                </el-form-item>
                <el-form-item label="节点类型">
                  <el-input v-model="selectedNode.type" disabled></el-input>
                </el-form-item>
                
                <!-- 大模型调用节点配置 -->
                <template v-if="selectedNode.type === 'llm_call'">
                  <el-form-item label="系统提示词">
                    <el-input 
                      v-model="selectedNodeConfig.systemPrompt" 
                      type="textarea" 
                      :rows="4" 
                      @input="updateNodeConfig"
                    ></el-input>
                  </el-form-item>
                  <el-form-item label="用户提示词模板">
                    <el-input 
                      v-model="selectedNodeConfig.userPrompt" 
                      type="textarea" 
                      :rows="4" 
                      @input="updateNodeConfig"
                      placeholder="使用${变量名}来引用上下文变量"
                    ></el-input>
                  </el-form-item>
                  <el-form-item label="输出变量名">
                    <el-input v-model="selectedNodeConfig.outputVar" @input="updateNodeConfig"></el-input>
                  </el-form-item>
                  <el-divider>对话界面设置</el-divider>
                  <el-form-item label="打印到对话界面">
                    <el-switch 
                      v-model="selectedNodeConfig.enableChatOutput" 
                      @change="updateNodeConfig"
                    ></el-switch>
                  </el-form-item>
                  <el-form-item label="对话界面昵称" v-if="selectedNodeConfig.enableChatOutput">
                    <el-input 
                      v-model="selectedNodeConfig.chatNickname" 
                      @input="updateNodeConfig"
                      placeholder="留空则使用节点名称"
                    ></el-input>
                  </el-form-item>
                  <el-divider>历史对话设置</el-divider>
                  <el-form-item label="历史对话Key">
                    <el-input 
                      v-model="selectedNodeConfig.historyKey" 
                      @input="updateNodeConfig"
                      placeholder="默认为default"
                    ></el-input>
                    <div class="el-form-item__help">用于标识不同的历史对话容器</div>
                  </el-form-item>
                  <el-form-item label="引用历史对话">
                    <el-switch 
                      v-model="selectedNodeConfig.useHistory" 
                      @change="updateNodeConfig"
                    ></el-switch>
                    <div class="el-form-item__help">勾选后，发送API请求时会包含历史对话消息</div>
                  </el-form-item>
                  <el-form-item label="保留输出至历史对话">
                    <el-switch 
                      v-model="selectedNodeConfig.saveToHistory" 
                      @change="updateNodeConfig"
                    ></el-switch>
                    <div class="el-form-item__help">勾选后，每次输出会存入对应key的历史对话容器</div>
                  </el-form-item>
                  <el-form-item label="保存历史对话Key" v-if="selectedNodeConfig.saveToHistory">
                    <el-input 
                      v-model="selectedNodeConfig.saveHistoryKey" 
                      @input="updateNodeConfig"
                      placeholder="留空则使用上面的历史对话Key"
                    ></el-input>
                    <div class="el-form-item__help">用于保存输出的历史对话容器key，可与引用key不同</div>
                  </el-form-item>
                </template>
                
                <!-- 大模型赋值节点配置 -->
                <template v-if="selectedNode.type === 'llm_assign'">
                  <el-form-item label="用户提示词模板">
                    <el-input 
                      v-model="selectedNodeConfig.userPrompt" 
                      type="textarea" 
                      :rows="4" 
                      @input="updateNodeConfig"
                      placeholder="使用${变量名}来引用全局变量，AI将根据此提示词输出JSON格式的变量值"
                    ></el-input>
                  </el-form-item>
                  <el-divider>历史对话设置</el-divider>
                  <el-form-item label="历史对话Key">
                    <el-input 
                      v-model="selectedNodeConfig.historyKey" 
                      @input="updateNodeConfig"
                      placeholder="默认为default"
                    ></el-input>
                    <div class="el-form-item__help">用于标识不同的历史对话容器</div>
                  </el-form-item>
                  <el-form-item label="引用历史对话">
                    <el-switch 
                      v-model="selectedNodeConfig.useHistory" 
                      @change="updateNodeConfig"
                    ></el-switch>
                    <div class="el-form-item__help">勾选后，发送API请求时会包含历史对话消息（不会保存输出至历史对话）</div>
                  </el-form-item>
                  <el-form-item label="对话界面昵称" v-if="selectedNodeConfig.useHistory">
                    <el-input 
                      v-model="selectedNodeConfig.chatNickname" 
                      @input="updateNodeConfig"
                      placeholder="留空则使用节点名称"
                    ></el-input>
                    <div class="el-form-item__help">用于判断历史消息的role（相同昵称为assistant，否则为user）</div>
                  </el-form-item>
                  <el-divider>要赋值的全局变量</el-divider>
                  <div class="assign-variables-list">
                    <div 
                      v-for="(varItem, index) in (selectedNodeConfig.assignVariables || [])" 
                      :key="index" 
                      class="assign-variable-item"
                    >
                      <el-form label-position="top" size="small">
                        <div class="assign-variable-row">
                          <el-form-item label="变量名" :label-width="60">
                            <el-input 
                              v-model="varItem.name" 
                              @input="updateNodeConfig"
                              placeholder="支持中文，例如：用户姓名"
                            ></el-input>
                          </el-form-item>
                          <el-form-item label="类型" :label-width="40">
                            <el-select 
                              v-model="varItem.type" 
                              @change="updateNodeConfig" 
                              style="width: 100px;"
                            >
                              <el-option label="字符串" value="string"></el-option>
                              <el-option label="整数" value="integer"></el-option>
                              <el-option label="浮点数" value="double"></el-option>
                            </el-select>
                          </el-form-item>
                          <el-button 
                            type="danger" 
                            size="small" 
                            circle 
                            @click="removeAssignVariable(index)"
                            style="margin-top: 22px;"
                          >
                            <el-icon><Delete /></el-icon>
                          </el-button>
                        </div>
                      </el-form>
                    </div>
                    <el-button 
                      type="primary" 
                      size="small" 
                      @click="addAssignVariable"
                      style="margin-top: 10px;"
                    >
                      <el-icon><Plus /></el-icon>
                      添加变量
                    </el-button>
                    <div v-if="!selectedNodeConfig.assignVariables || selectedNodeConfig.assignVariables.length === 0" class="no-variables">
                      <p>暂无变量，点击添加按钮创建</p>
                    </div>
                  </div>
                </template>
                
                <!-- 大模型分支节点配置 -->
                <template v-if="selectedNode.type === 'llm_branch'">
                  <el-form-item label="用户提示词模板">
                    <el-input 
                      v-model="selectedNodeConfig.userPrompt" 
                      type="textarea" 
                      :rows="4" 
                      @input="updateNodeConfig"
                      placeholder="使用${变量名}来引用全局变量，AI将根据此提示词选择分支"
                    ></el-input>
                  </el-form-item>
                  <el-divider>历史对话设置</el-divider>
                  <el-form-item label="历史对话Key">
                    <el-input 
                      v-model="selectedNodeConfig.historyKey" 
                      @input="updateNodeConfig"
                      placeholder="默认为default"
                    ></el-input>
                    <div class="el-form-item__help">用于标识不同的历史对话容器</div>
                  </el-form-item>
                  <el-form-item label="引用历史对话">
                    <el-switch 
                      v-model="selectedNodeConfig.useHistory" 
                      @change="updateNodeConfig"
                    ></el-switch>
                    <div class="el-form-item__help">勾选后，发送API请求时会包含历史对话消息（不会保存输出至历史对话）</div>
                  </el-form-item>
                  <el-form-item label="对话界面昵称" v-if="selectedNodeConfig.useHistory">
                    <el-input 
                      v-model="selectedNodeConfig.chatNickname" 
                      @input="updateNodeConfig"
                      placeholder="留空则使用节点名称"
                    ></el-input>
                    <div class="el-form-item__help">用于判断历史消息的role（相同昵称为assistant，否则为user）</div>
                  </el-form-item>
                  <el-divider>分支列表</el-divider>
                  <div class="llm-branch-list">
                    <div 
                      v-for="(branch, index) in (selectedNodeConfig.branches || [])" 
                      :key="index" 
                      class="llm-branch-item"
                    >
                      <el-form label-position="top" size="small">
                        <div class="llm-branch-row">
                          <el-form-item :label="index === (selectedNodeConfig.branches || []).length - 1 ? '分支名称（默认分支）' : '分支名称'" 
                                        :label-width="index === (selectedNodeConfig.branches || []).length - 1 ? 120 : 80">
                            <el-input 
                              v-model="branch.name" 
                              @input="updateNodeConfig"
                              :placeholder="index === (selectedNodeConfig.branches || []).length - 1 ? '例如：通过（默认分支）' : '例如：拒绝、待审核'"
                            >
                              <template v-if="index === (selectedNodeConfig.branches || []).length - 1" #prefix>
                                <span style="color: #409eff; font-size: 12px;">默认</span>
                              </template>
                            </el-input>
                          </el-form-item>
                          <el-form-item label="描述（可选）" :label-width="80">
                            <el-input 
                              v-model="branch.description" 
                              @input="updateNodeConfig"
                              placeholder="分支的说明，帮助AI理解何时选择此分支"
                            ></el-input>
                          </el-form-item>
                          <el-button 
                            type="danger" 
                            size="small" 
                            circle 
                            @click="removeLlmBranch(index)"
                            style="margin-top: 22px;"
                            :disabled="index === (selectedNodeConfig.branches || []).length - 1"
                            :title="index === (selectedNodeConfig.branches || []).length - 1 ? '最后一个分支是默认分支，不能删除' : '删除分支'"
                          >
                            <el-icon><Delete /></el-icon>
                          </el-button>
                        </div>
                      </el-form>
                    </div>
                    <el-button 
                      type="primary" 
                      size="small" 
                      @click="addLlmBranch"
                      style="margin-top: 10px;"
                    >
                      <el-icon><Plus /></el-icon>
                      添加分支
                    </el-button>
                    <div v-if="!selectedNodeConfig.branches || selectedNodeConfig.branches.length === 0" class="no-branches">
                      <p>暂无分支，点击添加按钮创建</p>
                    </div>
                  </div>
                  <el-divider>默认分支</el-divider>
                  <div class="el-form-item__help" style="margin-bottom: 10px;">
                    <p>最后一个分支将自动作为默认分支。如果AI无法从上述分支中选择，将使用最后一个分支作为默认分支。新添加的分支会插入到最后一个分支之前。</p>
                  </div>
                </template>
                
                <!-- 基础分支节点配置 -->
                <template v-if="selectedNode.type === 'basic_branch'">
                  <el-form-item label="分支类型">
                    <el-select v-model="selectedNodeConfig.branchType" @change="updateNodeConfig">
                      <el-option label="条件分支" value="conditional"></el-option>
                      <el-option label="默认分支" value="default"></el-option>
                    </el-select>
                  </el-form-item>
                  <el-form-item label="默认分支">
                    <el-input v-model="selectedNodeConfig.defaultBranch" @input="updateNodeConfig"></el-input>
                  </el-form-item>
                  <el-form-item label="输出端口数量">
                    <el-input-number 
                      v-model="outputPortCount" 
                      :min="1" 
                      :max="10"
                      @change="updateOutputPortCount"
                    ></el-input-number>
                  </el-form-item>
                  <!-- 分支条件表达式编辑 -->
                  <el-divider>分支条件表达式</el-divider>
                  <div 
                    v-for="(port, index) in getOutputPorts(selectedNode)" 
                    :key="`condition-edit-${index}`"
                    class="branch-condition-edit"
                  >
                    <!-- 最后一个分支是默认分支，无需输入条件 -->
                    <el-form-item 
                      v-if="index === getOutputPorts(selectedNode).length - 1"
                      :label="`分支${index + 1}（默认分支）`"
                    >
                      <el-input 
                        value="默认分支（无需条件）"
                        disabled
                        placeholder="默认分支，无需输入条件"
                      ></el-input>
                    </el-form-item>
                    <el-form-item 
                      v-else
                      :label="`分支${index + 1}条件`"
                    >
                      <el-input 
                        v-model="branchConditions[index]" 
                        @input="updateBranchCondition(index)"
                        placeholder="例如：${answer} > 100"
                      ></el-input>
                    </el-form-item>
                  </div>
                </template>
                
                <!-- 赋值节点配置 -->
                <template v-if="selectedNode.type === 'assign'">
                  <el-divider>赋值语句列表</el-divider>
                  <div class="assign-statements-list">
                    <div 
                      v-for="(assignment, index) in (selectedNodeConfig.assignments || [])" 
                      :key="index" 
                      class="assign-statement-item"
                    >
                      <el-form label-position="top" size="small">
                        <div class="assign-statement-row">
                          <el-form-item label="变量名" :label-width="60">
                            <el-input 
                              v-model="assignment.variableName" 
                              @input="updateNodeConfig"
                              placeholder="例如：result"
                            ></el-input>
                          </el-form-item>
                          <el-form-item label="值表达式" style="flex: 1;">
                            <el-input 
                              v-model="assignment.valueExpression" 
                              @input="updateNodeConfig"
                              placeholder="例如：1+1 或 ${var1} * 2"
                            ></el-input>
                          </el-form-item>
                          <el-button 
                            type="danger" 
                            size="small" 
                            circle 
                            @click="removeAssignStatement(index)"
                            style="margin-top: 22px;"
                          >
                            <el-icon><Delete /></el-icon>
                          </el-button>
                        </div>
                      </el-form>
                    </div>
                    <el-button 
                      type="primary" 
                      size="small" 
                      @click="addAssignStatement"
                      style="margin-top: 10px;"
                    >
                      <el-icon><Plus /></el-icon>
                      添加赋值语句
                    </el-button>
                    <div v-if="!selectedNodeConfig.assignments || selectedNodeConfig.assignments.length === 0" class="no-variables">
                      <p>暂无赋值语句，点击添加按钮创建</p>
                    </div>
                  </div>
                </template>
                
                <!-- 用户输入节点配置 -->
                <template v-if="selectedNode.type === 'user_input'">
                  <el-form-item label="提示消息">
                    <el-input 
                      v-model="selectedNodeConfig.prompt" 
                      type="textarea" 
                      :rows="4" 
                      @input="updateNodeConfig"
                      placeholder="请输入提示消息，例如：请输入您的姓名："
                    ></el-input>
                    <div class="el-form-item__help">支持使用${变量名}来引用上下文变量</div>
                  </el-form-item>
                  <el-form-item label="输出变量名">
                    <el-input 
                      v-model="selectedNodeConfig.outputVariable" 
                      @input="updateNodeConfig"
                      placeholder="例如：userInput"
                    ></el-input>
                    <div class="el-form-item__help">用户输入将保存到此变量中</div>
                  </el-form-item>
                  <el-divider>历史对话设置</el-divider>
                  <el-form-item label="历史对话key">
                    <el-input 
                      v-model="selectedNodeConfig.historyKey" 
                      @input="updateNodeConfig"
                      placeholder="默认为 default"
                    ></el-input>
                    <div class="el-form-item__help">用户输入将存储到此key的历史会话中</div>
                  </el-form-item>
                  <el-form-item label="保存至历史对话">
                    <el-switch 
                      v-model="selectedNodeConfig.saveToHistory" 
                      @change="updateNodeConfig"
                    ></el-switch>
                    <div class="el-form-item__help">是否将用户输入保存到历史对话中（格式：【用户】说：内容）</div>
                  </el-form-item>
                  <el-form-item label="保存历史key（可选）">
                    <el-input 
                      v-model="selectedNodeConfig.saveHistoryKey" 
                      @input="updateNodeConfig"
                      placeholder="留空则使用上面的历史对话key"
                    ></el-input>
                    <div class="el-form-item__help">如果设置，用户输入将保存到此key，而不是上面的历史对话key</div>
                  </el-form-item>
                  <el-form-item label="历史对话昵称">
                    <el-input 
                      v-model="selectedNodeConfig.historyNickname" 
                      @input="updateNodeConfig"
                      placeholder="默认为 用户"
                    ></el-input>
                    <div class="el-form-item__help">用于历史对话的昵称，例如：用户/访客/学员</div>
                  </el-form-item>
                </template>
              </el-form>
            </div>
            <div v-else class="no-selection">
              <p>请选择一个节点</p>
            </div>
          </el-tab-pane>
          
          <!-- 全局变量标签 -->
          <el-tab-pane label="全局变量" name="variables">
            <div class="global-variables">
              <div class="variables-header">
                <h3>全局变量</h3>
                <el-button type="primary" size="small" @click="addGlobalVariable">添加变量</el-button>
              </div>
              <div class="variables-list">
                <div v-for="(variable, index) in globalVariables" :key="index" class="variable-item">
                  <el-form label-position="top" size="mini">
                    <div class="variable-row">
                      <el-form-item label="变量名" :label-width="60">
                        <el-input v-model="variable.name" @input="updateGlobalVariables"></el-input>
                      </el-form-item>
                      <el-form-item label="类型" :label-width="40">
                        <el-select v-model="variable.type" @change="updateGlobalVariables" style="width: 100px;">
                          <el-option label="字符串" value="string"></el-option>
                          <el-option label="整数" value="integer"></el-option>
                          <el-option label="浮点数" value="double"></el-option>
                        </el-select>
                      </el-form-item>
                      <el-form-item label="初始值" :label-width="60">
                        <el-input v-model="variable.initialValue" @input="updateGlobalVariables"></el-input>
                      </el-form-item>
                      <el-button 
                        type="danger" 
                        size="mini" 
                        circle 
                        @click="deleteGlobalVariable(index)"
                        style="margin-top: 22px;"
                      >
                        <el-icon><Delete /></el-icon>
                      </el-button>
                    </div>
                  </el-form>
                </div>
                <div v-if="globalVariables.length === 0" class="no-variables">
                  <p>暂无全局变量，点击添加按钮创建</p>
                </div>
              </div>
            </div>
          </el-tab-pane>
          
        </el-tabs>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Delete, Plus, Close } from '@element-plus/icons-vue'

// Props
const props = defineProps({
  workflow: {
    type: Object,
    default: () => ({})
  }
})

// Emits
const emit = defineEmits(['save', 'run'])

// 节点类型定义
const nodeTypes = [
  { type: 'start', label: '开始节点', icon: '▶️' },
  { type: 'end', label: '结束节点', icon: '⏹️' },
  { type: 'llm_call', label: '大模型调用', icon: '🤖' },
  { type: 'llm_assign', label: '大模型赋值', icon: '📝' },
  { type: 'parallel', label: '并行节点', icon: '🔀' },
  { type: 'basic_branch', label: '基础分支', icon: '🌿' },
  { type: 'llm_branch', label: '大模型分支', icon: '🌲' },
  { type: 'assign', label: '赋值节点', icon: '📝' },
  { type: 'workflow_call', label: '工作流调用', icon: '🔗' },
  { type: 'http_call', label: 'HTTP调用', icon: '🌐' },
  { type: 'user_input', label: '用户输入', icon: '👤' },
  { type: 'info_output', label: '信息输出', icon: '💬' }
]

// 常量
const nodeWidth = 180
const nodeHeight = 80
const portSize = 12
const portSpacing = 30
const canvasWidth = 5000
const canvasHeight = 5000

// 状态管理
const nodes = ref([])
const edges = ref([])
const selectedNode = ref(null)
const selectedEdge = ref(null)
const activeTab = ref('node')
const zoomLevel = ref(1)
const isConnecting = ref(false)
const connectingFrom = ref(null)
const connectingPortIndex = ref(0)
const mousePosition = ref({ x: 0, y: 0 })
const canvasContainer = ref(null)
const connectionsLayer = ref(null)
const selectedNodeConfig = ref({})
const globalVariables = ref([])
const outputPortCount = ref(2)
const branchConditions = ref([]) // 分支节点的条件表达式数组

// 工作流基本信息状态
const workflowName = ref(props.workflow.name || '')
const workflowDescription = ref(props.workflow.description || '')
const workflowVersion = ref(props.workflow.version || '1.0.0')

// 拖拽状态
const isDraggingNode = ref(false)
const dragNode = ref(null)
const dragOffset = ref({ x: 0, y: 0 })

// 画布拖拽状态
const isDraggingCanvas = ref(false)
const canvasDragStart = ref({ x: 0, y: 0 })
const canvasOffset = ref({ x: 0, y: 0 })

// 初始化标志，用于避免保存后清空界面
const isInitialized = ref(false)

// 计算属性
const getNodeTypeLabel = (type) => {
  const nodeType = nodeTypes.find(nt => nt.type === type)
  return nodeType ? nodeType.label : type
}

const getNodeTypeIcon = (type) => {
  const nodeType = nodeTypes.find(nt => nt.type === type)
  return nodeType ? nodeType.icon : '📦'
}

const isBranchNode = (type) => {
  return type === 'basic_branch' || type === 'llm_branch'
}

// 获取节点的输入端口列表
const getInputPorts = (node) => {
  // 所有节点只有一个输入端口（除了start节点）
  if (node.type === 'start') return []
  return [{}]
}

// 获取节点的输出端口列表
const getOutputPorts = (node) => {
  // 结束节点没有输出端口
  if (node.type === 'end') return []
  
  // 分支节点可以有多个输出端口
  if (isBranchNode(node.type)) {
    if (!node.config) node.config = {}
    if (!node.config.outputPorts) {
      node.config.outputPorts = [{}, {}] // 默认2个输出端口
    }
    return node.config.outputPorts
  }
  
  // 其他节点只有一个输出端口
  return [{}]
}

// 获取输入端口Y坐标
const getInputPortY = (node, index) => {
  return nodeHeight / 2
}

// 获取分支节点高度
const getBranchNodeHeight = (node) => {
  if (!isBranchNode(node.type)) return nodeHeight
  
  const outputPorts = getOutputPorts(node)
  const headerHeight = 60 // 节点头部高度
  const nodeTypeHeight = 30 // node-type 区域高度（padding 8px + 文字高度约14px）
  const conditionItemHeight = 32 // 每个条件表达式项的高度
  const padding = 8 // 上下内边距
  const minHeight = 100 // 最小高度
  
  // 计算总高度：头部 + node-type + 条件表达式区域 + 内边距
  const calculatedHeight = headerHeight + nodeTypeHeight + (outputPorts.length * conditionItemHeight) + padding
  
  return Math.max(minHeight, calculatedHeight)
}

// 获取输出端口Y坐标（相对于节点顶部）
const getOutputPortY = (node, index) => {
  const outputPorts = getOutputPorts(node)
  if (outputPorts.length === 1) {
    const nodeH = isBranchNode(node.type) ? getBranchNodeHeight(node) : nodeHeight
    return nodeH / 2
  }
  
  if (isBranchNode(node.type)) {
    // 分支节点：端口与条件表达式对齐
    const headerHeight = 60 // 节点头部高度
    const nodeTypeHeight = 30 // node-type 区域高度
    const conditionItemHeight = 32 // 每个条件表达式项的高度
    // 第一个条件表达式的顶部位置，减去2.5行的偏移（80px）以修正对齐
    const firstConditionTop = headerHeight + nodeTypeHeight - 80
    
    // 计算每个端口的Y坐标，使其与对应的条件表达式中心对齐
    // 条件表达式项的中心 = 顶部 + 高度/2，端口圆圈中心应该对齐到这里
    // 往下移动2.5行（85px）以对齐表达式
    return firstConditionTop + (index * conditionItemHeight) + (conditionItemHeight / 2) - 12 + 85 // 12是端口圆圈的半径，+85是往下移动2.5行
  } else {
    // 普通节点：均匀分布
    const nodeH = nodeHeight
    const totalHeight = (outputPorts.length - 1) * portSpacing
    const startY = (nodeH - totalHeight) / 2
    return startY + index * portSpacing
  }
}

// 获取条件表达式项的Y坐标（用于对齐，相对于node-content顶部）
const getConditionItemY = (node, index) => {
  if (!isBranchNode(node.type)) return 0
  
  const headerHeight = 60 // 节点头部高度（padding 12px + 内容约36px）
  const nodeTypeHeight = 30 // node-type 区域高度（padding 8px + 文字约14px）
  const conditionItemHeight = 32 // 每个条件表达式项的高度
  // 第一个条件表达式的顶部位置 = 头部 + node-type，减去2.5行的偏移（80px）
  const firstConditionTop = headerHeight + nodeTypeHeight - 80
  
  return firstConditionTop + (index * conditionItemHeight)
}

// 获取添加端口按钮Y坐标
const getAddPortY = (node) => {
  const outputPorts = getOutputPorts(node)
  const lastPortY = getOutputPortY(node, outputPorts.length - 1)
  return lastPortY + portSpacing
}

// 添加输出端口（分支节点）
const addOutputPort = (node) => {
  if (!isBranchNode(node.type)) return
  
  if (!node.config) node.config = {}
  if (!node.config.outputPorts) node.config.outputPorts = [{}]
  
  // 大模型分支节点：新分支插入到最后一个分支之前
  if (node.type === 'llm_branch') {
    if (!node.config.branches) node.config.branches = []
    const branches = node.config.branches
    const outputPorts = node.config.outputPorts
    
    // 插入到最后一个位置之前
    const insertIndex = branches.length > 0 ? branches.length - 1 : 0
    branches.splice(insertIndex, 0, {
      name: `分支${branches.length + 1}`,
      description: ''
    })
    
    // 同步添加输出端口（也插入到最后一个端口之前）
    const portInsertIndex = outputPorts.length > 0 ? outputPorts.length - 1 : 0
    outputPorts.splice(portInsertIndex, 0, {})
    
    // 如果当前选中的是这个节点，同步更新 selectedNodeConfig
    if (selectedNode.value && selectedNode.value.nodeKey === node.nodeKey) {
      selectedNodeConfig.value = { ...node.config }
    }
  } else if (node.type === 'basic_branch') {
    // 基础分支节点：新分支插入到最后一个端口之前（最后一个端口是默认分支）
    const outputPorts = node.config.outputPorts
    const portInsertIndex = outputPorts.length > 0 ? outputPorts.length - 1 : 0
    outputPorts.splice(portInsertIndex, 0, {})
    
    // 初始化条件表达式数组（如果不存在）
    if (!node.config.conditions) node.config.conditions = []
    // 在对应位置插入空条件表达式
    node.config.conditions.splice(portInsertIndex, 0, '')
    
    // 如果当前选中的是这个节点，同步更新 selectedNodeConfig 和 branchConditions
    if (selectedNode.value && selectedNode.value.nodeKey === node.nodeKey) {
      selectedNodeConfig.value = { ...node.config }
      // 更新 branchConditions，在对应位置插入空字符串
      branchConditions.value.splice(portInsertIndex, 0, '')
    }
  } else {
    // 其他分支节点：直接添加到最后
    node.config.outputPorts.push({})
  }
  
  ElMessage.success('已添加输出端口')
}

// 删除输出端口（分支节点）
const removeOutputPort = (node, index) => {
  if (!isBranchNode(node.type)) return
  
  const outputPorts = getOutputPorts(node)
  if (outputPorts.length <= 1) {
    ElMessage.warning('至少需要保留一个输出端口')
    return
  }
  
  // 分支节点：不允许删除最后一个端口（默认分支）
  if (isBranchNode(node.type)) {
    const lastIndex = outputPorts.length - 1
    if (index === lastIndex) {
      ElMessage.warning('最后一个端口是默认分支，不能删除')
      return
    }
  }
  
  // 大模型分支节点：同步删除分支列表中的对应分支
  if (node.type === 'llm_branch' && node.config.branches) {
    if (index < node.config.branches.length) {
      node.config.branches.splice(index, 1)
    }
    
    // 如果当前选中的是这个节点，同步更新 selectedNodeConfig
    if (selectedNode.value && selectedNode.value.nodeKey === node.nodeKey) {
      selectedNodeConfig.value = { ...node.config }
    }
  }
  
  // 基础分支节点：同步删除条件表达式
  if (node.type === 'basic_branch' && node.config.conditions) {
    if (index < node.config.conditions.length) {
      node.config.conditions.splice(index, 1)
    }
    
    // 如果当前选中的是这个节点，同步更新 selectedNodeConfig 和 branchConditions
    if (selectedNode.value && selectedNode.value.nodeKey === node.nodeKey) {
      selectedNodeConfig.value = { ...node.config }
      branchConditions.value.splice(index, 1)
    }
  }
  
  // 删除端口
  outputPorts.splice(index, 1)
    
    // 删除相关的连接线
    edges.value = edges.value.filter(edge => {
    if (edge.fromNodeKey === node.nodeKey) {
      // 如果删除的端口索引小于等于当前连接的端口索引，需要调整索引
      if (edge.fromPortIndex === index) {
        return false // 删除连接到这个端口的连接线
      } else if (edge.fromPortIndex > index) {
        edge.fromPortIndex-- // 调整后续端口的索引
      }
    }
    return true
  })
  
  ElMessage.success('已删除输出端口')
}

// 更新输出端口数量
const updateOutputPortCount = (count) => {
  if (!selectedNode.value || !isBranchNode(selectedNode.value.type)) return
  
  if (!selectedNode.value.config) selectedNode.value.config = {}
  if (!selectedNode.value.config.outputPorts) selectedNode.value.config.outputPorts = [{}]
  
  const currentCount = selectedNode.value.config.outputPorts.length
  if (count > currentCount) {
    // 增加端口
    for (let i = currentCount; i < count; i++) {
      selectedNode.value.config.outputPorts.push({})
    }
  } else if (count < currentCount) {
    // 减少端口，删除多余的连接线
    const portsToRemove = currentCount - count
    for (let i = 0; i < portsToRemove; i++) {
      const removeIndex = currentCount - 1 - i
      // 删除连接到这个端口的连接线
      edges.value = edges.value.filter(edge => {
        if (edge.fromNodeKey === selectedNode.value.nodeKey && edge.fromPortIndex === removeIndex) {
          return false
        } else if (edge.fromNodeKey === selectedNode.value.nodeKey && edge.fromPortIndex > removeIndex) {
          edge.fromPortIndex--
        }
        return true
      })
    }
    selectedNode.value.config.outputPorts = selectedNode.value.config.outputPorts.slice(0, count)
  }
  
  updateNodeConfig()
}

// 获取端口的世界坐标（连接线的起点/终点）
const getPortWorldPosition = (node, portType, portIndex) => {
  const nodeX = node.positionX
  const nodeY = node.positionY
  
  let portX, portY
  
  // 获取端口中心Y坐标
  const portCenterY = portType === 'input' 
    ? getInputPortY(node, portIndex)
    : getOutputPortY(node, portIndex)
  
  if (portType === 'input') {
    // 输入端口：连接线从节点左边缘开始（端口在左侧，圆圈中心在节点边缘）
    // 输入端口也需要向下偏移13px以对齐
    portX = nodeX
    portY = nodeY + portCenterY + 13
  } else {
    // 输出端口：连接线从节点右边缘开始（端口在右侧，圆圈中心在节点边缘）
    // 我人工把偏移量改成了13  这样对于所有节点正好对齐
    const connectionOffset = 13
    portX = nodeX + nodeWidth
    portY = nodeY + portCenterY + connectionOffset
  }
  
  return { x: portX, y: portY }
}

// 判断是否是反向连接（出口在入口右侧，即从右往左的连接）
const isReverseConnection = (edge) => {
  const fromNode = nodes.value.find(n => n.nodeKey === edge.fromNodeKey)
  const toNode = nodes.value.find(n => n.nodeKey === edge.toNodeKey)
  
  if (!fromNode || !toNode) return false
  
  const fromPos = getPortWorldPosition(fromNode, 'output', edge.fromPortIndex || 0)
  const toPos = getPortWorldPosition(toNode, 'input', edge.toPortIndex || 0)
  
  // 如果出口（fromPos）在入口（toPos）的右侧，则是反向连接（从右往左）
  return fromPos.x > toPos.x
}

// 获取连接线的路径（使用贝塞尔曲线或直线）
const getEdgePath = (edge) => {
  const fromNode = nodes.value.find(n => n.nodeKey === edge.fromNodeKey)
  const toNode = nodes.value.find(n => n.nodeKey === edge.toNodeKey)
  
  if (!fromNode || !toNode) return ''
  
  const fromPos = getPortWorldPosition(fromNode, 'output', edge.fromPortIndex || 0)
  const toPos = getPortWorldPosition(toNode, 'input', edge.toPortIndex || 0)
  
  const x1 = fromPos.x
  const y1 = fromPos.y
  const x2 = toPos.x
  const y2 = toPos.y
  
  // 计算直线距离
  const dx = x2 - x1
  const dy = y2 - y1
  const distance = Math.sqrt(dx * dx + dy * dy)
  
  // 如果距离小于100px，直接使用直线
  if (distance < 100) {
    return `M ${x1} ${y1} L ${x2} ${y2}`
  }
  
  // 距离大于等于100px，使用贝塞尔曲线
  const controlOffset = Math.max(100, Math.abs(dx) * 0.5)
  const cx1 = x1 + controlOffset
  const cy1 = y1
  const cx2 = x2 - controlOffset
  const cy2 = y2
  
  return `M ${x1} ${y1} C ${cx1} ${cy1}, ${cx2} ${cy2}, ${x2} ${y2}`
}

// 获取预览路径
const getPreviewPath = () => {
  if (!isConnecting.value || !connectingFrom.value) return ''
  
  const fromNode = nodes.value.find(n => n.nodeKey === connectingFrom.value)
  if (!fromNode) return ''
  
  const fromPos = getPortWorldPosition(fromNode, 'output', connectingPortIndex.value)
  const toPos = mousePosition.value
  
  const x1 = fromPos.x
  const y1 = fromPos.y
  const x2 = toPos.x
  const y2 = toPos.y
  
  // 计算直线距离
  const dx = x2 - x1
  const dy = y2 - y1
  const distance = Math.sqrt(dx * dx + dy * dy)
  
  // 如果距离小于100px，直接使用直线
  if (distance < 100) {
    return `M ${x1} ${y1} L ${x2} ${y2}`
  }
  
  // 距离大于等于100px，使用贝塞尔曲线
  const controlOffset = Math.max(100, Math.abs(dx) * 0.5)
  const cx1 = x1 + controlOffset
  const cy1 = y1
  const cx2 = x2 - controlOffset
  const cy2 = y2
  
  return `M ${x1} ${y1} C ${cx1} ${cy1}, ${cx2} ${cy2}, ${x2} ${y2}`
}

// 获取连接线中点坐标
const getEdgeMidX = (edge) => {
  const fromNode = nodes.value.find(n => n.nodeKey === edge.fromNodeKey)
  const toNode = nodes.value.find(n => n.nodeKey === edge.toNodeKey)
  if (!fromNode || !toNode) return 0
  
  const fromPos = getPortWorldPosition(fromNode, 'output', edge.fromPortIndex || 0)
  const toPos = getPortWorldPosition(toNode, 'input', edge.toPortIndex || 0)
  return (fromPos.x + toPos.x) / 2
}

const getEdgeMidY = (edge) => {
  const fromNode = nodes.value.find(n => n.nodeKey === edge.fromNodeKey)
  const toNode = nodes.value.find(n => n.nodeKey === edge.toNodeKey)
  if (!fromNode || !toNode) return 0
  
  const fromPos = getPortWorldPosition(fromNode, 'output', edge.fromPortIndex || 0)
  const toPos = getPortWorldPosition(toNode, 'input', edge.toPortIndex || 0)
  return (fromPos.y + toPos.y) / 2
}

// 拖拽事件处理
const onDragStart = (event, nodeType) => {
  event.dataTransfer.setData('application/json', JSON.stringify(nodeType))
}

const onDragOver = (event) => {
  event.preventDefault()
}

const onDrop = (event) => {
  event.preventDefault()
  const nodeTypeData = event.dataTransfer.getData('application/json')
  if (nodeTypeData) {
    const nodeType = JSON.parse(nodeTypeData)
    const canvasRect = canvasContainer.value.getBoundingClientRect()
    const x = (event.clientX - canvasRect.left) / zoomLevel.value - nodeWidth / 2
    const y = (event.clientY - canvasRect.top) / zoomLevel.value - nodeHeight / 2
    
    // 创建新节点
    const newNode = {
      nodeKey: `${nodeType.type}_${Date.now()}`,
      name: `${nodeType.label} ${nodes.value.length + 1}`,
      type: nodeType.type,
      positionX: Math.max(0, x),
      positionY: Math.max(0, y),
      config: {}
    }
    
    // 分支节点默认配置
    if (isBranchNode(nodeType.type)) {
      newNode.config.outputPorts = [{}, {}]
      if (nodeType.type === 'llm_branch') {
        // 大模型分支节点：初始化分支列表
        newNode.config.branches = []
        newNode.config.defaultBranch = ''
      } else {
        // 基础分支节点：使用条件表达式
        newNode.config.branchType = 'conditional'
        newNode.config.defaultBranch = ''
      }
    }
    
    // 赋值节点默认配置
    if (nodeType.type === 'assign') {
      newNode.config.assignments = []
    }
    
    // 用户输入节点默认配置
    if (nodeType.type === 'user_input') {
      newNode.config.prompt = '请输入您的消息：'
      newNode.config.outputVariable = 'userInput'
      newNode.config.historyKey = 'default'
      newNode.config.saveToHistory = true
      newNode.config.historyNickname = '用户'
    }
    
    // 信息输出节点默认配置
    if (nodeType.type === 'info_output') {
      newNode.config.prompt = '这里是信息输出节点的默认内容'
      newNode.config.historyKey = 'default'
      newNode.config.saveToHistory = false
      newNode.config.historyNickname = '系统'
    }
    
    nodes.value.push(newNode)
  }
}

// 获取分支节点的条件表达式
const getBranchCondition = (node, portIndex) => {
  if (!isBranchNode(node.type)) return ''
  
  // 大模型分支节点：显示分支名称
  if (node.type === 'llm_branch') {
    const edge = edges.value.find(e => 
      e.fromNodeKey === node.nodeKey && 
      e.fromPortIndex === portIndex
    )
    
    if (edge && edge.conditionExpression) {
      return edge.conditionExpression
    }
    
    // 如果连接线没有条件表达式，尝试从分支列表中获取
    if (node.config && node.config.branches && node.config.branches[portIndex]) {
      return node.config.branches[portIndex].name || '未设置'
    }
    
    return '未设置'
  }
  
  // 基础分支节点：显示条件表达式
  const outputPorts = getOutputPorts(node)
  // 最后一个分支是默认分支
  if (portIndex === outputPorts.length - 1) {
    return '默认分支'
  }
  
  const edge = edges.value.find(e => 
    e.fromNodeKey === node.nodeKey && 
    e.fromPortIndex === portIndex
  )
  
  if (edge && edge.conditionExpression) {
    return edge.conditionExpression
  }
  
  // 从节点配置中获取
  if (node.config && node.config.conditions && node.config.conditions[portIndex]) {
    return node.config.conditions[portIndex]
  }
  
  return '未设置'
}

// 更新分支条件表达式
const updateBranchCondition = (portIndex) => {
  if (!selectedNode.value || !isBranchNode(selectedNode.value.type)) return
  
  // 基础分支节点的最后一个分支是默认分支，无需更新条件表达式
  if (selectedNode.value.type === 'basic_branch') {
    const outputPorts = getOutputPorts(selectedNode.value)
    if (portIndex === outputPorts.length - 1) {
      // 最后一个分支是默认分支，不更新条件表达式
      return
    }
  }
  
  // 更新节点配置
  if (!selectedNode.value.config) selectedNode.value.config = {}
  if (!selectedNode.value.config.conditions) selectedNode.value.config.conditions = []
  selectedNode.value.config.conditions[portIndex] = branchConditions.value[portIndex] || ''
  
  // 更新对应的连接线
  const edge = edges.value.find(e => 
    e.fromNodeKey === selectedNode.value.nodeKey && 
    e.fromPortIndex === portIndex
  )
  
  if (edge) {
    edge.conditionExpression = branchConditions.value[portIndex] || ''
  }
  
  updateNodeConfig()
}

// 节点选择
const selectNode = (node) => {
  selectedNode.value = node
  selectedEdge.value = null
  selectedNodeConfig.value = { ...node.config } || {}
  activeTab.value = 'node'
  
  // 初始化大模型赋值节点的变量列表
  if (node.type === 'llm_assign' && !selectedNodeConfig.value.assignVariables) {
    selectedNodeConfig.value.assignVariables = []
  }
  
  // 初始化赋值节点的赋值语句列表
  if (node.type === 'assign' && !selectedNodeConfig.value.assignments) {
    selectedNodeConfig.value.assignments = []
  }
  
  // 初始化大模型分支节点的分支列表，确保与输出端口同步
  if (node.type === 'llm_branch') {
    if (!selectedNodeConfig.value.branches) {
      selectedNodeConfig.value.branches = []
    }
    
    // 确保分支数量与输出端口数量一致
    const outputPorts = getOutputPorts(node)
    const branches = selectedNodeConfig.value.branches
    
    // 如果输出端口数量大于分支数量，补充分支
    while (branches.length < outputPorts.length) {
      branches.push({
        name: `分支${branches.length + 1}`,
        description: ''
      })
    }
    
    // 如果分支数量大于输出端口数量，补充输出端口
    if (branches.length > outputPorts.length) {
      if (!node.config) node.config = {}
      if (!node.config.outputPorts) node.config.outputPorts = [{}]
      while (node.config.outputPorts.length < branches.length) {
        node.config.outputPorts.push({})
      }
    }
    
    // 更新 selectedNodeConfig
    selectedNodeConfig.value = { ...node.config }
  }
  
  if (isBranchNode(node.type)) {
    outputPortCount.value = getOutputPorts(node).length
    // 加载分支条件表达式
    const outputPorts = getOutputPorts(node)
    branchConditions.value = outputPorts.map((port, index) => {
      // 基础分支节点的最后一个分支是默认分支，无需条件表达式
      if (node.type === 'basic_branch' && index === outputPorts.length - 1) {
        return '' // 默认分支，条件表达式为空
      }
      
      const edge = edges.value.find(e => 
        e.fromNodeKey === node.nodeKey && 
        e.fromPortIndex === index
      )
      if (edge && edge.conditionExpression) {
        return edge.conditionExpression
      }
      if (node.config && node.config.conditions && node.config.conditions[index]) {
        return node.config.conditions[index]
      }
      return ''
    })
  } else {
    branchConditions.value = []
  }
}

// 节点更新
const updateNode = () => {
  // 节点基本信息已通过v-model双向绑定
}

const updateNodeConfig = () => {
  if (selectedNode.value) {
    selectedNode.value.config = { ...selectedNodeConfig.value }
  }
}

// 添加赋值语句
const addAssignStatement = () => {
  if (!selectedNode.value || selectedNode.value.type !== 'assign') return
  
  if (!selectedNodeConfig.value.assignments) {
    selectedNodeConfig.value.assignments = []
  }
  
  selectedNodeConfig.value.assignments.push({
    variableName: '',
    valueExpression: ''
  })
  
  updateNodeConfig()
}

// 删除赋值语句
const removeAssignStatement = (index) => {
  if (!selectedNode.value || selectedNode.value.type !== 'assign') return
  
  if (selectedNodeConfig.value.assignments) {
    selectedNodeConfig.value.assignments.splice(index, 1)
    updateNodeConfig()
  }
}

// 删除节点
const deleteNode = (node) => {
  // 删除节点
  nodes.value = nodes.value.filter(n => n.nodeKey !== node.nodeKey)
  // 删除相关边
  edges.value = edges.value.filter(e => e.fromNodeKey !== node.nodeKey && e.toNodeKey !== node.nodeKey)
  // 取消选择
  if (selectedNode.value?.nodeKey === node.nodeKey) {
    selectedNode.value = null
    selectedNodeConfig.value = {}
  }
  ElMessage.success('节点已删除')
}

// 节点拖拽
const onNodeMouseDown = (event, node) => {
  if (isConnecting.value) return
  
  event.stopPropagation()
  isDraggingNode.value = true
  dragNode.value = node
  
  const canvasRect = canvasContainer.value.getBoundingClientRect()
  const nodeRect = event.currentTarget.getBoundingClientRect()
  // 考虑画布偏移和缩放
  dragOffset.value = {
    x: (event.clientX - canvasRect.left - canvasOffset.value.x) / zoomLevel.value - node.positionX,
    y: (event.clientY - canvasRect.top - canvasOffset.value.y) / zoomLevel.value - node.positionY
  }
  
  selectNode(node)
}

// 画布鼠标事件
const onCanvasMouseDown = (event) => {
  // 如果点击的是画布空白区域（不是节点、端口等），开始拖拽
  const target = event.target
  const isCanvasElement = target === canvasContainer.value || 
                          target.classList.contains('canvas') ||
                          target.classList.contains('grid-background') ||
                          target.classList.contains('connections-layer') ||
                          target.tagName === 'svg' ||
                          target.tagName === 'path' && !target.closest('.workflow-node')
  
  if (isCanvasElement && event.button === 0) { // 左键
    // 如果点击的是连接线，选择连接线而不是拖拽
    if (target.tagName === 'path') {
    return
  }
  
    selectedNode.value = null
    selectedEdge.value = null
    if (isConnecting.value) {
      cancelConnection()
    }
    
    // 开始画布拖拽
    isDraggingCanvas.value = true
    canvasDragStart.value = {
      x: event.clientX - canvasOffset.value.x,
      y: event.clientY - canvasOffset.value.y
    }
    event.preventDefault()
  }
}

const onCanvasMouseMove = (event) => {
  if (!canvasContainer.value) return
  
  // 处理画布拖拽（需要在全局范围内工作）
  if (isDraggingCanvas.value) {
    canvasOffset.value = {
      x: event.clientX - canvasDragStart.value.x,
      y: event.clientY - canvasDragStart.value.y
    }
    event.preventDefault()
    return
  }
  
  const canvasRect = canvasContainer.value.getBoundingClientRect()
  // 考虑画布偏移和缩放
  const newMousePos = {
    x: (event.clientX - canvasRect.left - canvasOffset.value.x) / zoomLevel.value,
    y: (event.clientY - canvasRect.top - canvasOffset.value.y) / zoomLevel.value
  }
  
  mousePosition.value = newMousePos
  
  // 处理节点拖拽 - 使用requestAnimationFrame确保同步更新
  if (isDraggingNode.value && dragNode.value) {
    dragNode.value.positionX = Math.max(0, newMousePos.x - dragOffset.value.x)
    dragNode.value.positionY = Math.max(0, newMousePos.y - dragOffset.value.y)
    // 强制触发响应式更新，确保连接线实时跟随
    nextTick(() => {
      // 连接线路径会在getEdgePath中自动重新计算
    })
  }
}

const onCanvasMouseUp = (event) => {
  isDraggingNode.value = false
  dragNode.value = null
  if (isDraggingCanvas.value) {
    isDraggingCanvas.value = false
  }
}

// 全局鼠标事件处理（用于画布拖拽）
const onGlobalMouseMove = (event) => {
  if (isDraggingCanvas.value) {
    canvasOffset.value = {
      x: event.clientX - canvasDragStart.value.x,
      y: event.clientY - canvasDragStart.value.y
    }
    event.preventDefault()
  }
}

const onGlobalMouseUp = (event) => {
  if (isDraggingCanvas.value) {
    isDraggingCanvas.value = false
  }
}

// 画布滚轮缩放
const onCanvasWheel = (event) => {
  if (!canvasContainer.value) return
  
  event.preventDefault()
  
  // 计算缩放中心点（鼠标位置）
  const canvasRect = canvasContainer.value.getBoundingClientRect()
  const mouseX = event.clientX - canvasRect.left
  const mouseY = event.clientY - canvasRect.top
  
  // 计算缩放前的鼠标在画布中的位置
  const beforeZoomX = (mouseX - canvasOffset.value.x) / zoomLevel.value
  const beforeZoomY = (mouseY - canvasOffset.value.y) / zoomLevel.value
  
  // 调整缩放级别
  const delta = event.deltaY > 0 ? -0.1 : 0.1
  const newZoom = Math.max(0.5, Math.min(2, zoomLevel.value + delta))
  zoomLevel.value = newZoom
  
  // 计算缩放后的鼠标在画布中的位置
  const afterZoomX = beforeZoomX * newZoom
  const afterZoomY = beforeZoomY * newZoom
  
  // 调整画布偏移，使鼠标位置保持不变
  canvasOffset.value = {
    x: mouseX - afterZoomX,
    y: mouseY - afterZoomY
  }
}

// 端口点击事件
const onPortClick = (event, node, portType, portIndex = 0) => {
  event.stopPropagation()
  
  if (isConnecting.value) {
    // 正在连接状态，只能连接到输入端口
    if (portType === 'input' && connectingFrom.value !== node.nodeKey) {
      // 检查输出端口是否已经有连接（每个输出端口只能连一条线）
      const fromNodeCheck = nodes.value.find(n => n.nodeKey === connectingFrom.value)
      if (fromNodeCheck) {
        const existingEdge = edges.value.find(e => 
          e.fromNodeKey === connectingFrom.value &&
          e.fromPortIndex === connectingPortIndex.value
        )
        
        if (existingEdge) {
          ElMessage.warning('该输出端口已经连接，每个输出端口只能连接一条线')
          cancelConnection()
          return
        }
      }
      
      // 完成连接
      const fromNode = nodes.value.find(n => n.nodeKey === connectingFrom.value)
      let conditionExpression = 'true'
      
      // 如果是分支节点的连接线，从节点配置中获取条件表达式
      if (fromNode && isBranchNode(fromNode.type)) {
        if (fromNode.type === 'llm_branch') {
          // 大模型分支节点：从分支列表中获取分支名称
          if (fromNode.config && fromNode.config.branches) {
            const branchIndex = connectingPortIndex.value || 0
            if (fromNode.config.branches[branchIndex]) {
              conditionExpression = fromNode.config.branches[branchIndex].name
            } else {
              // 如果索引超出范围，可能是最后一个分支（默认分支）
              const lastIndex = fromNode.config.branches.length - 1
              if (branchIndex === fromNode.config.branches.length) {
                conditionExpression = fromNode.config.branches[lastIndex].name
              } else {
                conditionExpression = 'default'
              }
            }
          } else {
            conditionExpression = 'default'
          }
        } else if (fromNode.type === 'basic_branch') {
          // 基础分支节点：从条件表达式中获取
          if (fromNode.config && fromNode.config.conditions && fromNode.config.conditions[connectingPortIndex.value]) {
            conditionExpression = fromNode.config.conditions[connectingPortIndex.value]
          } else {
            conditionExpression = 'true' // 默认值
          }
        } else {
          conditionExpression = 'true' // 其他分支节点的默认值
        }
      }
      
        const newEdge = {
        id: `edge_${Date.now()}_${Math.random()}`,
        fromNodeKey: connectingFrom.value,
          toNodeKey: node.nodeKey,
        fromPortIndex: connectingPortIndex.value,
          toPortIndex: portIndex,
        conditionExpression: conditionExpression,
          variableMappings: '{}'
        }
      
      // 检查是否已存在相同的连接
      const exists = edges.value.some(e => 
        e.fromNodeKey === newEdge.fromNodeKey &&
        e.toNodeKey === newEdge.toNodeKey &&
        e.fromPortIndex === newEdge.fromPortIndex &&
        e.toPortIndex === newEdge.toPortIndex
      )
      
      if (!exists) {
        edges.value.push(newEdge)
        ElMessage.success('连接已创建')
      } else {
        ElMessage.warning('连接已存在')
      }
      
      cancelConnection()
    } else if (portType === 'input' && connectingFrom.value === node.nodeKey) {
      ElMessage.warning('不能连接到自身')
      cancelConnection()
    }
  } else {
    // 开始连接，只能从输出端口开始
    if (portType === 'output') {
      // 检查该输出端口是否已经有连接
      const existingEdge = edges.value.find(e => 
        e.fromNodeKey === node.nodeKey &&
        e.fromPortIndex === portIndex
      )
      
      if (existingEdge) {
        ElMessage.warning('该输出端口已经连接，每个输出端口只能连接一条线')
    return
  }
  
    isConnecting.value = true
      connectingFrom.value = node.nodeKey
      connectingPortIndex.value = portIndex
    }
  }
}

// 端口鼠标按下事件（用于拖拽连接）
const onPortMouseDown = (event, node, portType, portIndex = 0) => {
  event.stopPropagation()
  
  if (portType === 'output') {
    isConnecting.value = true
    connectingFrom.value = node.nodeKey
    connectingPortIndex.value = portIndex
  }
}

// 取消连接
const cancelConnection = () => {
    isConnecting.value = false
  connectingFrom.value = null
  connectingPortIndex.value = 0
}

// 连接线选择（现在只用于删除）
const selectEdge = (edge) => {
  selectedEdge.value = edge
  selectedNode.value = null
}

// 更新连接线
const updateEdge = () => {
  // 连接线属性已通过v-model双向绑定
}

// 删除连接线
const deleteEdge = (edge) => {
  edges.value = edges.value.filter(e => e.id !== edge.id)
  if (selectedEdge.value?.id === edge.id) {
    selectedEdge.value = null
  }
  ElMessage.success('连接线已删除')
}

const getEdgeNodeName = (nodeKey) => {
  const node = nodes.value.find(n => n.nodeKey === nodeKey)
  return node ? node.name : nodeKey
}

// 缩放控制
const zoomIn = () => {
  if (zoomLevel.value < 2) {
    zoomLevel.value = Math.min(2, zoomLevel.value + 0.1)
  }
}

const zoomOut = () => {
  if (zoomLevel.value > 0.5) {
    zoomLevel.value = Math.max(0.5, zoomLevel.value - 0.1)
  }
}

const resetZoom = () => {
  zoomLevel.value = 1
  canvasOffset.value = { x: 0, y: 0 }
}

// 全局变量管理
const addGlobalVariable = () => {
  const newVariable = {
    name: `变量${globalVariables.value.length + 1}`,
    type: 'string',
    initialValue: ''
  }
  globalVariables.value.push(newVariable)
}

const deleteGlobalVariable = (index) => {
  globalVariables.value.splice(index, 1)
}

const updateGlobalVariables = () => {
  // 全局变量已通过v-model双向绑定
}

// 大模型赋值节点变量管理
const addAssignVariable = () => {
  if (!selectedNodeConfig.value.assignVariables) {
    selectedNodeConfig.value.assignVariables = []
  }
  selectedNodeConfig.value.assignVariables.push({
    name: `变量${selectedNodeConfig.value.assignVariables.length + 1}`,
    type: 'string'
  })
  updateNodeConfig()
}

const removeAssignVariable = (index) => {
  if (selectedNodeConfig.value.assignVariables) {
    selectedNodeConfig.value.assignVariables.splice(index, 1)
    updateNodeConfig()
  }
}

// 大模型分支节点分支管理
const addLlmBranch = () => {
  if (!selectedNode.value || selectedNode.value.type !== 'llm_branch') return
  
  if (!selectedNodeConfig.value.branches) {
    selectedNodeConfig.value.branches = []
  }
  
  // 新分支插入到最后一个分支之前（最后一个分支是默认分支）
  const branches = selectedNodeConfig.value.branches
  const insertIndex = branches.length > 0 ? branches.length - 1 : 0
  branches.splice(insertIndex, 0, {
    name: `分支${branches.length + 1}`,
    description: ''
  })
  
  // 同步添加输出端口（也插入到最后一个端口之前）
  if (!selectedNode.value.config) selectedNode.value.config = {}
  if (!selectedNode.value.config.outputPorts) selectedNode.value.config.outputPorts = [{}]
  const outputPorts = selectedNode.value.config.outputPorts
  const portInsertIndex = outputPorts.length > 0 ? outputPorts.length - 1 : 0
  outputPorts.splice(portInsertIndex, 0, {})
  
  updateNodeConfig()
}

const removeLlmBranch = (index) => {
  if (!selectedNode.value || selectedNode.value.type !== 'llm_branch') return
  
  if (!selectedNodeConfig.value.branches) return
  
  const branches = selectedNodeConfig.value.branches
  if (branches.length <= 1) {
    ElMessage.warning('至少需要保留一个分支（最后一个分支是默认分支，不能删除）')
    return
  }
  
  // 不允许删除最后一个分支（默认分支）
  const lastIndex = branches.length - 1
  if (index === lastIndex) {
    ElMessage.warning('最后一个分支是默认分支，不能删除')
    return
  }
  
  // 同步删除输出端口
  if (selectedNode.value.config && selectedNode.value.config.outputPorts) {
    const outputPorts = selectedNode.value.config.outputPorts
    if (index < outputPorts.length) {
      outputPorts.splice(index, 1)
      
      // 删除相关的连接线
      edges.value = edges.value.filter(edge => {
        if (edge.fromNodeKey === selectedNode.value.nodeKey) {
          if (edge.fromPortIndex === index) {
            return false // 删除连接到这个端口的连接线
          } else if (edge.fromPortIndex > index) {
            edge.fromPortIndex-- // 调整后续端口的索引
          }
        }
        return true
      })
    }
  }
  
  // 删除分支
  branches.splice(index, 1)
  updateNodeConfig()
}

// 保存工作流
const saveWorkflow = () => {
  // 验证工作流
  if (!workflowName.value.trim()) {
    ElMessage.warning('请输入工作流名称')
    return
  }
  
  const startNodes = nodes.value.filter(n => n.type === 'start')
  const endNodes = nodes.value.filter(n => n.type === 'end')
  
  if (startNodes.length === 0) {
    ElMessage.warning('工作流必须包含一个开始节点')
    return
  }
  
  if (endNodes.length === 0) {
    ElMessage.warning('工作流必须包含一个结束节点')
    return
  }
  
  // 构建工作流数据 - 符合后端要求的格式
  const workflowData = {
    id: props.workflow?.id,
    name: workflowName.value,
    description: workflowDescription.value,
    version: workflowVersion.value,
    status: props.workflow?.status !== undefined ? props.workflow.status : 1,
    definition: {
      nodes: nodes.value.map(node => ({
        nodeKey: node.nodeKey,
        name: node.name,
        type: node.type,
        positionX: node.positionX,
        positionY: node.positionY,
        config: node.config || {}
      })),
      transitions: edges.value.map(edge => {
        // 查找源节点，判断是否是分支节点
        const fromNode = nodes.value.find(n => n.nodeKey === edge.fromNodeKey)
        const isFromBranchNode = fromNode && isBranchNode(fromNode.type)
        
        // 构建transition对象
        const transition = {
        fromNodeKey: edge.fromNodeKey,
        toNodeKey: edge.toNodeKey,
        variableMappings: edge.variableMappings || '{}'
        }
        
        // 只有分支节点的连接线才包含conditionExpression
        if (isFromBranchNode) {
          let conditionExpression = edge.conditionExpression
          
          // 大模型分支节点：强制从分支列表中获取分支名称（覆盖已有的 conditionExpression）
          if (fromNode.type === 'llm_branch') {
            if (fromNode.config && fromNode.config.branches) {
              const branchIndex = edge.fromPortIndex || 0
              if (branchIndex < fromNode.config.branches.length) {
                // 从分支列表中获取对应的分支名称
                conditionExpression = fromNode.config.branches[branchIndex].name
                console.log(`大模型分支节点：端口 ${branchIndex} 对应分支名称 "${conditionExpression}"`)
              } else {
                // 如果索引超出范围，使用最后一个分支（默认分支）
                const lastIndex = fromNode.config.branches.length - 1
                conditionExpression = fromNode.config.branches[lastIndex].name
                console.log(`大模型分支节点：端口 ${branchIndex} 超出范围，使用最后一个分支（默认分支）"${conditionExpression}"`)
              }
            } else {
              // 如果没有分支列表，使用默认值
              conditionExpression = 'default'
              console.warn(`大模型分支节点 ${fromNode.nodeKey} 没有配置分支列表，使用默认值`)
            }
          } else if (fromNode.type === 'basic_branch') {
            // 基础分支节点：最后一个分支是默认分支，条件表达式为空
            const outputPorts = getOutputPorts(fromNode)
            const isLastBranch = (edge.fromPortIndex || 0) === outputPorts.length - 1
            if (isLastBranch) {
              // 最后一个分支是默认分支，不设置条件表达式（后端会将其识别为默认分支）
              conditionExpression = null
            } else {
              // 其他分支使用条件表达式
              conditionExpression = conditionExpression || ''
            }
          }
          
          transition.conditionExpression = conditionExpression
        }
        // 非分支节点的连接线不包含conditionExpression，后端会直接执行
        
        return transition
      }),
      globalVariables: globalVariables.value
    }
  }
  
  emit('save', workflowData)
  ElMessage.success('工作流已保存')
}

// 运行工作流
const runWorkflow = () => {
  emit('run', {
    nodes: nodes.value,
    edges: edges.value
  })
  ElMessage.success('工作流已开始运行')
}

// 解析transitions并分配端口索引的辅助函数
const parseTransitions = (transitions, nodesList) => {
  if (!Array.isArray(transitions)) return []
  
  // 使用Map记录每个分支节点已使用的端口索引
  // key: fromNodeKey, value: 下一个要分配的端口索引
  const portIndexMap = new Map()
  
  return transitions.map((transition, index) => {
    const fromNodeKey = transition.fromNodeKey
    let fromPortIndex = transition.fromPortIndex
    
    // 如果JSON中没有fromPortIndex，需要自动分配
    if (fromPortIndex === undefined || fromPortIndex === null) {
      // 查找源节点
      const fromNode = nodesList.find(n => n.nodeKey === fromNodeKey)
      
      if (fromNode && isBranchNode(fromNode.type)) {
        // 分支节点：按transitions数组中的顺序，依次分配端口索引 0, 1, 2...
        // 对于同一个分支节点的多个出边，第一个出边用端口0，第二个用端口1，以此类推
        if (!portIndexMap.has(fromNodeKey)) {
          portIndexMap.set(fromNodeKey, 0)
        }
        fromPortIndex = portIndexMap.get(fromNodeKey)
        // 更新该节点的下一个端口索引
        portIndexMap.set(fromNodeKey, fromPortIndex + 1)
      } else {
        // 普通节点：只有一个输出端口，使用索引0
        fromPortIndex = 0
      }
    }
    
    // 查找源节点，判断是否是分支节点
    const fromNode = nodesList.find(n => n.nodeKey === transition.fromNodeKey)
    const isFromBranchNode = fromNode && isBranchNode(fromNode.type)
    
    return {
      id: `edge_${Date.now()}_${index}_${Math.random()}`,
      fromNodeKey: transition.fromNodeKey,
      toNodeKey: transition.toNodeKey,
      fromPortIndex: fromPortIndex,
      toPortIndex: transition.toPortIndex || 0,
      // 只有分支节点的连接线才需要conditionExpression
      conditionExpression: isFromBranchNode ? (transition.conditionExpression || 'true') : 'true',
      variableMappings: transition.variableMappings || '{}'
    }
  })
}

// 初始化工作流
onMounted(() => {
  // 初始化工作流基本信息
  if (props.workflow) {
    workflowName.value = props.workflow.name || ''
    workflowDescription.value = props.workflow.description || ''
    workflowVersion.value = props.workflow.version || '1.0.0'
    
    // 处理工作流定义
    if (props.workflow.definition) {
      nodes.value = Array.isArray(props.workflow.definition.nodes) 
        ? props.workflow.definition.nodes.map(node => ({
            ...node,
            config: node.config || {}
          }))
        : []
      
      // 使用统一的解析函数
      edges.value = parseTransitions(props.workflow.definition.transitions, nodes.value)
      
      globalVariables.value = Array.isArray(props.workflow.definition.globalVariables) 
        ? props.workflow.definition.globalVariables 
        : []
      } else {
      nodes.value = []
      edges.value = []
      globalVariables.value = []
    }
  } else {
    nodes.value = []
    edges.value = []
    globalVariables.value = []
  }
  
  // 添加键盘事件监听（ESC取消连接）
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && isConnecting.value) {
      cancelConnection()
    }
    if (e.key === 'Delete' && selectedEdge.value) {
      deleteEdge(selectedEdge.value)
    }
  })
  
  // 添加全局鼠标事件监听（用于画布拖拽）
  document.addEventListener('mousemove', onGlobalMouseMove)
  document.addEventListener('mouseup', onGlobalMouseUp)
  
  // 标记为已初始化
  isInitialized.value = true
})

// 组件卸载时清理
onUnmounted(() => {
  cancelConnection()
  // 移除全局事件监听器
  document.removeEventListener('mousemove', onGlobalMouseMove)
  document.removeEventListener('mouseup', onGlobalMouseUp)
})

// 监听工作流变化（但避免在保存后清空界面）
watch(() => props.workflow, (newWorkflow, oldWorkflow) => {
  // 只在首次加载或workflow ID真正变化时才更新
  // 避免保存后因为workflow对象引用变化而清空界面
  if (!isInitialized.value || (newWorkflow?.id !== oldWorkflow?.id)) {
  if (newWorkflow) {
      workflowName.value = newWorkflow.name || ''
      workflowDescription.value = newWorkflow.description || ''
      workflowVersion.value = newWorkflow.version || '1.0.0'
      
    if (newWorkflow.definition) {
        nodes.value = Array.isArray(newWorkflow.definition.nodes) 
          ? newWorkflow.definition.nodes.map(node => ({
              ...node,
              config: node.config || {}
            }))
          : []
        
        // 使用统一的解析函数，确保分支节点端口索引正确分配
        edges.value = parseTransitions(newWorkflow.definition.transitions, nodes.value)
        
        globalVariables.value = Array.isArray(newWorkflow.definition.globalVariables) 
          ? newWorkflow.definition.globalVariables 
          : []
      } else {
      nodes.value = []
      edges.value = []
      globalVariables.value = []
    }
    }
    isInitialized.value = true
  }
}, { deep: true, immediate: false })
</script>

<style scoped>
.workflow-designer {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background-color: #f5f7fa;
}

/* 工具栏 */
.designer-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 20px;
  background-color: #fff;
  border-bottom: 1px solid #e0e0e0;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

.toolbar-right {
  display: flex;
  gap: 8px;
}

/* 设计器主体 */
.designer-container {
  display: flex;
  flex: 1;
  overflow: hidden;
}

/* 节点面板 */
.node-palette {
  width: 200px;
  background-color: #fff;
  border-right: 1px solid #e0e0e0;
  padding: 16px;
  overflow-y: auto;
}

.node-palette h3 {
  margin: 0 0 16px 0;
  font-size: 16px;
  font-weight: 600;
}

.node-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.node-item {
  display: flex;
  align-items: center;
  padding: 12px;
  background-color: #f0f0f0;
  border: 1px solid #e0e0e0;
  border-radius: 4px;
  cursor: move;
  transition: all 0.2s;
}

.node-item:hover {
  background-color: #e0e0e0;
  border-color: #d0d0d0;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.node-icon {
  font-size: 20px;
  margin-right: 8px;
}

.node-label {
  font-size: 14px;
  font-weight: 500;
}

/* 画布容器 */
.canvas-container {
  flex: 1;
  overflow: auto;
  background-color: #fafafa;
  position: relative;
  cursor: default;
}

.canvas {
  width: 5000px;
  height: 5000px;
  position: relative;
  background-color: #fff;
}

/* 网格背景 */
.grid-background {
  position: absolute;
  width: 100%;
  height: 100%;
  background-size: 20px 20px;
  background-image: 
    linear-gradient(to right, rgba(0, 0, 0, 0.05) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(0, 0, 0, 0.05) 1px, transparent 1px);
  background-position: 0 0;
  pointer-events: none;
}

/* 连接线层 */
.connections-layer {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 1;
}

.connection-line {
  stroke: #909399;
  stroke-width: 2px;
  fill: none;
  pointer-events: stroke;
  cursor: pointer;
  transition: stroke 0.2s;
}

.connection-line:hover {
  stroke: #409eff;
  stroke-width: 3px;
}

.connection-line-selected {
  stroke: #409eff;
  stroke-width: 3px;
}

/* 反向连接线（入口在出口右侧）- 使用虚线 */
.connection-line-reverse {
  stroke-dasharray: 5, 5;
  stroke: #909399;
}

.connection-line-reverse:hover {
  stroke: #409eff;
  stroke-dasharray: 5, 5;
}

.connection-line-reverse.connection-line-selected {
  stroke: #409eff;
  stroke-dasharray: 5, 5;
}

.connection-line-preview {
  stroke: #67c23a;
  stroke-dasharray: 5,5;
  stroke-width: 3px;
  cursor: crosshair;
  pointer-events: none;
}

.edge-delete-btn {
  fill: #ff4d4f;
  cursor: pointer;
  pointer-events: all;
  transition: r 0.2s;
}

.edge-delete-btn:hover {
  r: 10;
}

/* 工作流节点 */
.workflow-node {
  position: absolute;
  width: 180px;
  min-height: 80px;
  background-color: #fff;
  border: 2px solid #dcdfe6;
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
  cursor: move;
  z-index: 2;
  transition: all 0.2s;
  display: flex;
  flex-direction: column;
  overflow: visible;
}

.workflow-node:hover {
  box-shadow: 0 4px 16px 0 rgba(0, 0, 0, 0.15);
}

.workflow-node.node-selected {
  border-color: #409eff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2);
}

.workflow-node.branch-node {
  min-height: 100px;
}

/* 端口组 */
.port-group {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 24px;
  pointer-events: none;
}

.port-group-input {
  left: -12px;
}

.port-group-output {
  right: -12px;
}

/* 端口 */
.port {
  position: absolute;
  width: 24px;
  height: 24px;
  cursor: pointer;
  pointer-events: all;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10;
}

.port-circle {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  border: 2px solid #909399;
  background-color: #fff;
  transition: all 0.2s;
}

.port-circle-input {
  border-color: #67c23a;
}

.port-circle-output {
  border-color: #409eff;
}

/* 端口三角形样式 - 朝右的实心三角形 */
.port-triangle {
  width: 0;
  height: 0;
  border-top: 6px solid transparent;
  border-bottom: 6px solid transparent;
  border-left: 10px solid #909399;
  transition: all 0.2s;
}

.port-triangle-input {
  border-left-color: #67c23a;
}

.port-triangle-output {
  border-left-color: #409eff;
}

.port:hover .port-circle {
  transform: scale(1.3);
  border-width: 3px;
}

.port:hover .port-triangle {
  transform: scale(1.3);
}

.port:hover .port-triangle-input {
  border-left-color: #67c23a;
  border-left-width: 12px;
  border-top-width: 7px;
  border-bottom-width: 7px;
}

.port:hover .port-triangle-output {
  border-left-color: #409eff;
  border-left-width: 12px;
  border-top-width: 7px;
  border-bottom-width: 7px;
}

/* 端口删除按钮 */
.port-delete-btn {
  position: absolute;
  top: -8px;
  right: -8px;
  width: 18px;
  height: 18px;
  background-color: #ff4d4f;
  color: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  font-size: 12px;
  transition: all 0.2s;
  opacity: 0.8;
  z-index: 20;
}

.port-delete-btn:hover {
  opacity: 1;
  transform: scale(1.2);
}

/* 添加端口按钮 */
.port-add-btn {
  position: absolute;
  width: 20px;
  height: 20px;
  background-color: #f0f0f0;
  border: 1px dashed #d0d0d0;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
  pointer-events: all;
  z-index: 10;
}

.port-add-btn:hover {
  background-color: #e0e0e0;
  border-color: #409eff;
  transform: scale(1.1);
}

/* 节点内容 */
.node-content {
  flex: 1;
  padding: 0 8px;
  display: flex;
  flex-direction: column;
  position: relative;
  min-height: 0;
}

.node-header {
  display: flex;
  align-items: center;
  padding: 12px 8px;
  border-bottom: 1px solid #e0e0e0;
  background-color: rgba(240, 240, 240, 0.5);
  border-radius: 8px 8px 0 0;
}

.node-type-icon {
  font-size: 18px;
  margin-right: 8px;
}

.node-name {
  flex: 1;
  font-size: 14px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-type {
  padding: 8px;
  font-size: 12px;
  color: #606266;
  background-color: rgba(0, 0, 0, 0.03);
  text-align: center;
}

/* 分支节点条件表达式显示 */
.branch-conditions {
  position: relative;
  min-height: 0;
  background-color: rgba(64, 158, 255, 0.05);
  border-radius: 4px;
  flex: 1;
}

.branch-condition-item {
  position: absolute;
  left: 8px;
  right: 8px;
  display: flex;
  align-items: center;
  font-size: 11px;
  line-height: 32px;
  height: 32px;
}

.condition-label {
  color: #909399;
  margin-right: 6px;
  flex-shrink: 0;
  font-weight: 500;
}

.condition-expression {
  color: #303133;
  word-break: break-all;
  flex: 1;
}

.branch-condition-edit {
  margin-bottom: 12px;
}

/* 属性面板 */
.properties-panel {
  width: 300px;
  background-color: #fff;
  border-left: 1px solid #e0e0e0;
  padding: 16px;
  overflow-y: auto;
}

.properties-panel h3 {
  margin: 0 0 16px 0;
  font-size: 16px;
  font-weight: 600;
}

.node-properties {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.no-selection {
  padding: 20px;
  text-align: center;
  color: #909399;
  font-style: italic;
}

/* 全局变量 */
.global-variables {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.variables-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.variables-header h3 {
  margin: 0;
}

.variables-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.variable-item {
  padding: 12px;
  background-color: #f5f7fa;
  border-radius: 4px;
  border: 1px solid #e0e0e0;
}

.variable-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.no-variables {
  padding: 20px;
  text-align: center;
  color: #909399;
  font-style: italic;
}
</style>
