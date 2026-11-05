<template>
   <div class="app-container">
      <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="80px">
         <el-form-item label="服务商" prop="providerCode">
            <el-select v-model="queryParams.providerCode" placeholder="全部服务商" clearable style="width: 200px">
               <el-option v-for="item in providerOptions" :key="item.code" :label="item.name" :value="item.code" />
            </el-select>
         </el-form-item>
         <el-form-item label="渠道名称" prop="name">
            <el-input v-model="queryParams.name" placeholder="按渠道名称搜索" clearable style="width: 200px" @keyup.enter="handleQuery" />
         </el-form-item>
         <el-form-item label="健康状态" prop="healthStatus">
            <el-select v-model="queryParams.healthStatus" placeholder="全部状态" clearable style="width: 150px">
               <el-option label="正常" value="UP" />
               <el-option label="异常" value="DOWN" />
               <el-option label="未检测" value="UNKNOWN" />
            </el-select>
         </el-form-item>
         <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
         </el-form-item>
      </el-form>

      <el-row :gutter="10" class="mb8">
         <el-col :span="1.5">
            <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['ai:channel:add']">新增</el-button>
         </el-col>
         <el-col :span="1.5">
            <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate()" v-hasPermi="['ai:channel:edit']">修改</el-button>
         </el-col>
         <el-col :span="1.5">
            <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['ai:channel:remove']">删除</el-button>
         </el-col>
         <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table v-loading="loading" :data="channelList" @selection-change="handleSelectionChange">
         <el-table-column type="selection" width="55" align="center" />
         <el-table-column label="渠道名称" align="left" prop="name" :show-overflow-tooltip="true" min-width="160" />
         <el-table-column label="服务商" align="center" prop="providerCode" width="160">
            <template #default="scope">
               <span>{{ providerLabel(scope.row.providerCode) }}</span>
            </template>
         </el-table-column>
         <el-table-column label="接入点" align="left" prop="baseUrl" :show-overflow-tooltip="true" min-width="200">
            <template #default="scope">
               <span v-if="scope.row.baseUrl">{{ scope.row.baseUrl }}</span>
               <span v-else class="ai-muted">跟随服务商默认</span>
            </template>
         </el-table-column>
         <el-table-column label="密钥" align="center" prop="apiKey" width="160">
            <template #default="scope">
               <span v-if="scope.row.apiKey" class="ai-mono">{{ scope.row.apiKey }}</span>
               <span v-else class="ai-muted">未配置</span>
            </template>
         </el-table-column>
         <el-table-column label="降级顺序" align="center" prop="priority" width="100" />
         <el-table-column label="健康状态" align="center" width="100">
            <template #default="scope">
               <el-tag v-if="scope.row.healthStatus === 'UP'" type="success" size="small">正常</el-tag>
               <el-tag v-else-if="scope.row.healthStatus === 'DOWN'" type="danger" size="small">异常</el-tag>
               <el-tag v-else type="info" size="small">未检测</el-tag>
            </template>
         </el-table-column>
         <el-table-column label="最近检测" align="center" prop="lastCheckAt" width="160">
            <template #default="scope">
               <span v-if="scope.row.lastCheckAt">{{ parseTime(scope.row.lastCheckAt, '{y}-{m}-{d} {h}:{i}:{s}') }}</span>
               <span v-else class="ai-muted">-</span>
            </template>
         </el-table-column>
         <el-table-column label="启用" align="center" width="90">
            <template #default="scope">
               <el-switch
                  v-model="scope.row.enabled"
                  :active-value="1"
                  :inactive-value="0"
                  @change="handleStatusChange(scope.row)"
                  v-hasPermi="['ai:channel:edit']"
               />
            </template>
         </el-table-column>
         <el-table-column label="操作" align="center" width="230" fixed="right" class-name="small-padding fixed-width">
            <template #default="scope">
               <el-button link type="primary" icon="Connection" @click="handleTest(scope.row)" v-hasPermi="['ai:channel:test']">测试</el-button>
               <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['ai:channel:edit']">修改</el-button>
               <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['ai:channel:remove']">删除</el-button>
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

      <!-- 新增/修改对话框 -->
      <el-dialog :title="title" v-model="open" width="640px" append-to-body :close-on-click-modal="false">
         <el-form ref="channelRef" :model="form" :rules="rules" label-width="110px">
            <el-form-item label="服务商" prop="providerCode">
               <el-select v-model="form.providerCode" placeholder="请选择服务商" style="width: 100%">
                  <el-option v-for="item in providerOptions" :key="item.code" :label="item.name + '（' + item.protocol + '）'" :value="item.code" />
               </el-select>
            </el-form-item>
            <el-form-item label="渠道名称" prop="name">
               <el-input v-model="form.name" placeholder="如「百炼-主账号」" maxlength="128" />
            </el-form-item>
            <el-form-item label="接口密钥" prop="apiKey">
               <el-input
                  v-model="form.apiKey"
                  type="password"
                  show-password
                  :placeholder="form.id ? '留空表示不修改原密钥' : '请输入接口密钥'"
               />
               <div class="ai-hint">密钥加密后入库，任何接口都不会返回明文；主密钥变更后需要重新填写。</div>
            </el-form-item>
            <el-form-item label="接入点" prop="baseUrl">
               <el-input v-model="form.baseUrl" placeholder="留空则用服务商默认接入点" maxlength="255" />
               <div class="ai-hint">OpenAI 兼容协议必须在这里覆盖成目标厂商的地址，否则只会连 OpenAI 官方。</div>
            </el-form-item>
            <el-row :gutter="20">
               <el-col :span="12">
                  <el-form-item label="降级顺序" prop="priority">
                     <el-input-number v-model="form.priority" :min="1" :max="999" controls-position="right" style="width: 100%" />
                  </el-form-item>
               </el-col>
               <el-col :span="12">
                  <el-form-item label="启用">
                     <el-radio-group v-model="form.enabled">
                        <el-radio :value="1">启用</el-radio>
                        <el-radio :value="0">停用</el-radio>
                     </el-radio-group>
                  </el-form-item>
               </el-col>
            </el-row>
            <el-form-item label="备注" prop="remark">
               <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" />
            </el-form-item>
         </el-form>
         <template #footer>
            <div class="dialog-footer">
               <el-button type="primary" :loading="submitting" @click="submitForm">确 定</el-button>
               <el-button @click="cancel">取 消</el-button>
            </div>
         </template>
      </el-dialog>

      <!-- 按模型测试对话框：逐行测试 + 右上角测试全部 -->
      <el-dialog v-model="testOpen" title="测试渠道连通性" width="720px" append-to-body :close-on-click-modal="false">
         <div class="test-head">
            <span class="test-channel">{{ testChannelName }}</span>
            <span class="test-actions">
               <el-button link type="primary" :disabled="testing" @click="loadTestModels">刷新模型</el-button>
               <el-button
                  link
                  type="primary"
                  :loading="testing && testingId === null"
                  :disabled="!testModels.length || testing"
                  @click="runTestAll"
               >测试全部</el-button>
            </span>
         </div>
         <el-alert
            v-if="!testModels.length"
            type="warning"
            :closable="false"
            show-icon
            title="该渠道下还没有启用的模型，请先在「AI 模型管理」中添加"
         />
         <el-table v-else v-loading="testModelLoading" :data="testModels" max-height="360">
            <el-table-column label="模型名" prop="modelName" min-width="160" :show-overflow-tooltip="true" />
            <el-table-column label="展示名" prop="displayName" min-width="120" :show-overflow-tooltip="true" />
            <el-table-column label="能力" width="100">
               <template #default="scope">
                  <el-tag size="small">{{ capabilityLabel(scope.row.capability) }}</el-tag>
               </template>
            </el-table-column>
            <el-table-column label="测试结果" min-width="180">
               <template #default="scope">
                  <el-tooltip v-if="testResults[scope.row.id!] && !testResults[scope.row.id!].success"
                     :content="testResults[scope.row.id!].message" placement="top">
                     <el-tag type="danger" size="small">失败</el-tag>
                  </el-tooltip>
                  <el-tag v-else-if="testResults[scope.row.id!]?.success" type="success" size="small">成功</el-tag>
                  <span v-else-if="testingId === scope.row.id" class="ai-muted">测试中…</span>
                  <span v-else class="ai-muted">未测试</span>
               </template>
            </el-table-column>
            <el-table-column label="操作" align="center" width="90" fixed="right">
               <template #default="scope">
                  <el-button
                     link
                     type="primary"
                     icon="Connection"
                     :loading="testingId === scope.row.id"
                     :disabled="testing && testingId !== scope.row.id"
                     @click="runTest(scope.row)"
                  >测试</el-button>
               </template>
            </el-table-column>
         </el-table>
         <div v-if="testSummary" class="test-summary">{{ testSummary }}</div>
      </el-dialog>
   </div>
</template>

<script setup lang="ts" name="AiChannel">
import { useAppProxy, formRef } from '@/utils/appProxy'
import { listChannel, getChannel, addChannel, updateChannel, delChannel, changeChannelStatus, testChannel, listProviderOptions } from "@/api/ai/channel"
import { listModel } from "@/api/ai/model"
import type { AiChannel, AiChannelQueryParams, AiProvider } from "@/types/api/ai/channel"
import type { AiModel } from "@/types/api/ai/model"

const proxy = useAppProxy()

const channelList = ref<AiChannel[]>([])
const providerOptions = ref<AiProvider[]>([])
const open = ref<boolean>(false)
const loading = ref<boolean>(true)
const submitting = ref<boolean>(false)
const showSearch = ref<boolean>(true)
const ids = ref<number[]>([])
const single = ref<boolean>(true)
const multiple = ref<boolean>(true)
const total = ref<number>(0)
const title = ref<string>("")

/** 按模型测试弹窗状态 */
const testOpen = ref<boolean>(false)
const testChannelId = ref<number | null>(null)
const testChannelName = ref<string>("")
const testModels = ref<AiModel[]>([])
const testModelLoading = ref<boolean>(false)
const testing = ref<boolean>(false)
/** 正在测试的模型ID；null 表示空闲，配合 testing 区分「测试全部」与单行测试 */
const testingId = ref<number | null>(null)
const testResults = ref<Record<number, { success: boolean; message: string }>>({})
const testSummary = ref<string>("")

const capabilityOptions = [
  { label: "文本对话", value: "CHAT" },
  { label: "图片理解", value: "VISION" },
  { label: "文生图", value: "IMAGE" },
  { label: "文本向量", value: "EMBEDDING" }
]

const data = reactive({
  form: {} as AiChannel,
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    providerCode: undefined,
    name: undefined,
    healthStatus: undefined
  } as AiChannelQueryParams,
  rules: {
    providerCode: [{ required: true, message: "请选择服务商", trigger: "change" }],
    name: [{ required: true, message: "渠道名称不能为空", trigger: "blur" }]
  }
})

const { queryParams, form, rules } = toRefs(data)

/** 服务商标识转展示名 */
function providerLabel(code?: string): string {
  const matched = providerOptions.value.find((item: AiProvider) => item.code === code)
  return matched?.name || code || "-"
}

/** 查询渠道列表 */
function getList() {
  loading.value = true
  listChannel(queryParams.value).then(response => {
    channelList.value = response.rows
    total.value = response.total
    loading.value = false
  }).catch(() => {
    loading.value = false
  })
}

/** 查询服务商下拉选项 */
function getProviderOptions() {
  listProviderOptions().then(response => {
    providerOptions.value = response.data || []
  })
}

/** 取消按钮 */
function cancel() {
  open.value = false
  reset()
}

/** 表单重置 */
function reset() {
  form.value = {
    id: undefined,
    providerCode: undefined,
    name: "",
    apiKey: "",
    baseUrl: "",
    priority: 1,
    enabled: 1,
    remark: ""
  }
  proxy.resetForm("channelRef")
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

/** 多选框选中数据 */
function handleSelectionChange(selection: AiChannel[]) {
  ids.value = selection.map(item => item.id as number)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  open.value = true
  title.value = "新增渠道"
}

/** 修改按钮操作 */
function handleUpdate(row?: AiChannel) {
  reset()
  const id = row?.id || ids.value[0]
  getChannel(id as number).then(response => {
    form.value = response.data as AiChannel
    // 后端返回的是掩码，清空避免把掩码当成新密钥提交
    form.value.apiKey = ""
    open.value = true
    title.value = "修改渠道"
  })
}

/** 提交按钮 */
function submitForm() {
  formRef(proxy.$refs["channelRef"]).validate((valid: boolean) => {
    if (!valid) {
      return
    }
    submitting.value = true
    const request = form.value.id ? updateChannel(form.value) : addChannel(form.value)
    request.then(() => {
      proxy.$modal.msgSuccess(form.value.id ? "修改成功" : "新增成功")
      open.value = false
      getList()
    }).finally(() => {
      submitting.value = false
    })
  })
}

/** 删除按钮操作 */
function handleDelete(row?: AiChannel) {
  const delIds = row?.id ? [row.id] : ids.value
  proxy.$modal.confirm('是否确认删除渠道编号为"' + delIds + '"的数据项？').then(() => {
    return delChannel(delIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

/** 启停操作 */
function handleStatusChange(row: AiChannel) {
  const text = row.enabled === 1 ? "启用" : "停用"
  changeChannelStatus(row.id as number, row.enabled as number).then(() => {
    proxy.$modal.msgSuccess(text + "成功")
  }).catch(() => {
    row.enabled = row.enabled === 1 ? 0 : 1
  })
}

/** 能力类型转展示名 */
function capabilityLabel(capability?: string): string {
  return capabilityOptions.find(item => item.value === capability)?.label || capability || "-"
}

/** 打开按模型测试弹窗 */
function handleTest(row: AiChannel) {
  testChannelId.value = row.id as number
  testChannelName.value = row.name || ""
  testResults.value = {}
  testSummary.value = ""
  testingId.value = null
  testOpen.value = true
  loadTestModels()
}

/** 加载该渠道下的启用模型 */
function loadTestModels() {
  if (!testChannelId.value) {
    return
  }
  testModelLoading.value = true
  listModel({ pageNum: 1, pageSize: 100, channelId: testChannelId.value, enabled: 1 }).then(response => {
    testModels.value = (response.rows || []).filter((m: AiModel) => m.enabled === 1)
    testModelLoading.value = false
  }).catch(() => {
    testModelLoading.value = false
  })
}

/** 测试全部：依次测试该渠道下所有启用模型，汇总成功/失败数 */
async function runTestAll() {
  if (!testChannelId.value || !testModels.value.length) {
    return
  }
  testing.value = true
  testingId.value = null
  testResults.value = {}
  testSummary.value = ""
  let success = 0
  for (const model of testModels.value) {
    if (!model.id) {
      continue
    }
    try {
      const response = await testChannel(testChannelId.value, model.id)
      testResults.value[model.id] = { success: true, message: response.msg || "连通性正常" }
      success++
    } catch (error: any) {
      testResults.value[model.id] = {
        success: false,
        message: error?.message || error?.msg || "连通性检测失败"
      }
    }
  }
  testing.value = false
  testSummary.value = `共 ${testModels.value.length} 个模型，成功 ${success} 个，失败 ${testModels.value.length - success} 个`
  // 健康状态已回写，列表刷新一次
  getList()
}

/** 单行测试：只测这一行的模型，失败原因留在该行的 tooltip 里 */
async function runTest(model: AiModel) {
  if (!testChannelId.value || !model.id || testing.value) {
    return
  }
  testing.value = true
  testingId.value = model.id
  testSummary.value = ""
  try {
    const response = await testChannel(testChannelId.value, model.id)
    testResults.value[model.id] = { success: true, message: response.msg || "连通性正常" }
    proxy.$modal.msgSuccess(`${model.modelName || model.displayName || "该模型"} 连通性正常`)
  } catch (error: any) {
    const message = error?.message || error?.msg || "连通性检测失败"
    testResults.value[model.id] = { success: false, message }
    proxy.$modal.msgError(`${model.modelName || model.displayName || "该模型"} 测试失败：${message}`)
  } finally {
    testingId.value = null
    testing.value = false
    // 单行测试也会回写渠道健康状态
    getList()
  }
}

getProviderOptions()
getList()
</script>

<style scoped>
.ai-muted {
   color: #909399;
}

.ai-mono {
   font-family: Consolas, Monaco, monospace;
   font-size: 12px;
}

.ai-hint {
   color: #909399;
   font-size: 12px;
   line-height: 1.5;
}
.test-head {
   display: flex;
   align-items: center;
   justify-content: space-between;
   margin-bottom: 12px;
}

.test-actions {
   display: flex;
   align-items: center;
   gap: 12px;
}

.test-channel {
   font-weight: 600;
   color: var(--el-text-color-primary);
}

.test-summary {
   margin-top: 12px;
   font-size: 13px;
   color: var(--el-text-color-secondary);
}
</style>
