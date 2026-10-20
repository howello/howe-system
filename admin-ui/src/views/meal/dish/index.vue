<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="菜名" prop="name">
        <el-input v-model="queryParams.name" placeholder="请输入菜名" clearable style="width: 180px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="关键词" prop="keyword">
        <el-input v-model="queryParams.keyword" placeholder="菜名或用料" clearable style="width: 180px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="分类" prop="categoryId">
        <el-select v-model="queryParams.categoryId" placeholder="分类" clearable style="width: 180px">
          <el-option v-for="item in categoryOptions" :key="item.categoryId" :label="item.name" :value="item.categoryId" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="状态" clearable style="width: 140px">
          <el-option label="上架" value="0" />
          <el-option label="下架" value="1" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['meal:dish:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate()" v-hasPermi="['meal:dish:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['meal:dish:remove']">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="MagicStick"
          :disabled="multiple"
          :loading="batchAiLoading"
          @click="handleBatchAi"
          v-hasPermi="['meal:dish:edit']"
        >
          AI 优化
        </el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <div v-if="batchAiLoading" class="batch-ai">
      <el-progress :percentage="batchPercent" :stroke-width="10" />
      <div class="batch-ai__text">
        正在处理 {{ batchIndex }}/{{ batchTotal }}：{{ batchName }}
        <span class="ai-step" :class="stepClass(batchTextState)">
          <span class="ai-step__icon">{{ stepIcon(batchTextState) }}</span>菜品信息 · {{ stepText(batchTextState) }}
        </span>
        <span class="ai-step" :class="stepClass(batchCoverState)">
          <span class="ai-step__icon">{{ stepIcon(batchCoverState) }}</span>封面图 · {{ stepText(batchCoverState) }}
        </span>
      </div>
    </div>

    <el-table v-loading="loading" :data="dishList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="封面" align="center" width="80">
        <template #default="scope">
          <el-image v-if="scope.row.cover" :src="scope.row.cover" :preview-src-list="[scope.row.cover]" fit="cover" style="width: 40px; height: 40px; border-radius: 6px" preview-teleported />
          <el-avatar v-else :size="40">{{ (scope.row.name || "?").charAt(0) }}</el-avatar>
        </template>
      </el-table-column>
      <el-table-column label="菜名" align="left" prop="name" :show-overflow-tooltip="true" min-width="140">
        <template #default="scope">
          <a class="link-type" style="cursor: pointer" @click="handleUpdate(scope.row)">{{ scope.row.name }}</a>
        </template>
      </el-table-column>
      <el-table-column label="分类" align="center" prop="categoryNames" :show-overflow-tooltip="true" min-width="120">
        <template #default="scope">
          <el-tag v-if="scope.row.categoryNames" size="small">{{ scope.row.categoryNames }}</el-tag>
          <el-tag v-else type="warning" size="small">待分类</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="归属" align="center" width="100">
        <template #default="scope">
          <el-tag :type="scope.row.deptId === 0 ? 'warning' : 'success'" size="small">
            {{ scope.row.deptId === 0 ? "公共" : "本家庭" }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="简介" align="left" prop="description" :show-overflow-tooltip="true" min-width="180" />
      <el-table-column label="耗时" align="center" prop="duration" width="100" />
      <el-table-column label="难度" align="center" prop="level" width="90" />
      <el-table-column label="状态" align="center" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.status === '0' ? 'success' : 'info'" size="small">
            {{ scope.row.status === "0" ? "上架" : "下架" }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="来源" align="center" width="110">
        <template #default="scope">
          <el-tag :type="scope.row.source === '1' ? 'warning' : 'info'" size="small">
            {{ scope.row.source === "1" ? "新菜通过" : "自建" }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="150" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['meal:dish:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['meal:dish:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog :title="title" v-model="open" width="760px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="一键 AI">
          <div class="ai-field">
            <el-button type="primary" plain icon="MagicStick" :loading="aiLoading" @click="handleAiGenerate">一键 AI 补齐</el-button>
            <span class="ai-tip">填写菜名后点击，仅补齐空字段，不覆盖已填内容</span>
          </div>
          <div v-if="aiLoading" class="ai-steps">
            <span class="ai-step" :class="stepClass(aiTextState)">
              <span class="ai-step__icon">{{ stepIcon(aiTextState) }}</span>菜品信息 · {{ stepText(aiTextState) }}
            </span>
            <span class="ai-step" :class="stepClass(aiCoverState)">
              <span class="ai-step__icon">{{ stepIcon(aiCoverState) }}</span>封面图 · {{ stepText(aiCoverState) }}
            </span>
          </div>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="菜名" prop="name">
              <el-input v-model="form.name" placeholder="请输入菜名" maxlength="128" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="分类" prop="categoryIds">
              <el-select v-model="selectedCategoryId" placeholder="请选择唯一主类（单选）" clearable style="width: 100%">
                <el-option v-for="item in categoryOptions" :key="item.categoryId" :label="item.name" :value="item.categoryId" />
              </el-select>
              <div class="form-tip">一道菜只能归一个主类；留空表示待分类</div>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item v-if="isAdmin && !form.dishId" label="归属">
          <el-checkbox v-model="asPublic">建为平台公共菜谱（所有家庭可见）</el-checkbox>
        </el-form-item>
        <el-form-item label="封面地址" prop="cover">
          <div class="cover-field">
            <el-input v-model="form.cover" placeholder="选填，图片地址（/common/upload 返回的 URL）" />
            <el-image
              v-if="form.cover"
              :src="form.cover"
              :preview-src-list="[form.cover]"
              fit="cover"
              class="cover-thumb"
              preview-teleported
            />
          </div>
        </el-form-item>
        <el-form-item label="简介" prop="description">
          <el-input v-model="form.description" placeholder="一句话简介" maxlength="500" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="标签" prop="tags">
              <el-input v-model="form.tags" placeholder="逗号分隔，如 家常,下饭" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="耗时" prop="duration">
              <el-input v-model="form.duration" placeholder="如 90 分钟" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="难度" prop="level">
              <el-input v-model="form.level" placeholder="如 中等" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="份量" prop="serve">
              <el-input v-model="form.serve" placeholder="如 3 人份" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="热量" prop="kcal">
              <el-input v-model="form.kcal" placeholder="如 520 千卡" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="排序" prop="sort">
              <el-input-number v-model="form.sort" :min="0" controls-position="right" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="用料" prop="ingredients">
          <div class="list-editor">
            <div v-for="(item, index) in ingredientList" :key="index" class="list-editor__row">
              <el-input v-model="item.name" placeholder="用料名称，如 带皮五花肉" class="list-editor__grow" />
              <el-input v-model="item.amount" placeholder="用量，如 600 g" class="list-editor__amount" />
              <el-button link type="danger" icon="Delete" class="list-editor__op" @click="removeIngredient(index)" />
            </div>
            <el-button link type="primary" icon="Plus" @click="addIngredient">添加用料</el-button>
          </div>
        </el-form-item>
        <el-form-item label="做法" prop="steps">
          <div class="list-editor">
            <div v-for="(item, index) in stepList" :key="index" class="list-editor__row list-editor__row--step">
              <span class="list-editor__index">{{ index + 1 }}</span>
              <el-input
                v-model="stepList[index]"
                type="textarea"
                :autosize="{ minRows: 1, maxRows: 4 }"
                placeholder="描述这一步的做法"
                class="list-editor__grow"
              />
              <div class="list-editor__ops">
                <el-button link icon="Top" :disabled="index === 0" @click="moveStep(index, -1)" />
                <el-button link icon="Bottom" :disabled="index === stepList.length - 1" @click="moveStep(index, 1)" />
                <el-button link type="danger" icon="Delete" @click="removeStep(index)" />
              </div>
            </div>
            <el-button link type="primary" icon="Plus" @click="addStep">添加步骤</el-button>
          </div>
        </el-form-item>
        <el-form-item label="小贴士" prop="tips">
          <div class="list-editor">
            <div v-for="(item, index) in tipList" :key="index" class="list-editor__row">
              <el-input v-model="tipList[index]" placeholder="如 炒糖色全程小火" class="list-editor__grow" />
              <el-button link type="danger" icon="Delete" class="list-editor__op" @click="removeTip(index)" />
            </div>
            <el-button link type="primary" icon="Plus" @click="addTip">添加小贴士</el-button>
          </div>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio value="0">上架</el-radio>
            <el-radio value="1">下架</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" />
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

<script setup lang="ts" name="MealDish">
import { getDish, listDish, addDish, updateDish, delDish, aiGenerateDishText, aiGenerateDishCover } from "@/api/meal/dish"
import { listCategory } from "@/api/meal/category"
import type { MealDish, MealDishQueryParams, MealDishAiCurrent, MealDishAiTextResult } from "@/types/api/meal/dish"
import type { MealCategory } from "@/types/api/meal/category"
import useUserStore from "@/store/modules/user"

/** AI 两步任务状态 */
type StepState = "idle" | "loading" | "done" | "error" | "skip"

const { proxy } = getCurrentInstance() as any

const userStore = useUserStore()
const isAdmin = computed(() => userStore.roles.includes("admin"))

const dishList = ref<MealDish[]>([])
const categoryOptions = ref<MealCategory[]>([])
const open = ref<boolean>(false)
const loading = ref<boolean>(true)
const submitting = ref<boolean>(false)
const aiTextState = ref<StepState>("idle")
const aiCoverState = ref<StepState>("idle")
const aiLoading = computed(() => aiTextState.value === "loading" || aiCoverState.value === "loading")
const batchAiLoading = ref<boolean>(false)
const batchTotal = ref<number>(0)
const batchDone = ref<number>(0)
const batchIndex = ref<number>(0)
const batchName = ref<string>("")
const batchTextState = ref<StepState>("idle")
const batchCoverState = ref<StepState>("idle")
const batchPercent = computed(() => (batchTotal.value ? Math.round((batchDone.value / batchTotal.value) * 100) : 0))
const showSearch = ref<boolean>(true)
const ids = ref<number[]>([])
const single = ref<boolean>(true)
const multiple = ref<boolean>(true)
const total = ref<number>(0)
const title = ref<string>("")
const asPublic = ref<boolean>(false)

/** 表单里的唯一主类（单选；提交时包成单元素 categoryIds） */
const selectedCategoryId = ref<number | undefined>(undefined)

/** 可视化编辑用的三段列表：用料、做法、小贴士 */
interface IngredientItem {
  name: string
  amount: string
}
const ingredientList = ref<IngredientItem[]>([])
const stepList = ref<string[]>([])
const tipList = ref<string[]>([])

const data = reactive({
  form: {} as MealDish,
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    name: undefined,
    keyword: undefined,
    categoryId: undefined,
    status: undefined
  } as MealDishQueryParams,
  rules: {
    name: [{ required: true, message: "菜名不能为空", trigger: "blur" }]
  }
})

const { queryParams, form, rules } = toRefs(data)

function getList() {
  loading.value = true
  listDish(queryParams.value)
    .then((response: any) => {
      dishList.value = response.rows
      total.value = response.total
    })
    .finally(() => {
      loading.value = false
    })
}

function loadCategories() {
  listCategory({ pageNum: 1, pageSize: 200 })
    .then((response: any) => {
      categoryOptions.value = response.rows
    })
    .catch(() => {})
}

/** 解析用料 JSON（[{name, amount}]），非法或非数组返回空 */
function parseIngredients(raw?: string): IngredientItem[] {
  if (!raw) {
    return []
  }
  try {
    const arr = JSON.parse(raw)
    if (!Array.isArray(arr)) {
      return []
    }
    return arr.map((it: any) => ({ name: String(it?.name ?? ""), amount: String(it?.amount ?? "") }))
  } catch (e) {
    return []
  }
}

/** 解析字符串数组 JSON（做法 / 小贴士），非法或非数组返回空 */
function parseStringList(raw?: string): string[] {
  if (!raw) {
    return []
  }
  try {
    const arr = JSON.parse(raw)
    if (!Array.isArray(arr)) {
      return []
    }
    return arr.map((it: any) => (typeof it === "string" ? it : String(it ?? "")))
  } catch (e) {
    return []
  }
}

/** 把表单里的三段 JSON 解析进可视化列表 */
function loadListsFromForm() {
  ingredientList.value = parseIngredients(form.value.ingredients)
  stepList.value = parseStringList(form.value.steps)
  tipList.value = parseStringList(form.value.tips)
}

/** 把可视化列表序列化回表单字段；空列表置空，提交时由后端按「未填」处理 */
function saveListsToForm() {
  const ingredients = ingredientList.value
    .map((it: IngredientItem) => ({ name: (it.name || "").trim(), amount: (it.amount || "").trim() }))
    .filter((it: IngredientItem) => it.name || it.amount)
  const steps = stepList.value.map((s: string) => (s || "").trim()).filter((s: string) => s)
  const tips = tipList.value.map((t: string) => (t || "").trim()).filter((t: string) => t)
  form.value.ingredients = ingredients.length ? JSON.stringify(ingredients) : undefined
  form.value.steps = steps.length ? JSON.stringify(steps) : undefined
  form.value.tips = tips.length ? JSON.stringify(tips) : undefined
}

function addIngredient() {
  ingredientList.value.push({ name: "", amount: "" })
}

function removeIngredient(index: number) {
  ingredientList.value.splice(index, 1)
}

function addStep() {
  stepList.value.push("")
}

function removeStep(index: number) {
  stepList.value.splice(index, 1)
}

/** 做法步骤上下移动 */
function moveStep(index: number, offset: number) {
  const target = index + offset
  if (target < 0 || target >= stepList.value.length) {
    return
  }
  const list = stepList.value
  ;[list[index], list[target]] = [list[target], list[index]]
}

function addTip() {
  tipList.value.push("")
}

function removeTip(index: number) {
  tipList.value.splice(index, 1)
}

function cancel() {
  open.value = false
  reset()
}

function reset() {
  asPublic.value = false
  selectedCategoryId.value = undefined
  ingredientList.value = []
  stepList.value = []
  tipList.value = []
  form.value = {
    dishId: undefined,
    name: undefined,
    categoryIds: [],
    cover: undefined,
    description: undefined,
    tags: undefined,
    duration: undefined,
    level: undefined,
    serve: undefined,
    kcal: undefined,
    ingredients: undefined,
    steps: undefined,
    tips: undefined,
    status: "0",
    sort: 0,
    remark: undefined
  }
  proxy.resetForm("formRef")
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

function handleSelectionChange(selection: MealDish[]) {
  ids.value = selection.map((item) => item.dishId as number)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

function handleAdd() {
  reset()
  loadCategories()
  open.value = true
  title.value = "新增菜品"
}

function handleUpdate(row?: MealDish) {
  reset()
  loadCategories()
  const dishId = row?.dishId || ids.value[0]
  getDish(dishId).then((response: any) => {
    form.value = response.data
    // 唯一主类：详情最多返回 1 个，回填到单选控件
    selectedCategoryId.value =
      form.value.categoryIds && form.value.categoryIds.length ? form.value.categoryIds[0] : undefined
    loadListsFromForm()
    open.value = true
    title.value = "修改菜品"
  })
}

/** 进度行图标 */
function stepIcon(state: StepState): string {
  if (state === "done") return "✓"
  if (state === "error") return "✕"
  if (state === "skip") return "—"
  return "●"
}

/** 进度行文案 */
function stepText(state: StepState): string {
  const map: Record<StepState, string> = {
    idle: "等待中",
    loading: "进行中",
    done: "已完成",
    error: "失败",
    skip: "已跳过"
  }
  return map[state]
}

/** 进度行样式 */
function stepClass(state: StepState): Record<string, boolean> {
  return {
    "is-active": state === "loading",
    "is-done": state === "done",
    "is-error": state === "error"
  }
}

/** 把文本补齐结果回填到表单（只填空字段） */
function applyTextResult(result: MealDishAiTextResult) {
  const fields = result.fields || {}
  Object.keys(fields).forEach((key) => {
    const value = fields[key]
    if (value !== undefined && value !== null && value !== "") {
      ;(form.value as any)[key] = value
    }
  })
  // 只在用户没选分类时回填唯一主类，避免覆盖已选
  if (result.matchedCategoryId && !selectedCategoryId.value) {
    selectedCategoryId.value = result.matchedCategoryId
  }
  if (result.unmatchedCategoryName) {
    proxy.$modal.msgWarning(`「${result.unmatchedCategoryName}」不在 9 个主类中，未回填`)
  }
  // 用料 / 做法 / 小贴士 可能被补齐，重新解析进可视化列表
  loadListsFromForm()
}

/** 一键 AI：文本与封面并行请求，各自回填；已填封面时跳过封面请求 */
async function handleAiGenerate() {
  const name = (form.value.name || "").trim()
  if (!name) {
    proxy.$modal.msgWarning("请先填写菜名")
    return
  }
  if (aiLoading.value) {
    return
  }
  // 先把可视化列表写回表单，保证 current 快照包含用户已填的用料 / 做法 / 小贴士
  saveListsToForm()
  const current = {
    categoryIds: selectedCategoryId.value ? [selectedCategoryId.value] : undefined,
    description: form.value.description || undefined,
    cover: form.value.cover || undefined,
    tags: form.value.tags || undefined,
    duration: form.value.duration || undefined,
    level: form.value.level || undefined,
    serve: form.value.serve || undefined,
    kcal: form.value.kcal || undefined,
    ingredients: form.value.ingredients || undefined,
    steps: form.value.steps || undefined,
    tips: form.value.tips || undefined
  }
  const deptId = isAdmin.value && !form.value.dishId && asPublic.value ? 0 : undefined
  const payload = { name, deptId, current }
  const needCover = !form.value.cover

  aiTextState.value = "loading"
  aiCoverState.value = needCover ? "loading" : "skip"

  const textTask = aiGenerateDishText(payload)
    .then((response: any) => {
      applyTextResult(response.data || {})
      aiTextState.value = "done"
    })
    .catch(() => {
      aiTextState.value = "error"
    })

  const coverTask = needCover
    ? aiGenerateDishCover(payload)
        .then((response: any) => {
          const result = response.data || {}
          if (result.cover) {
            form.value.cover = result.cover
            aiCoverState.value = "done"
          } else {
            aiCoverState.value = "error"
            if (result.imageError) {
              proxy.$modal.msgWarning(`封面图未生成：${result.imageError}`)
            }
          }
        })
        .catch(() => {
          aiCoverState.value = "error"
        })
    : Promise.resolve()

  await Promise.allSettled([textTask, coverTask])
  finishAiSummary()
}

/** 两步都结束后汇总提示（放在函数里读取状态，避免 TS 把赋值后的类型收窄） */
function finishAiSummary() {
  if (aiTextState.value === "done" && aiCoverState.value !== "error") {
    proxy.$modal.msgSuccess("已补齐空字段")
  }
}

/** 批量 AI 优化：对选中的多个菜品逐个补齐空字段并自动保存 */
function handleBatchAi() {
  const selected = dishList.value.filter((item: MealDish) => ids.value.includes(item.dishId as number))
  if (!selected.length) {
    proxy.$modal.msgWarning("请先选择菜品")
    return
  }
  proxy.$modal
    .confirm(`将对选中的 ${selected.length} 个菜品逐个执行 AI 补齐并自动保存，是否继续？`)
    .then(() => runBatchAi(selected))
    .catch(() => {})
}

/** 批量：当前菜品文本失败且封面未成功时视为失败 */
function batchCurrentFailed(): boolean {
  return batchTextState.value === "error" && batchCoverState.value !== "done"
}

/** 依次处理每个菜品：拉取详情 → 文本/封面并行补齐 → 自动提交；单条失败不中断 */
async function runBatchAi(rows: MealDish[]) {
  batchAiLoading.value = true
  batchTotal.value = rows.length
  batchDone.value = 0
  let success = 0
  const failed: string[] = []
  for (let i = 0; i < rows.length; i++) {
    const row = rows[i]
    batchIndex.value = i + 1
    batchName.value = row.name || String(row.dishId)
    batchTextState.value = "idle"
    batchCoverState.value = "idle"
    try {
      const detailResp: any = await getDish(row.dishId as number)
      const dish: MealDish = detailResp.data || row
      const current: MealDishAiCurrent = {
        categoryIds: dish.categoryIds && dish.categoryIds.length ? dish.categoryIds : undefined,
        description: dish.description || undefined,
        cover: dish.cover || undefined,
        tags: dish.tags || undefined,
        duration: dish.duration || undefined,
        level: dish.level || undefined,
        serve: dish.serve || undefined,
        kcal: dish.kcal || undefined,
        ingredients: dish.ingredients || undefined,
        steps: dish.steps || undefined,
        tips: dish.tips || undefined
      }
      const payload = { name: dish.name as string, current }
      const needCover = !dish.cover
      batchTextState.value = "loading"
      batchCoverState.value = needCover ? "loading" : "skip"

      const textTask = aiGenerateDishText(payload)
        .then((resp: any) => {
          const result = resp.data || {}
          const fields = result.fields || {}
          Object.keys(fields).forEach((key) => {
            const value = fields[key]
            if (value !== undefined && value !== null && value !== "") {
              ;(dish as any)[key] = value
            }
          })
          if (result.matchedCategoryId && !(dish.categoryIds && dish.categoryIds.length)) {
            dish.categoryIds = [result.matchedCategoryId]
          }
          batchTextState.value = "done"
        })
        .catch(() => {
          batchTextState.value = "error"
        })

      const coverTask = needCover
        ? aiGenerateDishCover(payload)
            .then((resp: any) => {
              const result = resp.data || {}
              if (result.cover) {
                dish.cover = result.cover
                batchCoverState.value = "done"
              } else {
                batchCoverState.value = "error"
              }
            })
            .catch(() => {
              batchCoverState.value = "error"
            })
        : Promise.resolve()

      await Promise.allSettled([textTask, coverTask])

      // 文本失败且封面也没成功 → 视为失败，不保存
      if (batchCurrentFailed()) {
        throw new Error("AI 生成失败")
      }

      // 修改时不清空/变更归属，与单条编辑保持一致
      dish.deptId = undefined
      await updateDish(dish)
      success++
    } catch (e) {
      failed.push(row.name || String(row.dishId))
    } finally {
      batchDone.value = i + 1
    }
  }
  batchAiLoading.value = false
  batchTotal.value = 0
  batchDone.value = 0
  batchIndex.value = 0
  batchName.value = ""
  batchTextState.value = "idle"
  batchCoverState.value = "idle"
  getList()
  if (failed.length) {
    proxy.$modal.msgWarning(`AI 优化完成：成功 ${success} 个，失败 ${failed.length} 个（${failed.join("、")}）`)
  } else {
    proxy.$modal.msgSuccess(`AI 优化完成：成功 ${success} 个`)
  }
}

function submitForm() {
  proxy.$refs["formRef"].validate((valid: boolean) => {
    if (!valid) {
      return
    }
    // 可视化列表统一序列化后再提交
    saveListsToForm()
    // 唯一主类：单选值包成单元素列表，空值提交空数组表示待分类
    form.value.categoryIds = selectedCategoryId.value ? [selectedCategoryId.value] : []
    submitting.value = true
    const payload: MealDish = { ...form.value }
    if (!payload.dishId) {
      payload.deptId = asPublic.value ? 0 : undefined
    } else {
      payload.deptId = undefined
    }
    const action = payload.dishId ? updateDish(payload) : addDish(payload)
    action
      .then(() => {
        proxy.$modal.msgSuccess(payload.dishId ? "修改成功" : "新增成功")
        open.value = false
        getList()
      })
      .finally(() => {
        submitting.value = false
      })
  })
}

function handleDelete(row?: MealDish) {
  const dishIds = row?.dishId ? [row.dishId] : ids.value
  proxy.$modal
    .confirm("是否确认删除选中的菜品？历史订单里的菜名与封面是快照，不受影响。")
    .then(() => delDish(dishIds))
    .then(() => {
      getList()
      proxy.$modal.msgSuccess("删除成功")
    })
    .catch(() => {})
}

getList()
loadCategories()
</script>

<style scoped>
.ai-tip {
  margin-left: 12px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.form-tip {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}

.cover-field {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}

.cover-field .el-input {
  flex: 1;
}

.cover-thumb {
  width: 40px;
  height: 40px;
  border-radius: 6px;
  flex-shrink: 0;
  cursor: pointer;
}

/* 用料 / 做法 / 小贴士 可视化列表编辑器 */
.list-editor {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.list-editor__row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.list-editor__row--step {
  align-items: flex-start;
}

.list-editor__grow {
  flex: 1;
}

.list-editor__amount {
  width: 140px;
  flex-shrink: 0;
}

.list-editor__index {
  width: 24px;
  height: 24px;
  line-height: 24px;
  text-align: center;
  border-radius: 50%;
  background: var(--el-fill-color);
  color: var(--el-text-color-regular);
  font-size: 12px;
  flex-shrink: 0;
  margin-top: 5px;
}

.list-editor__op {
  flex-shrink: 0;
}

.list-editor__ops {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  margin-top: 2px;
}

/* 一键 AI 两步进度 */
.ai-field {
  display: flex;
  align-items: center;
  gap: 12px;
}

.ai-steps {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 8px;
  font-size: 12px;
}

.batch-ai {
  margin-bottom: 12px;
}

.batch-ai__text {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.ai-step {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--el-text-color-secondary);
}

.ai-step__icon {
  font-weight: 700;
}

.ai-step.is-active {
  color: var(--el-color-primary);
  font-weight: 600;
}

.ai-step.is-done {
  color: var(--el-color-success);
}

.ai-step.is-error {
  color: var(--el-color-danger);
}
</style>
