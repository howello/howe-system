<template>
   <div class="app-container">
      <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
         <el-form-item label="标题" prop="title">
            <el-input v-model="queryParams.title" placeholder="请输入标题" clearable style="width: 200px" @keyup.enter="handleQuery" />
         </el-form-item>
         <el-form-item label="分类" prop="categories">
            <el-input v-model="queryParams.categories" placeholder="请输入分类" clearable style="width: 200px" @keyup.enter="handleQuery" />
         </el-form-item>
         <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="草稿状态" clearable style="width: 200px">
               <el-option label="草稿" value="0" />
               <el-option label="已发布" value="1" />
            </el-select>
         </el-form-item>
         <el-form-item label="创建时间">
            <el-date-picker
               v-model="dateRange"
               style="width: 240px"
               value-format="YYYY-MM-DD"
               type="daterange"
               range-separator="-"
               start-placeholder="开始日期"
               end-placeholder="结束日期"
            ></el-date-picker>
         </el-form-item>
         <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
         </el-form-item>
      </el-form>

      <el-row :gutter="10" class="mb8">
         <el-col :span="1.5">
            <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['blog:draft:add']">新增</el-button>
         </el-col>
         <el-col :span="1.5">
            <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate()" v-hasPermi="['blog:draft:edit']">修改</el-button>
         </el-col>
         <el-col :span="1.5">
            <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['blog:draft:remove']">删除</el-button>
         </el-col>
         <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table v-loading="loading" :data="draftList" @selection-change="handleSelectionChange">
         <el-table-column type="selection" width="55" align="center" />
         <el-table-column label="标题" align="left" prop="title" :show-overflow-tooltip="true" min-width="220">
            <template #default="scope">
               <a class="link-type" style="cursor: pointer" @click="handleUpdate(scope.row)">{{ scope.row.title }}</a>
            </template>
         </el-table-column>
         <el-table-column label="标识" align="center" prop="slug" :show-overflow-tooltip="true" width="180" />
         <el-table-column label="分类" align="center" prop="categories" width="120" />
         <el-table-column label="标签" align="center" prop="tags" :show-overflow-tooltip="true" width="160" />
         <el-table-column label="状态" align="center" width="90">
            <template #default="scope">
               <el-tag v-if="scope.row.status === '1'" type="success" size="small">已发布</el-tag>
               <el-tag v-else type="info" size="small">草稿</el-tag>
            </template>
         </el-table-column>
         <el-table-column label="创建时间" align="center" prop="createTime" width="160">
            <template #default="scope">
               <span>{{ parseTime(scope.row.createTime, '{y}-{m}-{d} {h}:{i}') }}</span>
            </template>
         </el-table-column>
         <el-table-column label="发布路径" align="left" prop="publishedPath" :show-overflow-tooltip="true" min-width="180" />
         <el-table-column label="操作" align="center" width="220" class-name="small-padding fixed-width">
            <template #default="scope">
               <el-button link type="primary" icon="Edit" :disabled="scope.row.status === '1'" @click="handleUpdate(scope.row)" v-hasPermi="['blog:draft:edit']">修改</el-button>
               <el-button link type="success" icon="Upload" :disabled="scope.row.status === '1'" @click="handlePublish(scope.row)" v-hasPermi="['blog:draft:publish']">发布</el-button>
               <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['blog:draft:remove']">删除</el-button>
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
      <el-dialog :title="title" v-model="open" width="1100px" top="5vh" append-to-body :close-on-click-modal="false">
         <el-form ref="draftRef" :model="form" :rules="rules" label-width="90px">
            <el-row :gutter="20">
               <el-col :span="12">
                  <el-form-item label="标题" prop="title">
                     <el-input v-model="form.title" placeholder="请输入标题" />
                  </el-form-item>
               </el-col>
               <el-col :span="12">
                  <el-form-item label="文章标识" prop="slug">
                     <el-input v-model="form.slug" placeholder="发布时作为文章 URL，可稍后再填" />
                  </el-form-item>
               </el-col>
            </el-row>
            <el-row :gutter="20">
               <el-col :span="12">
                  <el-form-item label="分类" prop="categories">
                     <el-input v-model="form.categories" placeholder="单个分类，发布前必填" />
                  </el-form-item>
               </el-col>
               <el-col :span="12">
                  <el-form-item label="标签" prop="tags">
                     <el-input v-model="form.tags" placeholder="多个标签用英文逗号分隔" />
                  </el-form-item>
               </el-col>
            </el-row>
            <el-row :gutter="20">
               <el-col :span="12">
                  <el-form-item label="计划发布" prop="publishDate">
                     <el-date-picker
                        v-model="form.publishDate"
                        type="datetime"
                        value-format="YYYY-MM-DD HH:mm:ss"
                        placeholder="留空则发布时取当前时间"
                        style="width: 100%"
                     />
                  </el-form-item>
               </el-col>
               <el-col :span="12">
                  <el-form-item label="封面图" prop="cover">
                     <el-input v-model="form.cover" placeholder="留空则由主题随机分配内置封面" />
                  </el-form-item>
               </el-col>
            </el-row>
            <el-row>
               <el-col :span="24">
                  <el-form-item label="标记">
                     <el-checkbox v-model="topFlag" label="置顶" />
                     <el-checkbox v-model="recommendFlag" label="推荐" />
                     <el-checkbox v-model="hideFlag" label="隐藏" />
                  </el-form-item>
               </el-col>
            </el-row>
            <el-row>
               <el-col :span="24">
                  <el-form-item label="正文" prop="content">
                     <markdown-editor v-model="form.content" :height="500" />
                  </el-form-item>
               </el-col>
            </el-row>
         </el-form>
         <template #footer>
            <div class="dialog-footer">
               <el-button type="primary" :loading="submitting" @click="submitForm">确 定</el-button>
               <el-button @click="cancel">取 消</el-button>
            </div>
         </template>
      </el-dialog>

      <!-- 发布对话框 -->
      <el-dialog title="发布草稿" v-model="publishOpen" width="600px" append-to-body>
         <el-form label-width="90px">
            <el-form-item label="文件路径">
               <el-input v-model="publishPath" placeholder="相对文章目录，如 interview-notes/07-netty.md；留空用 标识.md" />
            </el-form-item>
            <el-alert type="info" :closable="false" show-icon>
               发布会把草稿生成 markdown 并提交到 GitHub 仓库，提交成功后草稿转为「已发布」且不可再编辑，后续修改请到文章管理。
            </el-alert>
         </el-form>
         <template #footer>
            <div class="dialog-footer">
               <el-button type="primary" :loading="publishing" @click="submitPublish">确 定</el-button>
               <el-button @click="publishOpen = false">取 消</el-button>
            </div>
         </template>
      </el-dialog>
   </div>
</template>

<script setup lang="ts" name="BlogDraft">
import MarkdownEditor from "@/components/MarkdownEditor/index.vue"
import { listDraft, getDraft, delDraft, addDraft, updateDraft, publishDraft } from "@/api/blog/draft"
import type { BlogDraft, BlogDraftQueryParams } from "@/types/api/blog/draft"

const { proxy } = getCurrentInstance()

const draftList = ref<BlogDraft[]>([])
const open = ref<boolean>(false)
const publishOpen = ref<boolean>(false)
const loading = ref<boolean>(true)
const submitting = ref<boolean>(false)
const publishing = ref<boolean>(false)
const showSearch = ref<boolean>(true)
const ids = ref<number[]>([])
const single = ref<boolean>(true)
const multiple = ref<boolean>(true)
const total = ref<number>(0)
const title = ref<string>("")
const dateRange = ref<string[]>([])
const publishPath = ref<string>("")
const publishId = ref<number>()

const data = reactive({
  form: {} as BlogDraft,
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    title: undefined,
    categories: undefined,
    status: undefined
  } as BlogDraftQueryParams,
  rules: {
    title: [{ required: true, message: "标题不能为空", trigger: "blur" }],
    slug: [{ pattern: /^$|^[A-Za-z0-9][A-Za-z0-9._-]*$/, message: "只能包含字母、数字、点、下划线和中划线，且以字母或数字开头", trigger: "blur" }]
  }
})

const { queryParams, form, rules } = toRefs(data)

const topFlag = computed({
  get: () => form.value.isTop === "1",
  set: (val: boolean) => (form.value.isTop = val ? "1" : "0")
})
const recommendFlag = computed({
  get: () => form.value.recommend === "1",
  set: (val: boolean) => (form.value.recommend = val ? "1" : "0")
})
const hideFlag = computed({
  get: () => form.value.hide === "1",
  set: (val: boolean) => (form.value.hide = val ? "1" : "0")
})

/** 查询草稿列表 */
function getList() {
  loading.value = true
  listDraft(proxy.addDateRange(queryParams.value, dateRange.value)).then(response => {
    draftList.value = response.rows
    total.value = response.total
    loading.value = false
  }).catch(() => {
    loading.value = false
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
    draftId: undefined,
    title: undefined,
    slug: undefined,
    categories: undefined,
    tags: undefined,
    cover: undefined,
    publishDate: undefined,
    recommend: "0",
    hide: "0",
    isTop: "0",
    content: ""
  }
  proxy.resetForm("draftRef")
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  dateRange.value = []
  proxy.resetForm("queryRef")
  handleQuery()
}

/** 多选框选中数据 */
function handleSelectionChange(selection: BlogDraft[]) {
  ids.value = selection.map(item => item.draftId as number)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  open.value = true
  title.value = "新增草稿"
}

/** 修改按钮操作 */
function handleUpdate(row?: BlogDraft) {
  reset()
  const draftId = row?.draftId || ids.value[0]
  getDraft(draftId as number).then(response => {
    form.value = response.data as BlogDraft
    open.value = true
    title.value = "修改草稿"
  })
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["draftRef"].validate((valid: boolean) => {
    if (!valid) {
      return
    }
    submitting.value = true
    const request = form.value.draftId ? updateDraft(form.value) : addDraft(form.value)
    request.then(() => {
      proxy.$modal.msgSuccess(form.value.draftId ? "修改成功" : "新增成功")
      open.value = false
      getList()
    }).finally(() => {
      submitting.value = false
    })
  })
}

/** 删除按钮操作 */
function handleDelete(row?: BlogDraft) {
  const draftIds = row?.draftId ? [row.draftId] : ids.value
  proxy.$modal.confirm('是否确认删除草稿编号为"' + draftIds + '"的数据项？').then(() => {
    return delDraft(draftIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

/** 发布按钮操作 */
function handlePublish(row: BlogDraft) {
  if (!row.slug) {
    proxy.$modal.msgError("发布前请先填写文章标识")
    return
  }
  if (!row.categories) {
    proxy.$modal.msgError("发布前请先填写分类")
    return
  }
  publishId.value = row.draftId
  publishPath.value = ""
  publishOpen.value = true
}

/** 确认发布 */
function submitPublish() {
  publishing.value = true
  publishDraft(publishId.value as number, publishPath.value).then(() => {
    proxy.$modal.msgSuccess("发布成功，已提交到 GitHub 仓库")
    publishOpen.value = false
    getList()
  }).finally(() => {
    publishing.value = false
  })
}

getList()
</script>
