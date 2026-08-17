<template>
  <div class="app-container">
    <el-tabs v-model="activeResource" @tab-change="handleTabChange">
      <el-tab-pane label="渠道" name="channels" />
      <el-tab-pane label="模型" name="models" />
      <el-tab-pane label="路由策略" name="routes" />
      <el-tab-pane label="价格" name="prices" />
      <el-tab-pane label="供应商" name="providers" />
    </el-tabs>

    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
      <el-form-item label="关键词" prop="keyword">
        <el-input v-model="queryParams.keyword" placeholder="编码或名称" clearable style="width: 200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['ai:config:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="!currentId" @click="handleEdit()" v-hasPermi="['ai:config:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="!currentId" @click="handleDelete()" v-hasPermi="['ai:config:remove']">删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="configList" highlight-current-row @current-change="handleCurrentChange">
      <el-table-column label="编码" prop="key" align="left" min-width="160" :show-overflow-tooltip="true" />
      <el-table-column label="名称" prop="name" align="left" min-width="160" :show-overflow-tooltip="true" />
      <el-table-column label="启用" align="center" width="80">
        <template #default="scope">
          <el-tag :type="scope.row.enabled === '1' ? 'success' : 'info'" size="small">
            {{ scope.row.enabled === '1' ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="activeResource === 'channels'" label="密钥摘要" prop="key_summary" align="left" min-width="140" :show-overflow-tooltip="true" />
      <el-table-column label="操作" align="center" width="280" fixed="right">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleEdit(scope.row)" v-hasPermi="['ai:config:edit']">修改</el-button>
          <el-button link type="success" icon="VideoPlay" v-if="scope.row.enabled !== '1'" @click="handleToggle(scope.row, true)" v-hasPermi="['ai:config:edit']">启用</el-button>
          <el-button link type="warning" icon="VideoPause" v-else @click="handleToggle(scope.row, false)" v-hasPermi="['ai:config:edit']">停用</el-button>
          <el-button link type="primary" icon="Key" v-if="activeResource === 'channels'" @click="handleReplaceKey(scope.row)" v-hasPermi="['ai:config:key:replace']">替换密钥</el-button>
          <el-button link type="info" icon="Connection" @click="handleTest(scope.row)" v-hasPermi="['ai:config:test']">测试</el-button>
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

    <!-- 新增/编辑对话框 -->
    <el-dialog :title="editTitle" v-model="editOpen" width="720px" append-to-body :close-on-click-modal="false">
      <el-form ref="editRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="编码" prop="key">
          <el-input v-model="form.key" :disabled="isEdit" placeholder="小写字母数字与 _-" />
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="配置 JSON" prop="configJson">
          <el-input v-model="form.configJson" type="textarea" :rows="6" :placeholder="configPlaceholder" />
        </el-form-item>
        <el-form-item label="启用" prop="enabled">
          <el-radio-group v-model="form.enabled">
            <el-radio value="1">启用</el-radio>
            <el-radio value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="activeResource === 'channels'" label="API Key" prop="apiKey">
          <el-input v-model="form.apiKey" type="password" show-password placeholder="仅写入不回显；留空表示不替换密钥" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" :loading="submitting" @click="submitForm">确 定</el-button>
        <el-button @click="editOpen = false">取 消</el-button>
      </template>
    </el-dialog>

    <!-- 替换密钥对话框 -->
    <el-dialog title="替换 API Key" v-model="keyOpen" width="520px" append-to-body :close-on-click-modal="false">
      <el-form label-width="100px">
        <el-form-item label="新 API Key">
          <el-input v-model="newApiKey" type="password" show-password placeholder="明文仅写入并立即加密，绝不回显" />
        </el-form-item>
        <el-alert type="info" :closable="false" show-icon>
          替换后旧密钥立即停用；仅返回脱敏摘要与密钥版本，不回显明文。
        </el-alert>
      </el-form>
      <template #footer>
        <el-button type="primary" :loading="replacingKey" @click="submitReplaceKey">确认替换</el-button>
        <el-button @click="keyOpen = false">取 消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="AiConfig">
import {
  listConfig, getConfig, addConfig, updateConfig, delConfig,
  toggleConfig, replaceApiKey, testConfig
} from "@/api/ai/config"
import type { AiConfigResource, AiConfigPayload } from "@/types/api/ai"

const { proxy } = getCurrentInstance() as any

const activeResource = ref<AiConfigResource>("channels")
const configList = ref<any[]>([])
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const submitting = ref(false)
const replacingKey = ref(false)
const editOpen = ref(false)
const keyOpen = ref(false)
const isEdit = ref(false)
const currentId = ref<number | undefined>(undefined)
const currentRow = ref<any>(null)
const newApiKey = ref("")

const queryParams = reactive({ pageNum: 1, pageSize: 10, keyword: "" })

const form = reactive<AiConfigPayload>({
  key: "", name: "", configJson: "", enabled: "0", apiKey: ""
})
const formRules = {
  key: [
    { required: true, message: "编码不能为空", trigger: "blur" },
    { pattern: /^[a-z0-9][a-z0-9_-]{0,63}$/, message: "小写字母数字与 _-，且以字母或数字开头", trigger: "blur" }
  ],
  name: [{ required: true, message: "名称不能为空", trigger: "blur" }]
}

const editTitle = computed(() => (isEdit.value ? "修改配置" : "新增配置"))

const configPlaceholder = computed(() => {
  switch (activeResource.value) {
    case "channels":
      return '{"endpoint":"https://api.example.com","provider":"openai"}'
    case "models":
      return '{"capabilities":["chat","tool"],"contextWindow":8192}'
    case "routes":
      return '{"items":[{"channel":"default","model":"gpt-4","priority":1}]}'
    case "prices":
      return '{"currency":"USD","input":"0.01","output":"0.03","effectiveFrom":"2026-08-14"}'
    case "providers":
      return '{"type":"custom"}'
    default:
      return "{}"
  }
})

function handleTabChange() {
  queryParams.pageNum = 1
  queryParams.keyword = ""
  currentId.value = undefined
  currentRow.value = null
  getList()
}

function getList() {
  loading.value = true
  listConfig(activeResource.value, queryParams.keyword, queryParams.pageNum, queryParams.pageSize).then((res: any) => {
    configList.value = res.data || []
    total.value = (res.data || []).length >= queryParams.pageSize
      ? queryParams.pageSize * queryParams.pageNum
      : (queryParams.pageNum - 1) * queryParams.pageSize + (res.data || []).length
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

function handleCurrentChange(row: any) {
  currentRow.value = row
  currentId.value = row?.id ?? row?.[`${activeResource.value.replace(/s$/, '')}_id`]
}

function resetForm() {
  form.key = ""
  form.name = ""
  form.configJson = ""
  form.enabled = "0"
  form.apiKey = ""
  currentId.value = undefined
  proxy.resetForm("editRef")
}

function handleAdd() {
  resetForm()
  isEdit.value = false
  editOpen.value = true
}

function handleEdit(row?: any) {
  const target = row || currentRow.value
  if (!target) return
  resetForm()
  isEdit.value = true
  const id = target.id ?? target[`${activeResource.value.replace(/s$/, '')}_id`]
  currentId.value = id
  getConfig(activeResource.value, id).then((res: any) => {
    const data = res.data || {}
    form.key = data.key || data.provider_key || data.channel_key || data.model_key || data.policy_key || ""
    form.name = data.name || data.model_name || ""
    form.configJson = data.config_json || data.capabilities || data.policy_json || ""
    form.enabled = data.enabled || "0"
    editOpen.value = true
  })
}

function submitForm() {
  proxy.$refs["editRef"].validate((valid: boolean) => {
    if (!valid) return
    submitting.value = true
    const payload: AiConfigPayload = { ...form }
    if (!payload.apiKey) delete (payload as any).apiKey
    const req = isEdit.value
      ? updateConfig(activeResource.value, currentId.value as number, payload)
      : addConfig(activeResource.value, payload)
    req.then(() => {
      proxy.$modal.msgSuccess(isEdit.value ? "修改成功" : "新增成功")
      editOpen.value = false
      getList()
    }).finally(() => { submitting.value = false })
  })
}

function handleToggle(row: any, enabled: boolean) {
  const id = row.id ?? row[`${activeResource.value.replace(/s$/, '')}_id`]
  toggleConfig(activeResource.value, id, enabled).then(() => {
    proxy.$modal.msgSuccess(enabled ? "已启用" : "已停用")
    getList()
  })
}

function handleDelete() {
  if (!currentId.value) return
  proxy.$modal.confirm("确认删除该配置？").then(() => {
    delConfig(activeResource.value, currentId.value as number).then(() => {
      proxy.$modal.msgSuccess("删除成功")
      currentId.value = undefined
      getList()
    })
  }).catch(() => {})
}

function handleReplaceKey(row: any) {
  const id = row.id ?? row.channel_id
  currentId.value = id
  newApiKey.value = ""
  keyOpen.value = true
}

function submitReplaceKey() {
  if (!newApiKey.value) {
    proxy.$modal.msgError("请输入新 API Key")
    return
  }
  replacingKey.value = true
  replaceApiKey(activeResource.value, currentId.value as number, { key: "", apiKey: newApiKey.value }).then((res: any) => {
    proxy.$modal.msgSuccess(`密钥已替换，摘要：${res.data?.keySummary}，版本：${res.data?.keyVersion}`)
    keyOpen.value = false
    getList()
  }).finally(() => { replacingKey.value = false })
}

function handleTest(row: any) {
  const id = row.id ?? row[`${activeResource.value.replace(/s$/, '')}_id`]
  testConfig(activeResource.value, id).then((res: any) => {
    proxy.$modal.msgSuccess(res.data?.message || "测试请求已提交")
  })
}

handleTabChange()
</script>

<style scoped>
.mb8 {
  margin-bottom: 8px;
}
</style>
