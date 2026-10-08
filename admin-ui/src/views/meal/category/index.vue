<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="分类名称" prop="name">
        <el-input v-model="queryParams.name" placeholder="请输入分类名称" clearable style="width: 200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="状态" clearable style="width: 200px">
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
        <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate()" v-hasPermi="['meal:category:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['meal:category:remove']">删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="categoryList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="分类名称" align="left" prop="name" :show-overflow-tooltip="true" min-width="160">
        <template #default="scope">
          <a class="link-type" style="cursor: pointer" @click="handleUpdate(scope.row)">{{ scope.row.name }}</a>
        </template>
      </el-table-column>
      <el-table-column label="归属" align="center" width="110">
        <template #default="scope">
          <el-tag :type="scope.row.deptId === 0 ? 'warning' : 'success'" size="small">
            {{ scope.row.deptId === 0 ? "公共" : "本家庭" }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="排序" align="center" prop="sort" width="80" />
      <el-table-column label="状态" align="center" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.status === '0' ? 'success' : 'info'" size="small">
            {{ scope.row.status === "0" ? "正常" : "停用" }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="备注" align="left" prop="remark" :show-overflow-tooltip="true" min-width="160" />
      <el-table-column label="创建时间" align="center" prop="createTime" width="170" />
      <el-table-column label="操作" align="center" width="150" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['meal:category:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['meal:category:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog :title="title" v-model="open" width="600px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="分类名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入分类名称" maxlength="64" />
        </el-form-item>
        <el-form-item v-if="isAdmin && !form.categoryId" label="归属">
          <el-checkbox v-model="asPublic">建为平台公共分类（所有家庭可见）</el-checkbox>
        </el-form-item>
        <el-form-item label="图标地址" prop="icon">
          <el-input v-model="form.icon" placeholder="选填，图片地址" />
        </el-form-item>
        <el-form-item label="排序" prop="sort">
          <el-input-number v-model="form.sort" :min="0" controls-position="right" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio value="0">正常</el-radio>
            <el-radio value="1">停用</el-radio>
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

<script setup lang="ts" name="MealCategory">
import { getCategory, listCategory, addCategory, updateCategory, delCategory } from "@/api/meal/category"
import type { MealCategory, MealCategoryQueryParams } from "@/types/api/meal/category"
import useUserStore from "@/store/modules/user"

const { proxy } = getCurrentInstance() as any

const userStore = useUserStore()
const isAdmin = computed(() => userStore.roles.includes("admin"))

const categoryList = ref<MealCategory[]>([])
const open = ref<boolean>(false)
const loading = ref<boolean>(true)
const submitting = ref<boolean>(false)
const showSearch = ref<boolean>(true)
const ids = ref<number[]>([])
const single = ref<boolean>(true)
const multiple = ref<boolean>(true)
const total = ref<number>(0)
const title = ref<string>("")
/** 新增时是否建成平台公共分类 */
const asPublic = ref<boolean>(false)

const data = reactive({
  form: {} as MealCategory,
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    name: undefined,
    status: undefined
  } as MealCategoryQueryParams,
  rules: {
    name: [{ required: true, message: "分类名称不能为空", trigger: "blur" }]
  }
})

const { queryParams, form, rules } = toRefs(data)

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

function cancel() {
  open.value = false
  reset()
}

function reset() {
  asPublic.value = false
  form.value = {
    categoryId: undefined,
    name: undefined,
    icon: undefined,
    sort: 0,
    status: "0",
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

function handleSelectionChange(selection: MealCategory[]) {
  ids.value = selection.map((item) => item.categoryId as number)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

function handleAdd() {
  reset()
  open.value = true
  title.value = "新增分类"
}

function handleUpdate(row?: MealCategory) {
  reset()
  const categoryId = row?.categoryId || ids.value[0]
  getCategory(categoryId).then((response: any) => {
    form.value = response.data
    open.value = true
    title.value = "修改分类"
  })
}

function submitForm() {
  proxy.$refs["formRef"].validate((valid: boolean) => {
    if (!valid) {
      return
    }
    submitting.value = true
    // 归属由后端按当前登录用户决定；只有超管勾选「平台公共」时才显式传 0
    const payload: MealCategory = { ...form.value }
    if (!payload.categoryId) {
      payload.deptId = asPublic.value ? 0 : undefined
    } else {
      payload.deptId = undefined
    }
    const action = payload.categoryId ? updateCategory(payload) : addCategory(payload)
    action
      .then(() => {
        proxy.$modal.msgSuccess(payload.categoryId ? "修改成功" : "新增成功")
        open.value = false
        getList()
      })
      .finally(() => {
        submitting.value = false
      })
  })
}

function handleDelete(row?: MealCategory) {
  const categoryIds = row?.categoryId ? [row.categoryId] : ids.value
  proxy.$modal
    .confirm("是否确认删除选中的分类？分类下还有菜品时会被拒绝。")
    .then(() => delCategory(categoryIds))
    .then(() => {
      getList()
      proxy.$modal.msgSuccess("删除成功")
    })
    .catch(() => {})
}

getList()
</script>
