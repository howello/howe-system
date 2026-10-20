<template>
  <div class="app-container">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="分类固定为 9 个唯一主类，一道菜只能归一个主类；「招牌」不是分类，由点餐端按近 90 天点单量自动推荐。"
      style="margin-bottom: 12px"
    />

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
        <el-button
          type="success"
          plain
          icon="Edit"
          :disabled="single"
          @click="handleUpdate()"
          v-hasPermi="['meal:category:edit']"
        >修改</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="categoryList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="分类名称" align="left" prop="name" min-width="110">
        <template #default="scope">
          <a class="link-type" style="cursor: pointer" @click="handleUpdate(scope.row)">
            {{ scope.row.icon }} {{ scope.row.name }}
          </a>
        </template>
      </el-table-column>
      <el-table-column label="分类说明" align="left" prop="description" :show-overflow-tooltip="true" min-width="180" />
      <el-table-column label="分类依据" align="left" prop="basis" :show-overflow-tooltip="true" min-width="260" />
      <el-table-column label="排序" align="center" prop="sort" width="80" />
      <el-table-column label="状态" align="center" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.status === '0' ? 'success' : 'info'" size="small">
            {{ scope.row.status === "0" ? "正常" : "停用" }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="100" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['meal:category:edit']">修改</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog :title="title" v-model="open" width="680px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="分类名称" prop="name">
          <el-input v-model="form.name" disabled />
          <div class="form-tip">分类名称是唯一主类的稳定标识，不支持修改</div>
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
          <el-input v-model="form.icon" placeholder="emoji 或图片地址" />
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
import { getCategory, listCategory, updateCategory } from "@/api/meal/category"
import type { MealCategory, MealCategoryQueryParams } from "@/types/api/meal/category"

const { proxy } = getCurrentInstance() as any

const categoryList = ref<MealCategory[]>([])
const open = ref<boolean>(false)
const loading = ref<boolean>(true)
const submitting = ref<boolean>(false)
const showSearch = ref<boolean>(true)
const ids = ref<number[]>([])
const single = ref<boolean>(true)
const total = ref<number>(0)
const title = ref<string>("")

const data = reactive({
  form: {} as MealCategory,
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    name: undefined,
    status: undefined
  } as MealCategoryQueryParams,
  rules: {
    description: [{ required: true, message: "分类说明不能为空", trigger: "blur" }],
    basis: [{ required: true, message: "分类依据不能为空", trigger: "blur" }]
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
  form.value = {
    categoryId: undefined,
    name: undefined,
    description: undefined,
    basis: undefined,
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
    // 名称是唯一主类标识：原样回传（后端会校验是否被改动），只改可维护字段
    const payload: MealCategory = {
      categoryId: form.value.categoryId,
      name: form.value.name,
      description: form.value.description,
      basis: form.value.basis,
      icon: form.value.icon,
      sort: form.value.sort,
      status: form.value.status,
      remark: form.value.remark
    }
    updateCategory(payload)
      .then(() => {
        proxy.$modal.msgSuccess("修改成功")
        open.value = false
        getList()
      })
      .finally(() => {
        submitting.value = false
      })
  })
}

getList()
</script>

<style scoped>
.form-tip {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}
</style>
