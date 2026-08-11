<template>
   <div class="app-container">
      <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
         <el-form-item label="标题" prop="title">
            <el-input v-model="queryParams.title" placeholder="请输入文章标题" clearable style="width: 200px" @keyup.enter="handleQuery" />
         </el-form-item>
         <el-form-item label="标识" prop="slug">
            <el-input v-model="queryParams.slug" placeholder="请输入文章标识" clearable style="width: 200px" @keyup.enter="handleQuery" />
         </el-form-item>
         <el-form-item label="分类" prop="categories">
            <el-input v-model="queryParams.categories" placeholder="请输入分类" clearable style="width: 200px" @keyup.enter="handleQuery" />
         </el-form-item>
         <el-form-item label="标签" prop="tags">
            <el-input v-model="queryParams.tags" placeholder="请输入标签" clearable style="width: 200px" @keyup.enter="handleQuery" />
         </el-form-item>
         <el-form-item label="发布日期">
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
            <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['blog:article:add']">新增</el-button>
         </el-col>
         <el-col :span="1.5">
            <el-button type="primary" plain icon="DocumentAdd" @click="handleQuickAdd" v-hasPermi="['blog:article:add']">一键新增</el-button>
         </el-col>
         <el-col :span="1.5">
            <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate()" v-hasPermi="['blog:article:edit']">修改</el-button>
         </el-col>
         <el-col :span="1.5">
            <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['blog:article:remove']">删除</el-button>
         </el-col>
         <el-col :span="1.5">
            <el-button type="warning" plain icon="Refresh" :loading="syncing" @click="handleSync" v-hasPermi="['blog:article:sync']">同步仓库</el-button>
         </el-col>
         <el-col :span="1.5">
            <el-button type="info" plain icon="Download" @click="handleExport" v-hasPermi="['blog:article:export']">导出</el-button>
         </el-col>
         <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table v-loading="loading" :data="articleList" @selection-change="handleSelectionChange">
         <el-table-column type="selection" width="55" align="center" />
         <el-table-column label="标题" align="left" prop="title" :show-overflow-tooltip="true" min-width="220">
            <template #default="scope">
               <a class="link-type" style="cursor: pointer" @click="handleUpdate(scope.row)">{{ scope.row.title }}</a>
            </template>
         </el-table-column>
         <el-table-column label="标识" align="center" prop="slug" :show-overflow-tooltip="true" width="180" />
         <el-table-column label="分类" align="center" prop="categories" width="120" />
         <el-table-column label="标签" align="center" prop="tags" :show-overflow-tooltip="true" width="160" />
         <el-table-column label="字数" align="center" prop="wordCount" width="80" />
         <el-table-column label="标记" align="center" width="140">
            <template #default="scope">
               <el-tag v-if="scope.row.isTop === '1'" type="danger" size="small">置顶</el-tag>
               <el-tag v-if="scope.row.recommend === '1'" type="warning" size="small">推荐</el-tag>
               <el-tag v-if="scope.row.hide === '1'" type="info" size="small">隐藏</el-tag>
            </template>
         </el-table-column>
         <el-table-column label="发布日期" align="center" prop="publishDate" width="160">
            <template #default="scope">
               <span>{{ parseTime(scope.row.publishDate, '{y}-{m}-{d} {h}:{i}') }}</span>
            </template>
         </el-table-column>
         <el-table-column label="文件路径" align="left" prop="filePath" :show-overflow-tooltip="true" min-width="200" />
         <el-table-column label="操作" align="center" width="160" fixed="right" class-name="small-padding fixed-width">
            <template #default="scope">
               <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['blog:article:edit']">修改</el-button>
               <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['blog:article:remove']">删除</el-button>
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
         <el-form ref="articleRef" :model="form" :rules="rules" label-width="90px">
            <el-row :gutter="20">
               <el-col :span="12">
                  <el-form-item label="标题" prop="title">
                     <el-input v-model="form.title" placeholder="请输入文章标题" />
                  </el-form-item>
               </el-col>
               <el-col :span="12">
                  <el-form-item label="文章标识" prop="slug">
                     <el-input v-model="form.slug" placeholder="决定文章 URL /article/{标识}，需全局唯一" />
                  </el-form-item>
               </el-col>
            </el-row>
            <el-row :gutter="20">
               <el-col :span="12">
                  <el-form-item label="分类" prop="categories">
                     <el-input v-model="form.categories" placeholder="单个分类，如 java知识点" />
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
                  <el-form-item label="发布日期" prop="publishDate">
                     <el-date-picker
                        v-model="form.publishDate"
                        type="datetime"
                        value-format="YYYY-MM-DD HH:mm:ss"
                        placeholder="留空则取当前时间"
                        style="width: 100%"
                     />
                  </el-form-item>
               </el-col>
               <el-col :span="12">
                  <el-form-item label="文件路径" prop="filePath">
                     <el-input v-model="form.filePath" placeholder="相对文章目录，如 interview-notes/07-netty.md；留空用 标识.md" />
                  </el-form-item>
               </el-col>
            </el-row>
            <el-row :gutter="20">
               <el-col :span="12">
                  <el-form-item label="封面图" prop="cover">
                     <el-input v-model="form.cover" placeholder="留空则由主题随机分配内置封面" />
                  </el-form-item>
               </el-col>
               <el-col :span="12">
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

      <!-- 一键新增对话框 -->
      <el-dialog title="一键新增" v-model="quickAddOpen" width="680px" top="8vh" append-to-body :close-on-click-modal="false" @close="handleQuickAddClose">
         <el-input
            v-model="quickAddText"
            type="textarea"
            :rows="14"
            placeholder="粘贴聊天投稿生成的 JSON。可含 slug / title / categories / tags / filePath / content / publishDate / cover / recommend / hide / top / overwrite；未知字段会被忽略"
         />
         <el-alert v-if="quickAddErrors.length > 0" type="error" show-icon :closable="false" title="解析失败" style="margin-top: 8px">
            <p v-for="err in quickAddErrors" :key="err" class="quick-add-error-item">{{ err }}</p>
         </el-alert>
         <template #footer>
            <div class="dialog-footer">
               <el-button type="primary" @click="handleParseAndOpen">解析并打开表单</el-button>
               <el-button @click="quickAddOpen = false">取 消</el-button>
            </div>
         </template>
      </el-dialog>
   </div>
</template>

<script setup lang="ts" name="BlogArticle">
import MarkdownEditor from "@/components/MarkdownEditor/index.vue"
import { listArticle, getArticle, delArticle, addArticle, updateArticle, syncArticle } from "@/api/blog/article"
import type { BlogArticle, BlogArticleQueryParams, BlogSyncResult } from "@/types/api/blog/article"
import type { AjaxResult } from "@/types/api/common"

const { proxy } = getCurrentInstance()

const articleList = ref<BlogArticle[]>([])
const open = ref<boolean>(false)
const loading = ref<boolean>(true)
const syncing = ref<boolean>(false)
const submitting = ref<boolean>(false)
const showSearch = ref<boolean>(true)
const ids = ref<number[]>([])
const single = ref<boolean>(true)
const multiple = ref<boolean>(true)
const total = ref<number>(0)
const title = ref<string>("")
const dateRange = ref<string[]>([])

// ---- 一键新增状态 ----
const quickAddOpen = ref<boolean>(false)
const quickAddText = ref<string>("")
const quickAddErrors = ref<string[]>([])

const data = reactive({
  form: {} as BlogArticle,
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    title: undefined,
    slug: undefined,
    categories: undefined,
    tags: undefined
  } as BlogArticleQueryParams,
  rules: {
    title: [{ required: true, message: "文章标题不能为空", trigger: "blur" }],
    slug: [
      { required: true, message: "文章标识不能为空", trigger: "blur" },
      { pattern: /^[A-Za-z0-9][A-Za-z0-9._-]*$/, message: "只能包含字母、数字、点、下划线和中划线，且以字母或数字开头", trigger: "blur" }
    ],
    categories: [{ required: true, message: "分类不能为空", trigger: "blur" }],
    content: [{ required: true, message: "正文不能为空", trigger: "blur" }]
  }
})

const { queryParams, form, rules } = toRefs(data)

// ---- 一键新增：JSON 解析与校验（与 BlogArticlePublishBody 同形） ----
const QUICK_ADD_FIELD_WHITELIST = [
  "slug", "title", "categories", "tags", "filePath", "content",
  "publishDate", "cover", "recommend", "hide", "top", "overwrite"
] as const

const QUICK_ADD_REQUIRED_FIELDS = ["slug", "title", "categories", "content"] as const

/** 一键新增解析成功 */
interface QuickAddSuccess {
  ok: true
  article: BlogArticle
  overwrite: boolean
}

/** 一键新增解析失败 */
interface QuickAddFailure {
  ok: false
  errors: string[]
}

type QuickAddParseResult = QuickAddSuccess | QuickAddFailure

/** 布尔开关 → '1'/'0'，保留字符串 "1"/"0" 容错；非法类型返回 undefined */
function toSwitchFlag(value: unknown): "0" | "1" | undefined {
  if (value === undefined || value === null) return "0"
  if (typeof value === "boolean") return value ? "1" : "0"
  if (value === "1") return "1"
  if (value === "0") return "0"
  return undefined
}

/**
 * 解析「一键新增」粘贴的 JSON 为 BlogArticle。
 * 白名单过滤（未知字段忽略）；tags 数组 → 逗号串；recommend/hide/top 布尔 → '1'/'0'（JSON 键 top → 表单字段 isTop）；
 * publishDate/cover/filePath 字符串透传；overwrite 单独取出、不写入 form；
 * slug/title/categories/content 必填；非法 JSON / tags 类型不符 / 开关类型不符给出具体错误。
 */
function parseArticleJson(text: string): QuickAddParseResult {
  let parsed: unknown
  try {
    parsed = JSON.parse(text)
  } catch {
    return { ok: false, errors: ["JSON 解析失败：请检查粘贴内容是否为合法的 JSON"] }
  }
  if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) {
    return { ok: false, errors: ["JSON 顶层必须是对象（由一个或多个文章字段组成）"] }
  }
  // 白名单过滤：直接删除未白名单的键，避免未知字段漏进表单
  const source = parsed as Record<string, unknown>
  for (const key of Object.keys(source)) {
    if (!(QUICK_ADD_FIELD_WHITELIST as readonly string[]).includes(key)) {
      delete source[key]
    }
  }

  // 1. 必填字段校验：缺失 / 非字符串 / trim 后为空 分别报具体错误
  const errors: string[] = []
  for (const key of QUICK_ADD_REQUIRED_FIELDS) {
    const value = source[key]
    if (value === undefined || value === null) {
      errors.push(`缺少必填字段：${key}`)
    } else if (typeof value !== "string") {
      errors.push(`字段 ${key} 必须是字符串`)
    } else if (value.trim() === "") {
      errors.push(`字段 ${key} 不能为空`)
    }
  }
  if (errors.length > 0) {
    return { ok: false, errors }
  }

  // 2. 可选字符串字段类型校验：类型不符时报错，不静默丢弃（与必填/标签/开关的错误处理一致）
  for (const key of ["filePath", "publishDate", "cover"]) {
    const value = source[key]
    if (value !== undefined && value !== null && typeof value !== "string") {
      errors.push(`字段 ${key} 必须是字符串`)
    }
  }
  if (errors.length > 0) {
    return { ok: false, errors }
  }

  // 3. tags 必须是字符串数组（可空），去空白与空项后 join 成逗号串
  let tags = ""
  if (source.tags !== undefined) {
    if (!Array.isArray(source.tags) || source.tags.some(item => typeof item !== "string")) {
      return { ok: false, errors: ['tags 必须是字符串数组，如 ["标签1", "标签2"]'] }
    }
    tags = (source.tags as string[]).map(item => item.trim()).filter(item => item !== "").join(",")
  }

  // 4. 布尔开关 → '1'/'0'；JSON 键 top 映射到表单字段 isTop
  const recommend = toSwitchFlag(source.recommend)
  const hide = toSwitchFlag(source.hide)
  const top = toSwitchFlag(source.top)
  if (recommend === undefined || hide === undefined || top === undefined) {
    return { ok: false, errors: ['recommend / hide / top 必须是布尔值（true/false）或字符串 "1"/"0"'] }
  }

  // 5. overwrite 单独取出，不写入表单；仅接受布尔或 "true"/"false"
  const ow = source.overwrite
  if (ow !== undefined && ow !== null && typeof ow !== "boolean" && ow !== "true" && ow !== "false") {
    return { ok: false, errors: ['overwrite 必须是布尔值（true/false）'] }
  }
  const overwrite = ow === true || ow === "true"

  const article: BlogArticle = {
    articleId: undefined,
    slug: source.slug as string,
    title: source.title as string,
    categories: source.categories as string,
    content: source.content as string,
    tags,
    recommend,
    hide,
    isTop: top,
    filePath: typeof source.filePath === "string" ? source.filePath : undefined,
    publishDate: typeof source.publishDate === "string" ? source.publishDate : undefined,
    cover: typeof source.cover === "string" ? source.cover : undefined
  }
  return { ok: true, article, overwrite }
}

// 三个开关在表里是 '0'/'1'，界面上用复选框更直观
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

/** 查询文章列表 */
function getList() {
  loading.value = true
  listArticle(proxy.addDateRange(queryParams.value, dateRange.value)).then(response => {
    articleList.value = response.rows
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
    articleId: undefined,
    slug: undefined,
    title: undefined,
    filePath: undefined,
    publishDate: undefined,
    categories: undefined,
    tags: undefined,
    cover: undefined,
    recommend: "0",
    hide: "0",
    isTop: "0",
    content: ""
  }
  proxy.resetForm("articleRef")
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
function handleSelectionChange(selection: BlogArticle[]) {
  ids.value = selection.map(item => item.articleId as number)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  open.value = true
  title.value = "新增文章"
}

/** 一键新增 slug 预检（基于当前列表，尽力而为；后端 insertBlogArticle 仍会兜底） */
interface SlugPreCheckOk {
  blocked: false
  message: string | null
}
interface SlugPreCheckBlocked {
  blocked: true
  message: string
}
type SlugPreCheckResult = SlugPreCheckOk | SlugPreCheckBlocked
function preCheckSlug(slug: string, overwrite: boolean): SlugPreCheckResult {
  const occupied = articleList.value.some((item: BlogArticle) => item.slug === slug)
  if (occupied && overwrite) {
    return { blocked: true, message: "一键新增仅支持新建，覆盖请走既有修改流程" }
  }
  if (occupied) {
    return { blocked: false, message: `文章标识已存在：${slug}` }
  }
  return { blocked: false, message: null }
}

/** 一键新增按钮操作：打开粘贴弹窗并清空上次状态 */
function handleQuickAdd() {
  quickAddText.value = ""
  quickAddErrors.value = []
  quickAddOpen.value = true
}

/** 一键新增弹窗关闭：重置粘贴内容与解析状态 */
function handleQuickAddClose() {
  quickAddText.value = ""
  quickAddErrors.value = []
}

/** 解析 JSON，预检通过后转入可编辑的新增表单（提交仍走既有 submitForm） */
function handleParseAndOpen() {
  const result = parseArticleJson(quickAddText.value)
  if (!result.ok) {
    quickAddErrors.value = result.errors
    return
  }
  const slug = result.article.slug as string
  const check = preCheckSlug(slug, result.overwrite)
  if (check.message) {
    proxy.$modal.msgWarning(check.message)
  }
  if (check.blocked) {
    return
  }
  // 复用既有新增表单路径：reset + 赋值 + 打开；提交走 submitForm → addArticle
  handleAdd()
  Object.assign(form.value, result.article)
  quickAddOpen.value = false
}

/** 修改按钮操作 */
function handleUpdate(row?: BlogArticle) {
  reset()
  const articleId = row?.articleId || ids.value[0]
  loading.value = true
  getArticle(articleId as number).then(response => {
    form.value = response.data as BlogArticle
    open.value = true
    title.value = "修改文章"
  }).finally(() => {
    loading.value = false
  })
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["articleRef"].validate((valid: boolean) => {
    if (!valid) {
      return
    }
    submitting.value = true
    const request = form.value.articleId ? updateArticle(form.value) : addArticle(form.value)
    request.then(() => {
      proxy.$modal.msgSuccess(form.value.articleId ? "修改成功" : "新增成功")
      open.value = false
      getList()
    }).finally(() => {
      submitting.value = false
    })
  })
}

/** 删除按钮操作 */
function handleDelete(row?: BlogArticle) {
  const articleIds = row?.articleId ? [row.articleId] : ids.value
  proxy.$modal.confirm('删除后仓库里的 markdown 文件也会一并删除，确认删除文章编号为"' + articleIds + '"的数据项？').then(() => {
    return delArticle(articleIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

/** 从仓库同步索引 */
function handleSync() {
  proxy.$modal.confirm("将从 GitHub 拉取全部文章重建本地索引，仓库中已删除的文章会从列表移除，是否继续？").then(() => {
    syncing.value = true
    return syncArticle()
  }).then((response: AjaxResult<BlogSyncResult>) => {
    const result = response.data as BlogSyncResult
    let message = `同步完成：扫描 ${result.scanned} 篇，新增 ${result.added}，更新 ${result.updated}，删除 ${result.removed}，跳过 ${result.skipped}`
    if (result.failures && result.failures.length > 0) {
      // 失败明细逐条列出，通常是 frontmatter 少了 id 或日期格式解析不了
      proxy.$modal.alertError(message + `，失败 ${result.failures.length} 篇：\n` + result.failures.join("\n"))
    } else {
      proxy.$modal.msgSuccess(message)
    }
    getList()
  }).catch(() => {}).finally(() => {
    syncing.value = false
  })
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download("blog/article/export", { ...queryParams.value }, `article_${new Date().getTime()}.xlsx`)
}

getList()
</script>

<style scoped>
.quick-add-error-item {
  margin: 4px 0 0;
}
</style>
