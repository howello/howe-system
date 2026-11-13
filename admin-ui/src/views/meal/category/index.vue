<template>
  <div class="app-container">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="9 个公共分类名称与数量固定；家庭私有分类可新增、修改、删除，保存前会先经 AI 审阅并润色。"
      style="margin-bottom: 12px"
    />

    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="分类名称" prop="name">
        <el-input v-model="queryParams.name" placeholder="请输入分类名称" clearable style="width: 180px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item v-if="isAdmin" label="归属家庭" prop="deptId">
        <el-select v-model="queryParams.deptId" placeholder="全部家庭" clearable filterable style="width: 180px">
          <el-option v-for="item in familyOptions" :key="item.deptId" :label="item.name" :value="item.deptId" />
          <el-option label="公共分类" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="状态" clearable style="width: 140px">
          <el-option label="正常" value="0" />
          <el-option label="停用" value="1" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['meal:category:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="Delete"
          :disabled="!canBatchDelete"
          @click="handleDelete()"
          v-hasPermi="['meal:category:remove']"
        >删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="categoryList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" :selectable="isSelectable" />
      <el-table-column label="图标" align="center" width="70">
        <template #default="scope">
          <span class="category-icon">{{ scope.row.icon || "🍽️" }}</span>
        </template>
      </el-table-column>
      <el-table-column label="分类名称" align="left" prop="name" min-width="150">
        <template #default="scope">
          <a class="link-type" style="cursor: pointer" @click="handleUpdate(scope.row)">
            {{ scope.row.name }}
          </a>
          <el-tag v-if="scope.row.status === '1'" type="info" size="small" style="margin-left: 6px">停用</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="归属" align="center" width="130">
        <template #default="scope">
          <el-tag v-if="scope.row.deptId === 0" type="warning" size="small">公共</el-tag>
          <el-tag v-else type="success" size="small">{{ familyLabel(scope.row.deptId) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="分类说明" align="left" prop="description" :show-overflow-tooltip="true" min-width="170" />
      <el-table-column label="分类依据" align="left" prop="basis" :show-overflow-tooltip="true" min-width="240" />
      <el-table-column label="排序" align="center" prop="sort" width="70" />
      <el-table-column label="操作" align="center" width="150" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['meal:category:edit']">修改</el-button>
          <el-button
            v-if="scope.row.deptId !== 0"
            link
            type="danger"
            icon="Delete"
            @click="handleDelete(scope.row)"
            v-hasPermi="['meal:category:remove']"
          >删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog
      :title="title"
      v-model="open"
      width="680px"
      append-to-body
      :close-on-click-modal="false"
      :close-on-press-escape="!submitting"
      :show-close="!submitting"
      :before-close="handleDialogClose"
    >
      <el-form ref="entityFormRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item v-if="isAdmin" label="归属" prop="deptId">
          <el-select v-model="form.deptId" placeholder="请选择归属" filterable style="width: 100%">
            <el-option v-if="!isCreate" label="公共分类" :value="0" />
            <el-option v-for="item in familyOptions" :key="item.deptId" :label="item.name" :value="item.deptId" />
          </el-select>
          <div class="form-tip">
            {{ isCreate
              ? "公共分类不能在这里新建；家庭私有分类在所选家庭内可见"
              : isPublicCategory
                ? "选家庭 = 公共分类降为家庭私有分类，挂它下面的公共菜一并迁走"
                : "选「公共分类」= 升为公共分类，名下菜品一并转为公共；选家庭 = 迁到该家庭" }}
          </div>
        </el-form-item>
        <el-form-item label="分类名称" prop="name">
          <el-input v-model="form.name" :disabled="nameLocked" placeholder="如：岷县" maxlength="64" show-word-limit />
          <div class="form-tip">
            {{ nameLocked
              ? "公共分类名称固定，不支持修改；要改名请先把归属改到某个家庭"
              : "同一归属内不能重名；升为公共分类时也允许保留自定义名称" }}
          </div>
        </el-form-item>
        <el-form-item label="分类说明" prop="description">
          <el-input v-model="form.description" placeholder="一句话定位，如「以肉为主、肉量明显的菜」" maxlength="255" show-word-limit />
        </el-form-item>
        <el-form-item label="分类依据" prop="basis">
          <el-input
            v-model="form.basis"
            type="textarea"
            :rows="4"
            placeholder="放什么、不放什么、示例"
            maxlength="1000"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="图标" prop="icon">
          <MealCategoryIconPicker v-model="form.icon" />
        </el-form-item>
        <el-form-item label="排序" prop="sort">
          <el-input-number v-model="form.sort" :min="0" controls-position="right" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio value="0">正常</el-radio>
            <el-radio value="1">停用</el-radio>
          </el-radio-group>
          <div class="form-tip">停用后，点餐端该分类及其下的菜一起隐藏</div>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" maxlength="500" />
        </el-form-item>
      </el-form>

      <!-- AI 未通过：展示原因与修改示例，可一键回填 -->
      <el-alert
        v-if="review.rejected"
        type="error"
        :closable="false"
        show-icon
        title="AI 审阅未通过，本次未保存"
        style="margin-top: 8px"
      >
        <div class="review-reason">{{ review.reason }}</div>
        <div v-if="review.examples.length" class="review-examples">
          <div class="review-examples__title">修改示例（点击回填）</div>
          <div
            v-for="(example, index) in review.examples"
            :key="index"
            class="review-examples__item"
            @click="applyExample(example)"
          >
            {{ example }}
          </div>
        </div>
      </el-alert>

      <!-- AI 跳过：提示未经审阅 -->
      <el-alert
        v-if="review.skipped"
        type="warning"
        :closable="false"
        show-icon
        title="本次未经 AI 审阅（AI 服务不可用），已按你填写的内容保存"
        style="margin-top: 8px"
      />

      <!-- AI 通过：展示被调整的字段 -->
      <el-alert
        v-if="review.changes.length"
        type="success"
        :closable="false"
        show-icon
        title="AI 已润色并通过审阅，以下字段已调整"
        style="margin-top: 8px"
      >
        <div v-for="change in review.changes" :key="change.field" class="review-change">
          <span class="review-change__field">{{ fieldLabel(change.field) }}</span>
          <span class="review-change__before">{{ change.before || "（空）" }}</span>
          <span class="review-change__arrow">→</span>
          <span class="review-change__after">{{ change.after }}</span>
        </div>
      </el-alert>

      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="submitting" :disabled="submitting" @click="submitForm">
            {{ submitting ? "AI 审阅中…" : "确 定" }}
          </el-button>
          <el-button :disabled="submitting" @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="MealCategory">
import { useAppProxy, formRef } from '@/utils/appProxy'
import { ElMessageBox } from 'element-plus'
import useUserStore from "@/store/modules/user"
import { addCategory, getCategory, listCategory, updateCategory, delCategory } from "@/api/meal/category"
import { listDishOwnerOptions } from "@/api/meal/dish"
import type {
  MealCategory,
  MealCategoryQueryParams,
  MealCategorySaveRequest,
  MealCategorySaveResult
} from "@/types/api/meal/category"
import MealCategoryIconPicker from "./components/MealCategoryIconPicker.vue"
import { DEFAULT_MEAL_CATEGORY_ICON } from "./icons"

const proxy = useAppProxy()
const userStore = useUserStore()

const loading = ref<boolean>(true)
const submitting = ref<boolean>(false)
const showSearch = ref<boolean>(true)
const open = ref<boolean>(false)
const title = ref<string>("")
const total = ref<number>(0)
const categoryList = ref<MealCategory[]>([])
const ids = ref<number[]>([])
const familyOptions = ref<Array<{ deptId: number; name: string }>>([])

/** 仅平台管理员可以跨家庭筛选、指定归属 */
const isAdmin = computed(() => userStore.roles.includes("admin"))

/** 表单模型：保存请求 + 编辑时的分类 ID */
interface CategoryForm extends MealCategorySaveRequest {
  categoryId?: number;
}

const data = reactive({
  form: {} as CategoryForm,
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    name: undefined,
    status: undefined,
    deptId: undefined
  } as MealCategoryQueryParams,
  rules: {
    // 归属只在「超管新增」时必填；编辑时允许保持原归属不动
    deptId: [
      {
        required: true,
        validator: (_rule: unknown, value: number | undefined, callback: (error?: Error) => void) => {
          if (!isCreate.value || !isAdmin.value) {
            callback()
            return
          }
          if (value === undefined || value === null || value <= 0) {
            callback(new Error("请选择归属家庭"))
            return
          }
          callback()
        },
        trigger: "change"
      }
    ],
    name: [{ required: true, message: "分类名称不能为空", trigger: "blur" }],
    description: [{ required: true, message: "分类说明不能为空", trigger: "blur" }],
    basis: [{ required: true, message: "分类依据不能为空", trigger: "blur" }]
  }
})

/** AI 审阅状态展示 */
const review = reactive({
  rejected: false,
  skipped: false,
  reason: "",
  examples: [] as string[],
  changes: [] as MealCategorySaveResult["changes"]
})

const { queryParams, form, rules } = toRefs(data)

/** 当前表单是否仍是公共分类（编辑时跟随归属选择变化：改成家庭就不再是公共分类） */
const isPublicCategory = computed(() => {
  if (isCreate.value) {
    return false
  }
  return form.value.deptId === 0
})

/**
 * 名称是否锁定
 *
 * 公共分类保持为公共时名称锁定；一旦把归属改到某个家庭（降为私有分类），
 * 名称就按私有分类规则允许修改。新增时永远可改。
 */
const nameLocked = computed(() => !isCreate.value && isPublicCategory.value)

const isCreate = ref<boolean>(false)

/** 打开编辑弹窗时该分类的原始归属，用于判断「归属是否真的被改了」 */
const originalDeptId = ref<number | undefined>(undefined)

/** 批量删除只允许选择家庭私有分类 */
function isSelectable(row: MealCategory) {
  return row.deptId !== 0
}

const canBatchDelete = computed(() => ids.value.length > 0)

/** 家庭名称显示 */
function familyLabel(deptId?: number) {
  if (deptId === undefined || deptId === null) {
    return "未知"
  }
  return familyOptions.value.find((item) => item.deptId === deptId)?.name ?? `家庭 ${deptId}`
}

/** 字段名 → 中文 */
function fieldLabel(field: string) {
  const labels: Record<string, string> = {
    name: "分类名称",
    description: "分类说明",
    basis: "分类依据",
    icon: "图标"
  }
  return labels[field] ?? field
}

function getList() {
  loading.value = true
  listCategory(queryParams.value)
    .then((response: any) => {
      categoryList.value = response.rows
      total.value = response.total
    })
    .finally(() => {
      loading.value = false
    })
}

/** 超管加载家庭选项（用于筛选与新增归属） */
function loadFamilies() {
  if (!isAdmin.value) {
    return
  }
  listDishOwnerOptions()
    .then((response: any) => {
      familyOptions.value = (response.data || []).map((item: any) => ({
        deptId: item.deptId,
        name: item.name
      }))
    })
    .catch(() => {
      familyOptions.value = []
    })
}

function resetReview() {
  review.rejected = false
  review.skipped = false
  review.reason = ""
  review.examples = []
  review.changes = []
}

/** 请求进行中不允许关闭弹窗，避免旧响应写到新表单上 */
function handleDialogClose(done: () => void) {
  if (submitting.value) {
    return
  }
  done()
}

function cancel() {
  open.value = false
  reset()
}

function reset() {
  resetReview()
  originalDeptId.value = undefined
  form.value = {
    deptId: undefined,
    name: "",
    description: "",
    basis: "",
    icon: DEFAULT_MEAL_CATEGORY_ICON,
    sort: 0,
    status: "0",
    remark: undefined,
    categoryId: undefined
  }
  proxy.resetForm("entityFormRef")
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm("queryRef")
  queryParams.value.deptId = undefined
  handleQuery()
}

function handleSelectionChange(selection: MealCategory[]) {
  ids.value = selection.map((item) => item.categoryId as number)
}

function handleAdd() {
  reset()
  isCreate.value = true
  originalDeptId.value = undefined
  // 非超管固定本家庭，不显示归属选择
  if (!isAdmin.value) {
    form.value.deptId = undefined
  }
  open.value = true
  title.value = "新增分类"
}

function handleUpdate(row?: MealCategory) {
  reset()
  isCreate.value = false
  const categoryId = row?.categoryId
  if (!categoryId) {
    return
  }
  getCategory(categoryId).then((response: any) => {
    const category: MealCategory = response.data
    form.value = {
      deptId: category.deptId,
      name: category.name ?? "",
      description: category.description ?? "",
      basis: category.basis ?? "",
      icon: category.icon || DEFAULT_MEAL_CATEGORY_ICON,
      sort: category.sort ?? 0,
      status: (category.status as "0" | "1") ?? "0",
      remark: category.remark,
      categoryId: category.categoryId
    }
    originalDeptId.value = category.deptId
    open.value = true
    title.value = category.deptId === 0 ? "修改公共分类" : "修改分类"
  })
}

/** 一键回填示例：按「名称｜说明｜依据」拆分 */
function applyExample(example: string) {
  const parts = example.split(/[｜|]/).map((item) => item.trim())
  if (parts[0]) {
    form.value.name = parts[0]
  }
  if (parts[1]) {
    form.value.description = parts[1]
  }
  if (parts[2]) {
    form.value.basis = parts[2]
  }
  proxy.$modal.msgSuccess("已回填示例，可继续调整后重新提交")
}

function submitForm() {
  // 入口同步拦截：请求进行中或弹窗已关闭时不再发起保存，避免连点与响应后的重复提交
  if (submitting.value || !open.value) {
    return
  }
  // 先上锁再进入异步校验，防止校验回调返回前连续点击
  submitting.value = true
  formRef(proxy.$refs["entityFormRef"]).validate((valid: boolean) => {
    if (!valid) {
      submitting.value = false
      return
    }
    resetReview()
    // 编辑时只有「归属真的变了」才传 deptId：保持公共分类不变时传 0 会被后端当成非法目标
    const deptIdForSubmit = isCreate.value
      ? form.value.deptId
      : form.value.deptId !== undefined && form.value.deptId !== originalDeptId.value
        ? form.value.deptId
        : undefined
    const payload: MealCategorySaveRequest = {
      deptId: isAdmin.value ? deptIdForSubmit : undefined,
      name: form.value.name?.trim(),
      description: form.value.description?.trim(),
      basis: form.value.basis?.trim(),
      icon: form.value.icon,
      sort: form.value.sort ?? 0,
      status: form.value.status ?? "0",
      remark: form.value.remark
    }
    const request = isCreate.value
      ? addCategory(payload)
      : updateCategory(form.value.categoryId as number, payload)
    // 编辑时 categoryId 从表单里带过来
    request
      .then((response: any) => {
        handleReviewResult(response.data)
      })
      .catch(() => {
        // 请求失败（校验、权限、重名等）已在请求层提示，这里只需释放锁让用户改完重试
      })
      .finally(() => {
        submitting.value = false
      })
  })
}

/** 处理 AI 审阅结果 */
function handleReviewResult(result?: MealCategorySaveResult) {
  if (!result) {
    getList()
    return
  }
  if (result.reviewStatus === "REJECTED") {
    review.rejected = true
    review.reason = result.reason || "分类内容需要调整"
    review.examples = result.examples || []
    return
  }
  if (result.reviewStatus === "SKIPPED") {
    review.skipped = true
  }
  review.changes = result.changes || []
  if (result.saved) {
    // 保存成功后必须关闭弹窗：否则用户再点「确定」会重复提交，
    // 新增会撞「已存在同名分类」，修改会再走一次 AI 审阅并可能被判重复。
    open.value = false
    showSavedFeedback(result)
    getList()
  }
}

/**
 * 保存成功后的反馈：弹窗已关闭，用只读结果框展示 AI 调整过的字段与迁移数量
 *
 * @param result 保存结果
 */
function showSavedFeedback(result: MealCategorySaveResult) {
  const lines: string[] = []
  if (result.movedDishCount && result.movedDishCount > 0) {
    lines.push(`已连同 ${result.movedDishCount} 道菜品一起迁移到新归属`)
  }
  if (review.changes.length) {
    for (const change of review.changes) {
      lines.push(`${fieldLabel(change.field)}：${change.before || "（空）"} → ${change.after}`)
    }
  }
  if (!lines.length) {
    if (result.reviewStatus === "SKIPPED") {
      proxy.$modal.msgWarning("已保存（未经 AI 审阅）")
    } else {
      proxy.$modal.msgSuccess("已保存")
    }
    return
  }
  const title = result.reviewStatus === "SKIPPED" ? "已保存（未经 AI 审阅）" : "已保存，AI 已调整以下字段"
  ElMessageBox.alert(lines.join("\n"), title, { confirmButtonText: "关闭" }).catch(() => undefined)
}

function handleDelete(row?: MealCategory) {
  const targetIds = row?.categoryId ? [row.categoryId] : ids.value
  if (!targetIds.length) {
    return
  }
  const names = categoryList.value
    .filter((item) => targetIds.includes(item.categoryId as number))
    .map((item) => item.name)
    .join("、")
  proxy.$modal
    .confirm(`确认删除分类「${names}」吗？分类下仍有菜品或提案时会被拒绝。`)
    .then(() => delCategory(targetIds))
    .then(() => {
      proxy.$modal.msgSuccess("删除成功")
      getList()
    })
    .catch(() => undefined)
}

loadFamilies()
getList()
</script>

<style scoped>
.category-icon {
  font-size: 20px;
  line-height: 1;
}

.form-tip {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}

.review-reason {
  margin: 6px 0;
  font-size: 13px;
  line-height: 1.6;
}

.review-examples__title {
  margin-bottom: 6px;
  font-size: 12px;
  opacity: 0.85;
}

.review-examples__item {
  padding: 6px 10px;
  margin-bottom: 6px;
  font-size: 13px;
  line-height: 1.6;
  background: var(--el-fill-color-light);
  border-radius: 4px;
  cursor: pointer;
}

.review-examples__item:hover {
  background: var(--el-fill-color);
}

.review-change {
  display: flex;
  gap: 6px;
  align-items: baseline;
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
}

.review-change__field {
  flex: 0 0 64px;
  color: var(--el-text-color-secondary);
}

.review-change__before {
  color: var(--el-text-color-placeholder);
  text-decoration: line-through;
}

.review-change__after {
  color: var(--el-color-success);
}
</style>
