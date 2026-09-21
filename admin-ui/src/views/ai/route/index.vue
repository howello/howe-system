<template>
   <div class="app-container">
      <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="80px">
         <el-form-item label="场景标识" prop="scene">
            <el-input v-model="queryParams.scene" placeholder="按场景标识搜索" clearable style="width: 240px" @keyup.enter="handleQuery" />
         </el-form-item>
         <el-form-item label="能力类型" prop="capability">
            <el-select v-model="queryParams.capability" placeholder="全部能力" clearable style="width: 160px">
               <el-option v-for="item in capabilityOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
         </el-form-item>
         <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
         </el-form-item>
      </el-form>

      <el-row :gutter="10" class="mb8">
         <el-col :span="1.5">
            <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['ai:route:edit']">新增</el-button>
         </el-col>
         <el-col :span="1.5">
            <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate()" v-hasPermi="['ai:route:edit']">修改</el-button>
         </el-col>
         <el-col :span="1.5">
            <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['ai:route:remove']">删除</el-button>
         </el-col>
         <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table v-loading="loading" :data="routeList" @selection-change="handleSelectionChange">
         <el-table-column type="selection" width="55" align="center" />
         <el-table-column label="场景标识" align="left" :show-overflow-tooltip="true" min-width="200">
            <template #default="scope">
               <a class="link-type" style="cursor: pointer" @click="handleUpdate(scope.row)">{{ scope.row.scene }}</a>
            </template>
         </el-table-column>
         <el-table-column label="能力类型" align="center" width="110">
            <template #default="scope">
               <el-tag size="small">{{ capabilityLabel(scope.row.capability) }}</el-tag>
            </template>
         </el-table-column>
         <el-table-column label="主模型" align="left" :show-overflow-tooltip="true" min-width="200">
            <template #default="scope">
               <span>{{ scope.row.primaryModelDisplayName || scope.row.primaryModelName || scope.row.primaryModelId }}</span>
            </template>
         </el-table-column>
         <el-table-column label="降级链" align="left" min-width="160">
            <template #default="scope">
               <span v-if="fallbackCount(scope.row) > 0">{{ fallbackCount(scope.row) }} 个备用模型</span>
               <span v-else class="ai-muted">未配置</span>
            </template>
         </el-table-column>
         <el-table-column label="参数覆盖" align="left" :show-overflow-tooltip="true" min-width="200">
            <template #default="scope">
               <span v-if="scope.row.params" class="ai-mono">{{ scope.row.params }}</span>
               <span v-else class="ai-muted">无</span>
            </template>
         </el-table-column>
         <el-table-column label="启用" align="center" width="90">
            <template #default="scope">
               <el-tag v-if="scope.row.enabled === 1" type="success" size="small">启用</el-tag>
               <el-tag v-else type="info" size="small">停用</el-tag>
            </template>
         </el-table-column>
         <el-table-column label="操作" align="center" width="150" fixed="right" class-name="small-padding fixed-width">
            <template #default="scope">
               <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['ai:route:edit']">修改</el-button>
               <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['ai:route:remove']">删除</el-button>
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
      <el-dialog :title="title" v-model="open" width="680px" append-to-body :close-on-click-modal="false">
         <el-form ref="routeRef" :model="form" :rules="rules" label-width="110px">
            <el-form-item label="场景标识" prop="scene">
               <el-input v-model="form.scene" placeholder="如 blog.recipe.cover" maxlength="128" />
               <div class="ai-hint">业务方调用时传的就是这个值，改这里等于换掉整条链路。</div>
            </el-form-item>
            <el-form-item label="能力类型" prop="capability">
               <el-select v-model="form.capability" placeholder="请选择能力类型" style="width: 100%">
                  <el-option v-for="item in capabilityOptions" :key="item.value" :label="item.label" :value="item.value" />
               </el-select>
            </el-form-item>
            <el-form-item label="主模型" prop="primaryModelId">
               <el-select v-model="form.primaryModelId" placeholder="请选择主模型" filterable style="width: 100%">
                  <el-option v-for="item in matchedModels" :key="item.id" :label="modelLabel(item)" :value="item.id as number" />
               </el-select>
            </el-form-item>
            <el-form-item label="降级链">
               <el-select v-model="fallbackSelection" multiple placeholder="主模型失败时依次尝试这些模型" style="width: 100%">
                  <el-option
                     v-for="item in fallbackCandidates"
                     :key="item.id"
                     :label="modelLabel(item)"
                     :value="item.id as number"
                  />
               </el-select>
               <div class="ai-hint">顺序即降级顺序；只在主模型出现超时、厂商 5xx、限流、鉴权失败时才会切换。</div>
            </el-form-item>
            <el-form-item label="参数覆盖">
               <el-input v-model="form.params" type="textarea" :rows="3" placeholder='JSON 对象，如 {"temperature":0.7,"size":"1024*1024","styleSuffixKey":"recipe.photo"}' />
               <div class="ai-hint">优先级：全局默认 &lt; 这里的场景参数 &lt; 请求显式字段。留空表示不覆盖。</div>
            </el-form-item>
            <el-form-item label="启用">
               <el-radio-group v-model="form.enabled">
                  <el-radio :value="1">启用</el-radio>
                  <el-radio :value="0">停用</el-radio>
               </el-radio-group>
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
   </div>
</template>

<script setup lang="ts" name="AiSceneRoute">
import { listRoute, saveRoute, delRoute } from "@/api/ai/route"
import { listModel } from "@/api/ai/model"
import type { AiSceneRoute, AiSceneRouteQueryParams } from "@/types/api/ai/route"
import type { AiModel } from "@/types/api/ai/model"

const { proxy } = getCurrentInstance()

const routeList = ref<AiSceneRoute[]>([])
const modelOptions = ref<AiModel[]>([])
const fallbackSelection = ref<number[]>([])
const open = ref<boolean>(false)
const loading = ref<boolean>(true)
const submitting = ref<boolean>(false)
const showSearch = ref<boolean>(true)
const ids = ref<number[]>([])
const single = ref<boolean>(true)
const multiple = ref<boolean>(true)
const total = ref<number>(0)
const title = ref<string>("")

const capabilityOptions = [
  { label: "文本对话", value: "CHAT" },
  { label: "图片理解", value: "VISION" },
  { label: "文生图", value: "IMAGE" },
  { label: "文本向量", value: "EMBEDDING" }
]

const data = reactive({
  form: {} as AiSceneRoute,
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    scene: undefined,
    capability: undefined
  } as AiSceneRouteQueryParams,
  rules: {
    scene: [{ required: true, message: "场景标识不能为空", trigger: "blur" }],
    capability: [{ required: true, message: "请选择能力类型", trigger: "change" }],
    primaryModelId: [{ required: true, message: "请选择主模型", trigger: "change" }]
  }
})

const { queryParams, form, rules } = toRefs(data)

/** 与当前能力类型匹配的模型 */
const matchedModels = computed(() => modelOptions.value.filter((item: AiModel) => item.capability === form.value.capability))

/** 降级候选：同能力下、排除主模型 */
const fallbackCandidates = computed(() => matchedModels.value.filter((item: AiModel) => item.id !== form.value.primaryModelId))

/** 能力类型转展示名 */
function capabilityLabel(capability?: string): string {
  const matched = capabilityOptions.find((item: { label: string; value: string }) => item.value === capability)
  return matched?.label || capability || "-"
}

/** 模型下拉的展示文案 */
function modelLabel(model: AiModel): string {
  return (model.displayName || model.modelName || "") + "（" + (model.channelName || "未知渠道") + "）"
}

/** 统计降级链长度 */
function fallbackCount(row: AiSceneRoute): number {
  if (!row.fallbackModelIds) {
    return 0
  }
  try {
    const parsed = JSON.parse(row.fallbackModelIds)
    return Array.isArray(parsed) ? parsed.length : 0
  } catch {
    return 0
  }
}

/** 查询场景路由列表 */
function getList() {
  loading.value = true
  listRoute(queryParams.value).then(response => {
    routeList.value = response.rows
    total.value = response.total
    loading.value = false
  }).catch(() => {
    loading.value = false
  })
}

/** 查询模型下拉选项 */
function getModelOptions() {
  listModel({ pageNum: 1, pageSize: 500 }).then(response => {
    modelOptions.value = response.rows
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
    scene: "",
    capability: "IMAGE",
    primaryModelId: undefined,
    fallbackModelIds: undefined,
    params: "",
    enabled: 1,
    remark: ""
  }
  fallbackSelection.value = []
  proxy.resetForm("routeRef")
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
function handleSelectionChange(selection: AiSceneRoute[]) {
  ids.value = selection.map(item => item.id as number)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  open.value = true
  title.value = "新增场景路由"
}

/** 修改按钮操作 */
function handleUpdate(row?: AiSceneRoute) {
  reset()
  const target = row || routeList.value.find((item: AiSceneRoute) => item.id === ids.value[0])
  if (!target) {
    return
  }
  form.value = { ...target }
  // 降级链在库里是 JSON 字符串，表单里用多选，这里做一次转换
  if (target.fallbackModelIds) {
    try {
      const parsed = JSON.parse(target.fallbackModelIds)
      fallbackSelection.value = Array.isArray(parsed) ? parsed : []
    } catch {
      fallbackSelection.value = []
    }
  }
  open.value = true
  title.value = "修改场景路由"
}

/** 校验 JSON 文本是否合法 */
function isValidJsonObject(text?: string): boolean {
  if (!text) {
    return true
  }
  try {
    const parsed = JSON.parse(text)
    return typeof parsed === "object" && parsed !== null && !Array.isArray(parsed)
  } catch {
    return false
  }
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["routeRef"].validate((valid: boolean) => {
    if (!valid) {
      return
    }
    if (!isValidJsonObject(form.value.params)) {
      proxy.$modal.msgError("参数覆盖必须是合法的 JSON 对象")
      return
    }
    const payload: AiSceneRoute = {
      ...form.value,
      fallbackModelIds: fallbackSelection.value.length ? JSON.stringify(fallbackSelection.value) : undefined
    }
    submitting.value = true
    saveRoute(payload).then(() => {
      proxy.$modal.msgSuccess("保存成功，缓存已刷新")
      open.value = false
      getList()
    }).finally(() => {
      submitting.value = false
    })
  })
}

/** 删除按钮操作 */
function handleDelete(row?: AiSceneRoute) {
  const delIds = row?.id ? [row.id] : ids.value
  proxy.$modal.confirm('是否确认删除场景路由编号为"' + delIds + '"的数据项？').then(() => {
    return delRoute(delIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

getModelOptions()
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
