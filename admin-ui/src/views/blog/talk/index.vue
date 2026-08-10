<template>
   <div class="app-container">
      <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
         <el-form-item label="正文" prop="content">
            <el-input v-model="queryParams.content" placeholder="按正文关键字搜索" clearable style="width: 200px" @keyup.enter="handleQuery" />
         </el-form-item>
         <el-form-item label="标签" prop="tags">
            <el-input v-model="queryParams.tags" placeholder="请输入标签" clearable style="width: 160px" @keyup.enter="handleQuery" />
         </el-form-item>
         <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="状态" clearable style="width: 140px">
               <el-option label="发布" value="0" />
               <el-option label="隐藏" value="1" />
            </el-select>
         </el-form-item>
         <el-form-item label="发布时间">
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
            <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['blog:talk:add']">新增</el-button>
         </el-col>
         <el-col :span="1.5">
            <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate()" v-hasPermi="['blog:talk:edit']">修改</el-button>
         </el-col>
         <el-col :span="1.5">
            <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['blog:talk:remove']">删除</el-button>
         </el-col>
         <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table v-loading="loading" :data="talkList" @selection-change="handleSelectionChange">
         <el-table-column type="selection" width="55" align="center" />
         <el-table-column label="正文" align="left" :show-overflow-tooltip="true" min-width="300">
            <template #default="scope">
               <el-tag v-if="scope.row.isTop === '1'" type="danger" size="small" class="talk-top-tag">置顶</el-tag>
               <a class="link-type" style="cursor: pointer" @click="handleUpdate(scope.row)">{{ summarize(scope.row.content) }}</a>
            </template>
         </el-table-column>
         <el-table-column label="标签" align="center" prop="tags" :show-overflow-tooltip="true" width="180" />
         <el-table-column label="发布时间" align="center" prop="pubDate" width="160">
            <template #default="scope">
               <span>{{ parseTime(scope.row.pubDate, '{y}-{m}-{d} {h}:{i}') }}</span>
            </template>
         </el-table-column>
         <el-table-column label="置顶" align="center" width="80">
            <template #default="scope">
               <el-tag v-if="scope.row.isTop === '1'" type="danger" size="small">是</el-tag>
               <span v-else class="talk-muted">否</span>
            </template>
         </el-table-column>
         <el-table-column label="状态" align="center" width="90">
            <template #default="scope">
               <el-tag v-if="scope.row.status === '0'" type="success" size="small">发布</el-tag>
               <el-tag v-else type="info" size="small">隐藏</el-tag>
            </template>
         </el-table-column>
         <el-table-column label="操作" align="center" width="150" fixed="right" class-name="small-padding fixed-width">
            <template #default="scope">
               <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['blog:talk:edit']">修改</el-button>
               <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['blog:talk:remove']">删除</el-button>
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
      <el-dialog :title="title" v-model="open" width="900px" top="5vh" append-to-body :close-on-click-modal="false">
         <el-form ref="talkRef" :model="form" :rules="rules" label-width="90px">
            <el-row :gutter="20">
               <el-col :span="12">
                  <el-form-item label="标签" prop="tags">
                     <el-input v-model="form.tags" placeholder="多个标签用英文逗号分隔" />
                  </el-form-item>
               </el-col>
               <el-col :span="12">
                  <el-form-item label="发布时间" prop="pubDate">
                     <el-date-picker
                        v-model="form.pubDate"
                        type="datetime"
                        value-format="YYYY-MM-DD HH:mm:ss"
                        placeholder="留空则取当前时间"
                        style="width: 100%"
                     />
                  </el-form-item>
               </el-col>
            </el-row>
            <el-row :gutter="20">
               <el-col :span="12">
                  <el-form-item label="置顶">
                     <el-switch v-model="topFlag" active-text="置顶" inactive-text="不置顶" />
                  </el-form-item>
               </el-col>
               <el-col :span="12">
                  <el-form-item label="状态" prop="status">
                     <el-radio-group v-model="form.status">
                        <el-radio value="0">发布</el-radio>
                        <el-radio value="1">隐藏</el-radio>
                     </el-radio-group>
                  </el-form-item>
               </el-col>
            </el-row>
            <el-form-item label="正文" prop="content">
               <!-- 必须用 MarkdownEditor：Quill 富文本会把 markdown 语法破坏成 HTML -->
               <markdown-editor v-model="form.content" :height="420" />
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

<script setup lang="ts" name="BlogTalk">
// MarkdownEditor 未全局注册，必须按需 import
import MarkdownEditor from "@/components/MarkdownEditor/index.vue"
import { listTalk, getTalk, delTalk, addTalk, updateTalk } from "@/api/blog/talk"
import type { BlogTalk, BlogTalkQueryParams } from "@/types/api/blog/talk"

const { proxy } = getCurrentInstance()

const talkList = ref<BlogTalk[]>([])
const open = ref<boolean>(false)
const loading = ref<boolean>(true)
const submitting = ref<boolean>(false)
const showSearch = ref<boolean>(true)
const ids = ref<number[]>([])
const single = ref<boolean>(true)
const multiple = ref<boolean>(true)
const total = ref<number>(0)
const title = ref<string>("")
const dateRange = ref<string[]>([])

const data = reactive({
  form: {} as BlogTalk,
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    content: undefined,
    tags: undefined,
    status: undefined
  } as BlogTalkQueryParams,
  rules: {
    content: [{ required: true, message: "正文不能为空", trigger: "blur" }]
  }
})

const { queryParams, form, rules } = toRefs(data)

const topFlag = computed({
  get: () => form.value.isTop === "1",
  set: (val: boolean) => (form.value.isTop = val ? "1" : "0")
})

/** 列表里只展示正文摘要：正文是 markdown 原文，换行会撑破表格 */
function summarize(content?: string): string {
  if (!content) {
    return ""
  }
  const flat = content.replace(/\s+/g, " ").trim()
  return flat.length > 80 ? flat.slice(0, 80) + "…" : flat
}

/** 查询说说列表 */
function getList() {
  loading.value = true
  listTalk(proxy.addDateRange(queryParams.value, dateRange.value)).then(response => {
    talkList.value = response.rows
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
    talkId: undefined,
    content: "",
    tags: undefined,
    pubDate: undefined,
    isTop: "0",
    status: "0"
  }
  proxy.resetForm("talkRef")
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
function handleSelectionChange(selection: BlogTalk[]) {
  ids.value = selection.map(item => item.talkId as number)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  open.value = true
  title.value = "新增说说"
}

/** 修改按钮操作 */
function handleUpdate(row?: BlogTalk) {
  reset()
  const talkId = row?.talkId || ids.value[0]
  getTalk(talkId as number).then(response => {
    // 后端返回的是 markdown 原文，直接回填编辑器即可
    form.value = response.data as BlogTalk
    open.value = true
    title.value = "修改说说"
  })
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["talkRef"].validate((valid: boolean) => {
    if (!valid) {
      return
    }
    submitting.value = true
    const request = form.value.talkId ? updateTalk(form.value) : addTalk(form.value)
    request.then(() => {
      proxy.$modal.msgSuccess(form.value.talkId ? "修改成功" : "新增成功")
      open.value = false
      getList()
    }).finally(() => {
      submitting.value = false
    })
  })
}

/** 删除按钮操作 */
function handleDelete(row?: BlogTalk) {
  const talkIds = row?.talkId ? [row.talkId] : ids.value
  proxy.$modal.confirm('是否确认删除说说编号为"' + talkIds + '"的数据项？').then(() => {
    return delTalk(talkIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

getList()
</script>

<style scoped>
.talk-top-tag {
   margin-right: 6px;
}

.talk-muted {
   color: #909399;
}
</style>
