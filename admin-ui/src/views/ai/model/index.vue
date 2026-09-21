<template>
   <div class="app-container">
      <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="80px">
         <el-form-item label="归属渠道" prop="channelId">
            <el-select v-model="queryParams.channelId" placeholder="全部渠道" clearable style="width: 200px">
               <el-option v-for="item in channelOptions" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
         </el-form-item>
         <el-form-item label="能力类型" prop="capability">
            <el-select v-model="queryParams.capability" placeholder="全部能力" clearable style="width: 160px">
               <el-option v-for="item in capabilityOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
         </el-form-item>
         <el-form-item label="模型名" prop="modelName">
            <el-input v-model="queryParams.modelName" placeholder="按模型名搜索" clearable style="width: 200px" @keyup.enter="handleQuery" />
         </el-form-item>
         <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
         </el-form-item>
      </el-form>

      <el-row :gutter="10" class="mb8">
         <el-col :span="1.5">
            <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['ai:model:add']">新增</el-button>
         </el-col>
         <el-col :span="1.5">
            <el-button type="warning" plain icon="Download" @click="handleImport" v-hasPermi="['ai:model:add']">从渠道获取</el-button>
         </el-col>
         <el-col :span="1.5">
            <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate()" v-hasPermi="['ai:model:edit']">修改</el-button>
         </el-col>
         <el-col :span="1.5">
            <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['ai:model:remove']">删除</el-button>
         </el-col>
         <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table v-loading="loading" :data="modelList" @selection-change="handleSelectionChange">
         <el-table-column type="selection" width="55" align="center" />
         <el-table-column label="展示名" align="left" :show-overflow-tooltip="true" min-width="160">
            <template #default="scope">
               <a class="link-type" style="cursor: pointer" @click="handleUpdate(scope.row)">{{ scope.row.displayName || scope.row.modelName }}</a>
            </template>
         </el-table-column>
         <el-table-column label="模型名" align="left" prop="modelName" :show-overflow-tooltip="true" min-width="160">
            <template #default="scope">
               <span class="ai-mono">{{ scope.row.modelName }}</span>
            </template>
         </el-table-column>
         <el-table-column label="能力类型" align="center" width="110">
            <template #default="scope">
               <el-tag size="small">{{ capabilityLabel(scope.row.capability) }}</el-tag>
            </template>
         </el-table-column>
         <el-table-column label="归属渠道" align="left" prop="channelName" :show-overflow-tooltip="true" min-width="150" />
         <el-table-column label="服务商" align="center" prop="providerCode" width="160" />
         <el-table-column label="最大 token" align="center" prop="maxTokens" width="110">
            <template #default="scope">
               <span v-if="scope.row.maxTokens">{{ scope.row.maxTokens }}</span>
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
                  v-hasPermi="['ai:model:edit']"
               />
            </template>
         </el-table-column>
         <el-table-column label="操作" align="center" width="150" fixed="right" class-name="small-padding fixed-width">
            <template #default="scope">
               <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['ai:model:edit']">修改</el-button>
               <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['ai:model:remove']">删除</el-button>
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
         <el-form ref="modelRef" :model="form" :rules="rules" label-width="110px">
            <el-form-item label="归属渠道" prop="channelId">
               <el-select v-model="form.channelId" placeholder="请选择归属渠道" style="width: 100%">
                  <el-option v-for="item in channelOptions" :key="item.id" :label="item.name" :value="item.id" />
               </el-select>
            </el-form-item>
            <el-form-item label="能力类型" prop="capability">
               <el-select v-model="form.capability" placeholder="请选择能力类型" style="width: 100%">
                  <el-option v-for="item in capabilityOptions" :key="item.value" :label="item.label" :value="item.value" />
               </el-select>
               <div class="ai-hint">场景路由按能力类型匹配模型，配错能力会导致场景找不到可用模型。</div>
            </el-form-item>
            <el-form-item label="模型名" prop="modelName">
               <el-input v-model="form.modelName" placeholder="传给厂商的名字，如 qwen-plus" maxlength="128" />
            </el-form-item>
            <el-form-item label="展示名" prop="displayName">
               <el-input v-model="form.displayName" placeholder="如「通义千问 Plus」" maxlength="128" />
            </el-form-item>
            <el-row :gutter="20">
               <el-col :span="12">
                  <el-form-item label="最大 token" prop="maxTokens">
                     <el-input-number v-model="form.maxTokens" :min="1" controls-position="right" style="width: 100%" />
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
            <el-form-item label="扩展配置" prop="extra">
               <el-input v-model="form.extra" type="textarea" :rows="4" placeholder='JSON 对象，如 {"extraBody":{"thinking":{"type":"disabled"}}}' />
               <div class="ai-hint">
                  extraBody 里的内容会原样并入厂商请求体，用来透传协议特有参数（如 DeepSeek 的 thinking、reasoning_effort）；
                  把 reasoning 设为 true 时，流式会改为自己解析上游，把推理模型的思考内容一并返回给调用方。必须是合法的 JSON 对象，写错会被拒绝保存。
               </div>
            </el-form-item>
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

      <!-- 从渠道获取模型 -->
      <el-dialog title="从渠道获取模型" v-model="importOpen" width="760px" append-to-body :close-on-click-modal="false">
         <el-form label-width="80px">
            <el-form-item label="归属渠道">
               <el-select
                  v-model="importChannelId"
                  placeholder="请选择要拉取模型的渠道"
                  style="width: 320px"
                  @change="loadRemoteModels"
               >
                  <el-option v-for="item in channelOptions" :key="item.id" :label="item.name" :value="item.id" />
               </el-select>
               <el-button class="ml10" plain icon="Refresh" :disabled="!importChannelId" @click="loadRemoteModels">刷新</el-button>
            </el-form-item>
         </el-form>
         <div class="ai-hint mb8">
            模型清单来自服务商的 models 接口。能力类型由模型名推测，导入前请逐行确认；已配置的模型不可重复选择。
         </div>
         <el-row :gutter="10" class="mb8">
            <el-col :span="10">
               <el-input v-model="importKeyword" placeholder="按模型名筛选" clearable prefix-icon="Search" />
            </el-col>
            <el-col :span="14">
               <el-checkbox v-model="onlyNew">仅显示未添加</el-checkbox>
               <span class="ai-muted ml10">已选 {{ importSelection.length }} 项</span>
            </el-col>
         </el-row>
         <el-table
            v-loading="importLoading"
            :data="filteredRemoteModels"
            height="360px"
            @selection-change="handleImportSelectionChange"
         >
            <el-table-column type="selection" width="55" align="center" :selectable="importSelectable" />
            <el-table-column label="模型名" align="left" prop="modelName" :show-overflow-tooltip="true" min-width="220">
               <template #default="scope">
                  <span class="ai-mono">{{ scope.row.modelName }}</span>
               </template>
            </el-table-column>
            <el-table-column label="状态" align="center" width="100">
               <template #default="scope">
                  <el-tag v-if="scope.row.added" type="info" size="small">已添加</el-tag>
                  <el-tag v-else type="success" size="small">未添加</el-tag>
               </template>
            </el-table-column>
            <el-table-column label="能力类型" align="center" width="150">
               <template #default="scope">
                  <el-select v-model="scope.row.capability" size="small" :disabled="scope.row.added">
                     <el-option v-for="item in capabilityOptions" :key="item.value" :label="item.label" :value="item.value" />
                  </el-select>
               </template>
            </el-table-column>
            <template #empty>
               <span class="ai-muted">{{ importChannelId ? "没有可导入的模型" : "请先选择渠道" }}</span>
            </template>
         </el-table>
         <template #footer>
            <div class="dialog-footer">
               <el-button type="primary" :loading="importSubmitting" @click="submitImport">导 入</el-button>
               <el-button @click="importOpen = false">取 消</el-button>
            </div>
         </template>
      </el-dialog>
   </div>
</template>

<script setup lang="ts" name="AiModel">
import { listModel, getModel, addModel, updateModel, delModel, changeModelStatus, listRemoteModel, batchAddModel } from "@/api/ai/model"
import { listChannel } from "@/api/ai/channel"
import type { AiModel, AiModelQueryParams, AiRemoteModel } from "@/types/api/ai/model"
import type { AiChannel } from "@/types/api/ai/channel"

/** 导入列表的一行：厂商模型 + 可编辑的能力类型 */
interface ImportRow extends AiRemoteModel {
  capability: string
}

const { proxy } = getCurrentInstance()

const modelList = ref<AiModel[]>([])
const channelOptions = ref<AiChannel[]>([])
const open = ref<boolean>(false)
const loading = ref<boolean>(true)
const submitting = ref<boolean>(false)
const showSearch = ref<boolean>(true)
const ids = ref<number[]>([])
const single = ref<boolean>(true)
const multiple = ref<boolean>(true)
const total = ref<number>(0)
const title = ref<string>("")

// 从渠道获取模型
const importOpen = ref<boolean>(false)
const importLoading = ref<boolean>(false)
const importSubmitting = ref<boolean>(false)
const importChannelId = ref<number>()
const importRows = ref<ImportRow[]>([])
const importSelection = ref<ImportRow[]>([])
const importKeyword = ref<string>("")
const onlyNew = ref<boolean>(true)

/** 按关键词与「仅显示未添加」过滤后的导入列表 */
const filteredRemoteModels = computed(() => {
  const keyword = importKeyword.value.trim().toLowerCase()
  return importRows.value.filter((row: ImportRow) => {
    if (onlyNew.value && row.added) {
      return false
    }
    return !keyword || row.modelName.toLowerCase().includes(keyword)
  })
})

const capabilityOptions = [
  { label: "文本对话", value: "CHAT" },
  { label: "图片理解", value: "VISION" },
  { label: "文生图", value: "IMAGE" },
  { label: "文本向量", value: "EMBEDDING" }
]

const data = reactive({
  form: {} as AiModel,
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    channelId: undefined,
    capability: undefined,
    modelName: undefined
  } as AiModelQueryParams,
  rules: {
    channelId: [{ required: true, message: "请选择归属渠道", trigger: "change" }],
    capability: [{ required: true, message: "请选择能力类型", trigger: "change" }],
    modelName: [{ required: true, message: "模型名不能为空", trigger: "blur" }]
  }
})

const { queryParams, form, rules } = toRefs(data)

/** 能力类型转展示名 */
function capabilityLabel(capability?: string): string {
  const matched = capabilityOptions.find((item: { label: string; value: string }) => item.value === capability)
  return matched?.label || capability || "-"
}

/** 查询模型列表 */
function getList() {
  loading.value = true
  listModel(queryParams.value).then(response => {
    modelList.value = response.rows
    total.value = response.total
    loading.value = false
  }).catch(() => {
    loading.value = false
  })
}

/** 查询渠道下拉选项 */
function getChannelOptions() {
  listChannel({ pageNum: 1, pageSize: 200 }).then(response => {
    channelOptions.value = response.rows
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
    channelId: undefined,
    capability: "CHAT",
    modelName: "",
    displayName: "",
    maxTokens: undefined,
    extra: "",
    enabled: 1,
    remark: ""
  }
  proxy.resetForm("modelRef")
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
function handleSelectionChange(selection: AiModel[]) {
  ids.value = selection.map(item => item.id as number)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  open.value = true
  title.value = "新增模型"
}

/** 修改按钮操作 */
function handleUpdate(row?: AiModel) {
  reset()
  const id = row?.id || ids.value[0]
  getModel(id as number).then(response => {
    form.value = response.data as AiModel
    open.value = true
    title.value = "修改模型"
  })
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["modelRef"].validate((valid: boolean) => {
    if (!valid) {
      return
    }
    submitting.value = true
    const request = form.value.id ? updateModel(form.value) : addModel(form.value)
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
function handleDelete(row?: AiModel) {
  const delIds = row?.id ? [row.id] : ids.value
  proxy.$modal.confirm('是否确认删除模型编号为"' + delIds + '"的数据项？').then(() => {
    return delModel(delIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

/** 启停操作 */
function handleStatusChange(row: AiModel) {
  const text = row.enabled === 1 ? "启用" : "停用"
  changeModelStatus(row.id as number, row.enabled as number).then(() => {
    proxy.$modal.msgSuccess(text + "成功")
  }).catch(() => {
    row.enabled = row.enabled === 1 ? 0 : 1
  })
}

/** 打开「从渠道获取模型」对话框 */
function handleImport() {
  importChannelId.value = queryParams.value.channelId
  importRows.value = []
  importSelection.value = []
  importKeyword.value = ""
  onlyNew.value = true
  importOpen.value = true
  if (importChannelId.value) {
    loadRemoteModels()
  }
}

/** 拉取所选渠道的厂商模型列表 */
function loadRemoteModels() {
  if (!importChannelId.value) {
    proxy.$modal.msgWarning("请先选择渠道")
    return
  }
  importLoading.value = true
  importRows.value = []
  importSelection.value = []
  listRemoteModel(importChannelId.value).then(response => {
    importRows.value = (response.data || []).map(item => ({ ...item, capability: item.suggestedCapability }))
  }).finally(() => {
    importLoading.value = false
  })
}

/** 已添加的模型不可勾选 */
function importSelectable(row: ImportRow): boolean {
  return !row.added
}

/** 导入列表勾选变化 */
function handleImportSelectionChange(selection: ImportRow[]) {
  importSelection.value = selection
}

/** 提交导入 */
function submitImport() {
  if (!importSelection.value.length) {
    proxy.$modal.msgWarning("请至少选择一个模型")
    return
  }
  importSubmitting.value = true
  batchAddModel({
    channelId: importChannelId.value as number,
    models: importSelection.value.map((row: ImportRow) => ({ modelName: row.modelName, capability: row.capability }))
  }).then(response => {
    const rows = response.data || 0
    if (rows > 0) {
      proxy.$modal.msgSuccess("成功导入 " + rows + " 个模型")
    } else {
      proxy.$modal.msgWarning("所选模型均已存在，未新增")
    }
    importOpen.value = false
    getList()
  }).finally(() => {
    importSubmitting.value = false
  })
}

getChannelOptions()
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
