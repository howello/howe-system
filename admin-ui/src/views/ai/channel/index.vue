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
               <el-button link type="primary" icon="Connection" :loading="testingId === scope.row.id" @click="handleTest(scope.row)" v-hasPermi="['ai:channel:test']">测试</el-button>
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
   </div>
</template>

<script setup lang="ts" name="AiChannel">
import { listChannel, getChannel, addChannel, updateChannel, delChannel, changeChannelStatus, testChannel, listProviderOptions } from "@/api/ai/channel"
import type { AiChannel, AiChannelQueryParams, AiProvider } from "@/types/api/ai/channel"

const { proxy } = getCurrentInstance()

const channelList = ref<AiChannel[]>([])
const providerOptions = ref<AiProvider[]>([])
const open = ref<boolean>(false)
const loading = ref<boolean>(true)
const submitting = ref<boolean>(false)
const showSearch = ref<boolean>(true)
const testingId = ref<number | null>(null)
const ids = ref<number[]>([])
const single = ref<boolean>(true)
const multiple = ref<boolean>(true)
const total = ref<number>(0)
const title = ref<string>("")

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
  proxy.$refs["channelRef"].validate((valid: boolean) => {
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

/** 连通性测试 */
function handleTest(row: AiChannel) {
  testingId.value = row.id as number
  testChannel(row.id as number).then(response => {
    proxy.$modal.msgSuccess(response.msg || "连通性正常")
    getList()
  }).finally(() => {
    testingId.value = null
  })
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
</style>
