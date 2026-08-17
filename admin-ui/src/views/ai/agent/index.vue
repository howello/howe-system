<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="Agent" prop="keyword">
        <el-input v-model="queryParams.keyword" placeholder="编码或名称" clearable style="width: 200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['ai:agent:edit']">新增</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="agentList">
      <el-table-column label="编码" align="left" prop="agent_key" min-width="160" :show-overflow-tooltip="true" />
      <el-table-column label="名称" align="left" prop="name" min-width="160" :show-overflow-tooltip="true" />
      <el-table-column label="草稿版本" align="center" prop="draft_version" width="90" />
      <el-table-column label="发布状态" align="center" width="110">
        <template #default="scope">
          <el-tag v-if="scope.row.published_version_id" type="success" size="small">已发布</el-tag>
          <el-tag v-else type="warning" size="small">未发布</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.status === '0' ? 'success' : 'danger'" size="small">
            {{ scope.row.status === '0' ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="320" fixed="right">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleEdit(scope.row)" v-hasPermi="['ai:agent:edit']">编辑草稿</el-button>
          <el-button link type="success" icon="Upload" @click="handlePublish(scope.row)" v-hasPermi="['ai:agent:publish']">发布</el-button>
          <el-button link type="info" icon="View" @click="handleVersions(scope.row)" v-hasPermi="['ai:agent:view']">版本</el-button>
          <el-button link type="warning" icon="CircleClose" v-if="scope.row.status === '0'" @click="handleDisable(scope.row)" v-hasPermi="['ai:agent:disable']">停用</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />

    <!-- 新增 Agent 对话框（仅创建编码与名称，草稿正文在编辑页维护） -->
    <el-dialog title="新增 Agent" v-model="addOpen" width="520px" append-to-body :close-on-click-modal="false">
      <el-form ref="addRef" :model="addForm" :rules="addRules" label-width="90px">
        <el-form-item label="编码" prop="agentKey">
          <el-input v-model="addForm.agentKey" placeholder="小写字母数字与 _-，如 blog-assistant" />
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="addForm.name" placeholder="Agent 显示名称" />
        </el-form-item>
        <el-alert type="info" :closable="false" show-icon>
          创建后会得到一个空草稿，进入「编辑草稿」填写系统提示词、路由、Tool 白名单与预算。
        </el-alert>
      </el-form>
      <template #footer>
        <el-button type="primary" :loading="submitting" @click="submitAdd">确 定</el-button>
        <el-button @click="addOpen = false">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 编辑草稿对话框 -->
    <el-dialog :title="`编辑草稿：${draftForm.name || ''}`" v-model="editOpen" width="1100px" top="5vh" append-to-body :close-on-click-modal="false">
      <el-alert v-if="versionConflict" type="error" :closable="false" show-icon class="mb8">
        草稿已被他人修改（版本号冲突），请关闭后重新打开以获取最新草稿。
      </el-alert>
      <el-form ref="editRef" :model="draftForm" :rules="editRules" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="名称" prop="name">
              <el-input v-model="draftForm.name" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="草稿版本">
              <el-input :model-value="draftVersion" disabled />
              <span class="version-hint">乐观锁凭据，更新时回传给后端</span>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="24">
            <el-form-item label="系统提示词" prop="systemPrompt">
              <markdown-editor v-model="draftForm.systemPrompt" :height="320" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="路由快照">
              <el-input v-model="draftForm.routeJson" type="textarea" :rows="4" placeholder='如 {"policy":"default"}' />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Tool 白名单">
              <el-input v-model="draftForm.toolJson" type="textarea" :rows="4" placeholder='如 ["blog_search","blog_stats"]' />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="预算">
              <el-input v-model="draftForm.budgetJson" type="textarea" :rows="4" placeholder='如 {"maxTokens":4096,"maxCost":"0.1"}' />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="校验">
              <el-button type="primary" plain :loading="validating" @click="handleValidate">校验草稿</el-button>
              <span v-if="validateMsg" :class="validateOk ? 'ok-hint' : 'err-hint'">{{ validateMsg }}</span>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button type="primary" :loading="submitting" @click="submitEdit">保存草稿</el-button>
        <el-button @click="editOpen = false">关 闭</el-button>
      </template>
    </el-dialog>

    <!-- 发布对话框 -->
    <el-dialog title="发布 Agent" v-model="publishOpen" width="520px" append-to-body :close-on-click-modal="false">
      <el-form label-width="90px">
        <el-form-item label="版本号">
          <el-input-number v-model="publishVersionNo" :min="1" :max="9999" />
        </el-form-item>
        <el-alert type="warning" :closable="false" show-icon>
          发布后生成不可变版本，固化系统提示词、路由、Tool 白名单与预算快照；历史 Run 按快照追溯，不受后续草稿修改影响。
        </el-alert>
      </el-form>
      <template #footer>
        <el-button type="primary" :loading="publishing" @click="submitPublish">确认发布</el-button>
        <el-button @click="publishOpen = false">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 版本列表与详情（只读） -->
    <el-dialog title="版本历史" v-model="versionOpen" width="900px" top="5vh" append-to-body>
      <el-table :data="versionList" v-loading="versionLoading" @row-click="handleVersionDetail">
        <el-table-column label="版本号" prop="version_no" width="80" align="center" />
        <el-table-column label="发布人" prop="published_by" width="120" align="center" />
        <el-table-column label="发布时间" prop="published_time" width="180" align="center">
          <template #default="scope">{{ parseTime(scope.row.published_time) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center">
          <template #default="scope">
            <el-button link type="primary" @click.stop="handleVersionDetail(scope.row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-dialog title="版本详情（只读）" v-model="versionDetailOpen" width="800px" append-to-body>
        <el-descriptions :column="1" border v-if="versionDetail">
          <el-descriptions-item label="版本号">{{ versionDetail.version_no }}</el-descriptions-item>
          <el-descriptions-item label="发布人">{{ versionDetail.published_by }}</el-descriptions-item>
          <el-descriptions-item label="发布时间">{{ parseTime(versionDetail.published_time) }}</el-descriptions-item>
          <el-descriptions-item label="系统提示词"><pre class="readonly-text">{{ versionDetail.system_prompt }}</pre></el-descriptions-item>
          <el-descriptions-item label="路由快照"><pre class="readonly-text">{{ versionDetail.route_snapshot }}</pre></el-descriptions-item>
          <el-descriptions-item label="Tool 白名单"><pre class="readonly-text">{{ versionDetail.tool_snapshot }}</pre></el-descriptions-item>
          <el-descriptions-item label="预算快照"><pre class="readonly-text">{{ versionDetail.budget_snapshot }}</pre></el-descriptions-item>
        </el-descriptions>
      </el-dialog>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="AiAgent">
import MarkdownEditor from "@/components/MarkdownEditor/index.vue"
import { addAgent, updateAgent, getAgent, validateAgent, publishAgent, disableAgent, listAgentVersions, getAgentVersion } from "@/api/ai/admin"
import { listConfig } from "@/api/ai/config"
import type { AgentDraftPayload } from "@/types/api/ai"

const { proxy } = getCurrentInstance() as any

const agentList = ref<any[]>([])
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const submitting = ref(false)
const validating = ref(false)
const validateMsg = ref("")
const validateOk = ref(false)
const publishing = ref(false)
const versionConflict = ref(false)

const addOpen = ref(false)
const editOpen = ref(false)
const publishOpen = ref(false)
const versionOpen = ref(false)
const versionDetailOpen = ref(false)

const currentAgentId = ref<number>()
const draftVersion = ref(0)
const publishVersionNo = ref(1)

const versionList = ref<any[]>([])
const versionLoading = ref(false)
const versionDetail = ref<any>(null)

const queryParams = reactive({ pageNum: 1, pageSize: 10, keyword: "" })

const addForm = reactive<{ agentKey: string; name: string }>({ agentKey: "", name: "" })
const addRules = {
  agentKey: [
    { required: true, message: "编码不能为空", trigger: "blur" },
    { pattern: /^[a-z0-9][a-z0-9_-]{0,63}$/, message: "小写字母数字与 _-，且以字母或数字开头", trigger: "blur" }
  ],
  name: [{ required: true, message: "名称不能为空", trigger: "blur" }]
}

const draftForm = reactive<AgentDraftPayload>({
  agentKey: "", name: "", systemPrompt: "", routeJson: "", toolJson: "", budgetJson: ""
})
const editRules = {
  name: [{ required: true, message: "名称不能为空", trigger: "blur" }],
  systemPrompt: [{ required: true, message: "系统提示词不能为空", trigger: "blur" }]
}

/** 查询 Agent 列表（通用配置接口，按 agents 资源查 ai_agent 表） */
function getList() {
  loading.value = true
  listConfig("agents", queryParams.keyword, queryParams.pageNum, queryParams.pageSize).then((res: any) => {
    agentList.value = res.data || []
    total.value = (res.data || []).length >= queryParams.pageSize ? queryParams.pageSize * queryParams.pageNum : (queryParams.pageNum - 1) * queryParams.pageSize + (res.data || []).length
    loading.value = false
  }).catch(() => { loading.value = false })
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

function handleAdd() {
  addForm.agentKey = ""
  addForm.name = ""
  addOpen.value = true
}

function submitAdd() {
  proxy.$refs["addRef"].validate((valid: boolean) => {
    if (!valid) return
    submitting.value = true
    addAgent({ ...addForm }).then(() => {
      proxy.$modal.msgSuccess("新增成功")
      addOpen.value = false
      getList()
    }).finally(() => { submitting.value = false })
  })
}

/** 解析草稿 JSON 字段为可编辑表单 */
function parseDraft(draftJson: string): AgentDraftPayload {
  try {
    const d = JSON.parse(draftJson || "{}")
    return {
      agentKey: d.agentKey || "",
      name: d.name || "",
      systemPrompt: d.systemPrompt || "",
      routeJson: typeof d.routeJson === "string" ? d.routeJson : JSON.stringify(d.routeJson || ""),
      toolJson: typeof d.toolJson === "string" ? d.toolJson : JSON.stringify(d.toolJson || ""),
      budgetJson: typeof d.budgetJson === "string" ? d.budgetJson : JSON.stringify(d.budgetJson || ""),
    }
  } catch {
    return { agentKey: "", name: "", systemPrompt: "", routeJson: "", toolJson: "", budgetJson: "" }
  }
}

function handleEdit(row: any) {
  currentAgentId.value = row.agent_id
  versionConflict.value = false
  validateMsg.value = ""
  getAgent(row.agent_id).then((res: any) => {
    const agent = res.data
    draftVersion.value = agent.draft_version || 0
    const parsed = parseDraft(agent.draft_json)
    Object.assign(draftForm, parsed)
    if (!draftForm.name) draftForm.name = row.name
    if (!draftForm.agentKey) draftForm.agentKey = row.agent_key
    editOpen.value = true
  })
}

function handleValidate() {
  validating.value = true
  validateMsg.value = ""
  validateAgent({ ...draftForm }).then(() => {
    validateOk.value = true
    validateMsg.value = "校验通过"
  }).catch(() => {
    validateOk.value = false
    validateMsg.value = "校验未通过"
  }).finally(() => { validating.value = false })
}

function submitEdit() {
  proxy.$refs["editRef"].validate((valid: boolean) => {
    if (!valid) return
    submitting.value = true
    versionConflict.value = false
    updateAgent(currentAgentId.value as number, draftVersion.value, { ...draftForm }).then(() => {
      proxy.$modal.msgSuccess("草稿已保存")
      draftVersion.value += 1
      getList()
    }).catch((err: any) => {
      // 乐观锁冲突或已发布：后端返回非 1，经拦截器映射为错误
      if (err && /过期|已发布|版本/i.test(String(err.message || err))) {
        versionConflict.value = true
      }
    }).finally(() => { submitting.value = false })
  })
}

function handlePublish(row: any) {
  currentAgentId.value = row.agent_id
  publishVersionNo.value = (row.draft_version || 1)
  publishOpen.value = true
}

function submitPublish() {
  publishing.value = true
  publishAgent(currentAgentId.value as number, publishVersionNo.value).then(() => {
    proxy.$modal.msgSuccess("发布成功")
    publishOpen.value = false
    getList()
  }).finally(() => { publishing.value = false })
}

function handleDisable(row: any) {
  proxy.$modal.confirm(`确认停用 Agent「${row.name}」？停用后无法创建新会话，历史 Run 不受影响。`).then(() => {
    disableAgent(row.agent_id).then(() => {
      proxy.$modal.msgSuccess("已停用")
      getList()
    })
  }).catch(() => {})
}

function handleVersions(row: any) {
  currentAgentId.value = row.agent_id
  versionOpen.value = true
  versionLoading.value = true
  listAgentVersions(row.agent_id, 1, 50).then((res: any) => {
    versionList.value = res.data || []
  }).finally(() => { versionLoading.value = false })
}

function handleVersionDetail(row: any) {
  getAgentVersion(currentAgentId.value as number, row.version_no).then((res: any) => {
    versionDetail.value = res.data
    versionDetailOpen.value = true
  })
}

getList()
</script>

<style scoped>
.version-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-left: 8px;
}
.ok-hint {
  color: var(--el-color-success);
  margin-left: 8px;
  font-size: 13px;
}
.err-hint {
  color: var(--el-color-danger);
  margin-left: 8px;
  font-size: 13px;
}
.readonly-text {
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
  max-height: 240px;
  overflow: auto;
  font-family: inherit;
}
.mb8 {
  margin-bottom: 8px;
}
</style>
