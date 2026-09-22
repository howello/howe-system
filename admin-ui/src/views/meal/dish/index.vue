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
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

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
      <el-table-column label="分类" align="center" prop="categoryName" width="100" />
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
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="菜名" prop="name">
              <el-input v-model="form.name" placeholder="请输入菜名" maxlength="128" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="分类" prop="categoryId">
              <el-select v-model="form.categoryId" placeholder="请选择分类" clearable style="width: 100%">
                <el-option v-for="item in categoryOptions" :key="item.categoryId" :label="item.name" :value="item.categoryId" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item v-if="isAdmin && !form.dishId" label="归属">
          <el-checkbox v-model="asPublic">建为平台公共菜谱（所有家庭可见）</el-checkbox>
        </el-form-item>
        <el-form-item label="封面地址" prop="cover">
          <el-input v-model="form.cover" placeholder="选填，图片地址（/common/upload 返回的 URL）" />
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
          <el-input
            v-model="form.ingredients"
            type="textarea"
            :rows="3"
            placeholder='JSON 数组，如 [{"name":"带皮五花肉","amount":"600 g"},{"name":"冰糖","amount":"25 g"}]'
          />
        </el-form-item>
        <el-form-item label="做法" prop="steps">
          <el-input
            v-model="form.steps"
            type="textarea"
            :rows="4"
            placeholder='JSON 数组，如 ["五花肉切块焯水","炒糖色后翻炒上色","加热水小火炖 60 分钟"]'
          />
        </el-form-item>
        <el-form-item label="小贴士" prop="tips">
          <el-input v-model="form.tips" type="textarea" :rows="2" placeholder='JSON 数组，如 ["炒糖色全程小火"]' />
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
import { getDish, listDish, addDish, updateDish, delDish } from "@/api/meal/dish"
import { listCategory } from "@/api/meal/category"
import type { MealDish, MealDishQueryParams } from "@/types/api/meal/dish"
import type { MealCategory } from "@/types/api/meal/category"
import useUserStore from "@/store/modules/user"

const { proxy } = getCurrentInstance() as any

const userStore = useUserStore()
const isAdmin = computed(() => userStore.roles.includes("admin"))

const dishList = ref<MealDish[]>([])
const categoryOptions = ref<MealCategory[]>([])
const open = ref<boolean>(false)
const loading = ref<boolean>(true)
const submitting = ref<boolean>(false)
const showSearch = ref<boolean>(true)
const ids = ref<number[]>([])
const single = ref<boolean>(true)
const multiple = ref<boolean>(true)
const total = ref<number>(0)
const title = ref<string>("")
const asPublic = ref<boolean>(false)

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

function cancel() {
  open.value = false
  reset()
}

function reset() {
  asPublic.value = false
  form.value = {
    dishId: undefined,
    name: undefined,
    categoryId: undefined,
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
    open.value = true
    title.value = "修改菜品"
  })
}

/** 三列 JSON 在提交前做一次格式校验，避免脏数据进库后前端解析失败 */
function validateJson(label: string, raw?: string): boolean {
  if (!raw) {
    return true
  }
  try {
    JSON.parse(raw)
    return true
  } catch (e) {
    proxy.$modal.msgError(`${label}不是合法的 JSON`)
    return false
  }
}

function submitForm() {
  proxy.$refs["formRef"].validate((valid: boolean) => {
    if (!valid) {
      return
    }
    if (
      !validateJson("用料", form.value.ingredients) ||
      !validateJson("做法", form.value.steps) ||
      !validateJson("小贴士", form.value.tips)
    ) {
      return
    }
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
