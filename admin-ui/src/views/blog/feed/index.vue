<template>
   <div class="app-container">
      <!-- 上半部：订阅源 -->
      <el-card shadow="never" class="mb8">
         <template #header>
            <div class="feed-card-header">
               <span>RSS 订阅源</span>
               <span class="feed-card-tip">点击某一行可在下方只看该源的条目</span>
            </div>
         </template>

         <el-form :model="feedQuery" ref="feedQueryRef" :inline="true" v-show="showSearch" label-width="68px">
            <el-form-item label="名称" prop="linkName">
               <el-input v-model="feedQuery.linkName" placeholder="请输入订阅源名称" clearable style="width: 200px" @keyup.enter="handleFeedQuery" />
            </el-form-item>
            <el-form-item label="状态" prop="status">
               <el-select v-model="feedQuery.status" placeholder="状态" clearable style="width: 160px">
                  <el-option label="正常" value="0" />
                  <el-option label="停用" value="1" />
               </el-select>
            </el-form-item>
            <el-form-item>
               <el-button type="primary" icon="Search" @click="handleFeedQuery">搜索</el-button>
               <el-button icon="Refresh" @click="resetFeedQuery">重置</el-button>
            </el-form-item>
         </el-form>

         <el-row :gutter="10" class="mb8">
            <el-col :span="1.5">
               <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['blog:feed:add']">新增</el-button>
            </el-col>
            <el-col :span="1.5">
               <el-button type="danger" plain icon="Delete" :disabled="feedMultiple" @click="handleDelete()" v-hasPermi="['blog:feed:remove']">删除</el-button>
            </el-col>
            <el-col :span="1.5">
               <el-button type="warning" plain icon="Refresh" :loading="syncing" @click="handleSyncAll" v-hasPermi="['blog:feed:sync']">全部同步</el-button>
            </el-col>
            <right-toolbar v-model:showSearch="showSearch" @queryTable="getFeedList"></right-toolbar>
         </el-row>

         <el-table
            v-loading="feedLoading"
            :data="feedList"
            highlight-current-row
            ref="feedTableRef"
            @selection-change="handleFeedSelectionChange"
            @current-change="handleCurrentFeedChange"
         >
            <el-table-column type="selection" width="55" align="center" />
            <el-table-column label="订阅源名称" align="left" prop="linkName" :show-overflow-tooltip="true" min-width="140" />
            <el-table-column label="RSS 地址" align="left" prop="rssUrl" :show-overflow-tooltip="true" min-width="220" />
            <el-table-column label="最后同步" align="center" prop="lastSyncTime" width="160">
               <template #default="scope">
                  <span v-if="scope.row.lastSyncTime">{{ parseTime(scope.row.lastSyncTime, '{y}-{m}-{d} {h}:{i}') }}</span>
                  <span v-else class="feed-never">从未同步</span>
               </template>
            </el-table-column>
            <el-table-column label="状态" align="center" width="80">
               <template #default="scope">
                  <el-tag v-if="scope.row.status === '0'" type="success" size="small">正常</el-tag>
                  <el-tag v-else type="info" size="small">停用</el-tag>
               </template>
            </el-table-column>
            <el-table-column label="最后错误" align="left" :show-overflow-tooltip="true" min-width="200">
               <template #default="scope">
                  <el-tag v-if="scope.row.lastError" type="danger" size="small">{{ scope.row.lastError }}</el-tag>
                  <span v-else class="feed-ok">—</span>
               </template>
            </el-table-column>
            <el-table-column label="操作" align="center" width="210" class-name="small-padding fixed-width">
               <template #default="scope">
                  <el-button link type="warning" icon="Refresh" @click="handleSyncOne(scope.row)" v-hasPermi="['blog:feed:sync']">同步</el-button>
                  <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['blog:feed:edit']">修改</el-button>
                  <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['blog:feed:remove']">删除</el-button>
               </template>
            </el-table-column>
         </el-table>

         <pagination
            v-show="feedTotal > 0"
            :total="feedTotal"
            v-model:page="feedQuery.pageNum"
            v-model:limit="feedQuery.pageSize"
            @pagination="getFeedList"
         />
      </el-card>

      <!-- 下半部：条目 -->
      <el-card shadow="never">
         <template #header>
            <div class="feed-card-header">
               <span>
                  朋友圈条目
                  <el-tag v-if="currentFeed" type="primary" size="small" class="feed-filter-tag">
                     仅看：{{ currentFeed.linkName }}
                  </el-tag>
               </span>
               <el-button v-if="currentFeed" link type="info" icon="Close" @click="clearCurrentFeed">查看全部</el-button>
            </div>
         </template>

         <el-form :model="itemQuery" ref="itemQueryRef" :inline="true" label-width="68px">
            <el-form-item label="标题" prop="title">
               <el-input v-model="itemQuery.title" placeholder="请输入条目标题" clearable style="width: 200px" @keyup.enter="handleItemQuery" />
            </el-form-item>
            <el-form-item label="作者" prop="author">
               <el-input v-model="itemQuery.author" placeholder="请输入作者" clearable style="width: 160px" @keyup.enter="handleItemQuery" />
            </el-form-item>
            <el-form-item>
               <el-button type="primary" icon="Search" @click="handleItemQuery">搜索</el-button>
               <el-button icon="Refresh" @click="resetItemQuery">重置</el-button>
               <el-button type="danger" plain icon="Delete" :disabled="itemMultiple" @click="handleItemDelete()" v-hasPermi="['blog:feed:remove']">删除</el-button>
            </el-form-item>
         </el-form>

         <el-table v-loading="itemLoading" :data="itemList" @selection-change="handleItemSelectionChange">
            <el-table-column type="selection" width="55" align="center" />
            <el-table-column label="标题" align="left" prop="title" :show-overflow-tooltip="true" min-width="260">
               <template #default="scope">
                  <!-- 条目链接来自不受控的第三方 RSS：:href 不做协议过滤，
                       javascript: 会在 admin 域执行、直接读走 localStorage 里的 token -->
                  <a v-if="safeUrl(scope.row.url)" :href="safeUrl(scope.row.url)" target="_blank" rel="noopener noreferrer" class="link-type">{{ scope.row.title }}</a>
                  <span v-else :title="scope.row.url">{{ scope.row.title }}</span>
               </template>
            </el-table-column>
            <el-table-column label="来源" align="center" prop="linkName" width="140" :show-overflow-tooltip="true" />
            <el-table-column label="作者" align="center" prop="author" width="140" :show-overflow-tooltip="true" />
            <el-table-column label="摘要" align="left" prop="summary" :show-overflow-tooltip="true" min-width="240" />
            <el-table-column label="发布时间" align="center" prop="pubDate" width="160">
               <template #default="scope">
                  <span>{{ parseTime(scope.row.pubDate, '{y}-{m}-{d} {h}:{i}') }}</span>
               </template>
            </el-table-column>
            <el-table-column label="操作" align="center" width="90" class-name="small-padding fixed-width">
               <template #default="scope">
                  <el-button link type="danger" icon="Delete" @click="handleItemDelete(scope.row)" v-hasPermi="['blog:feed:remove']">删除</el-button>
               </template>
            </el-table-column>
         </el-table>

         <pagination
            v-show="itemTotal > 0"
            :total="itemTotal"
            v-model:page="itemQuery.pageNum"
            v-model:limit="itemQuery.pageSize"
            @pagination="getItemList"
         />
      </el-card>

      <!-- 新增/修改订阅源 -->
      <el-dialog :title="title" v-model="open" width="680px" append-to-body :close-on-click-modal="false">
         <el-form ref="feedRef" :model="form" :rules="rules" label-width="90px">
            <el-form-item label="订阅源名称" prop="linkName">
               <el-input v-model="form.linkName" placeholder="请输入订阅源名称" />
            </el-form-item>
            <el-form-item label="RSS 地址" prop="rssUrl">
               <el-input v-model="form.rssUrl" placeholder="RSS 或 Atom 订阅地址，必填" />
            </el-form-item>
            <el-form-item label="站点地址" prop="linkUrl">
               <el-input v-model="form.linkUrl" placeholder="站点首页地址，选填" />
            </el-form-item>
            <el-form-item label="图标地址" prop="avatar">
               <el-input v-model="form.avatar" placeholder="选填" />
            </el-form-item>
            <el-row :gutter="20">
               <el-col :span="12">
                  <el-form-item label="显示顺序" prop="orderNum">
                     <el-input-number v-model="form.orderNum" :min="0" controls-position="right" style="width: 100%" />
                  </el-form-item>
               </el-col>
               <el-col :span="12">
                  <el-form-item label="状态" prop="status">
                     <el-radio-group v-model="form.status">
                        <el-radio value="0">正常</el-radio>
                        <el-radio value="1">停用</el-radio>
                     </el-radio-group>
                  </el-form-item>
               </el-col>
            </el-row>
            <el-form-item label="备注" prop="remark">
               <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="仅后台可见" />
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

<script setup lang="ts" name="BlogFeed">
import { listFeed, getFeed, addFeed, updateFeed, delFeed, syncAllFeeds, syncOneFeed, listFeedItems, delFeedItem } from "@/api/blog/feed"
import type { BlogLink } from "@/types/api/blog/link"
import type { BlogFeedQueryParams, BlogFeedItem, BlogFeedItemQueryParams, BlogFeedSyncResult } from "@/types/api/blog/feed"

const { proxy } = getCurrentInstance()

const feedList = ref<BlogLink[]>([])
const itemList = ref<BlogFeedItem[]>([])
const feedTableRef = ref()
// 主从联动的「主」：为 null 时下方展示全部条目
const currentFeed = ref<BlogLink | null>(null)

const open = ref<boolean>(false)
const feedLoading = ref<boolean>(true)
const itemLoading = ref<boolean>(true)
const submitting = ref<boolean>(false)
const syncing = ref<boolean>(false)
const showSearch = ref<boolean>(true)
const feedIds = ref<number[]>([])
const itemIds = ref<number[]>([])
const feedMultiple = ref<boolean>(true)
const itemMultiple = ref<boolean>(true)
const feedTotal = ref<number>(0)
const itemTotal = ref<number>(0)
const title = ref<string>("")

const data = reactive({
  form: {} as BlogLink,
  feedQuery: {
    pageNum: 1,
    pageSize: 10,
    linkName: undefined,
    status: undefined
  } as BlogFeedQueryParams,
  itemQuery: {
    pageNum: 1,
    pageSize: 10,
    linkId: undefined,
    title: undefined,
    author: undefined
  } as BlogFeedItemQueryParams,
  rules: {
    linkName: [{ required: true, message: "订阅源名称不能为空", trigger: "blur" }],
    rssUrl: [
      { required: true, message: "RSS 地址不能为空", trigger: "blur" },
      { pattern: /^https?:\/\/.+/, message: "地址需以 http:// 或 https:// 开头", trigger: "blur" }
    ]
  }
})

const { feedQuery, itemQuery, form, rules } = toRefs(data)

/**
 * 只放行 http/https 的链接
 *
 * 条目 url 来自不受控的第三方 RSS。Vue 的 :href 不做协议过滤，
 * javascript: 会在 admin 域执行，能直接读走 localStorage 里的 JWT。
 * 服务端 RssParser 已做同样白名单，这里挡的是校验上线前的存量数据。
 */
function safeUrl(url?: string): string {
  const raw = (url || "").trim()
  return /^https?:\/\//i.test(raw) ? raw : ""
}

/** 查询订阅源列表 */
function getFeedList() {
  feedLoading.value = true
  listFeed(feedQuery.value).then(response => {
    feedList.value = response.rows
    feedTotal.value = response.total
    feedLoading.value = false
  }).catch(() => {
    feedLoading.value = false
  })
}

/** 查询条目列表 */
function getItemList() {
  itemLoading.value = true
  listFeedItems(itemQuery.value).then(response => {
    itemList.value = response.rows
    itemTotal.value = response.total
    itemLoading.value = false
  }).catch(() => {
    itemLoading.value = false
  })
}

/** 刷新两个表格 */
function refreshBoth() {
  getFeedList()
  getItemList()
}

/** 选中某个订阅源：下方只看该源的条目 */
function handleCurrentFeedChange(row: BlogLink | null) {
  currentFeed.value = row
  itemQuery.value.linkId = row?.linkId
  // 换了过滤条件，页码要回到第一页，否则可能停在超出范围的页上
  itemQuery.value.pageNum = 1
  getItemList()
}

/** 取消选中，恢复查看全部条目 */
function clearCurrentFeed() {
  feedTableRef.value?.setCurrentRow(null)
  handleCurrentFeedChange(null)
}

/** 订阅源搜索 */
function handleFeedQuery() {
  feedQuery.value.pageNum = 1
  getFeedList()
}

/** 订阅源重置 */
function resetFeedQuery() {
  proxy.resetForm("feedQueryRef")
  handleFeedQuery()
}

/** 条目搜索 */
function handleItemQuery() {
  itemQuery.value.pageNum = 1
  getItemList()
}

/** 条目重置：只清标题/作者，保留主从联动的 linkId */
function resetItemQuery() {
  itemQuery.value.title = undefined
  itemQuery.value.author = undefined
  handleItemQuery()
}

/** 订阅源多选 */
function handleFeedSelectionChange(selection: BlogLink[]) {
  feedIds.value = selection.map(item => item.linkId as number)
  feedMultiple.value = !selection.length
}

/** 条目多选 */
function handleItemSelectionChange(selection: BlogFeedItem[]) {
  itemIds.value = selection.map(item => item.itemId as number)
  itemMultiple.value = !selection.length
}

/** 取消按钮 */
function cancel() {
  open.value = false
  reset()
}

/** 表单重置 */
function reset() {
  form.value = {
    linkId: undefined,
    linkName: undefined,
    linkUrl: undefined,
    avatar: undefined,
    rssUrl: undefined,
    orderNum: 0,
    status: "0",
    remark: undefined
  }
  proxy.resetForm("feedRef")
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  open.value = true
  title.value = "新增订阅源"
}

/** 修改按钮操作 */
function handleUpdate(row: BlogLink) {
  reset()
  getFeed(row.linkId as number).then(response => {
    form.value = response.data as BlogLink
    open.value = true
    title.value = "修改订阅源"
  })
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["feedRef"].validate((valid: boolean) => {
    if (!valid) {
      return
    }
    submitting.value = true
    const request = form.value.linkId ? updateFeed(form.value) : addFeed(form.value)
    request.then(() => {
      proxy.$modal.msgSuccess(form.value.linkId ? "修改成功" : "新增成功")
      open.value = false
      getFeedList()
    }).finally(() => {
      submitting.value = false
    })
  })
}

/** 删除订阅源（连带其条目） */
function handleDelete(row?: BlogLink) {
  const linkIds = row?.linkId ? [row.linkId] : feedIds.value
  proxy.$modal.confirm('删除订阅源会一并删除其下全部条目，是否确认删除编号为"' + linkIds + '"的订阅源？').then(() => {
    return delFeed(linkIds)
  }).then(() => {
    // 被删的可能正是当前联动选中的源，这里统一回到「查看全部」
    clearCurrentFeed()
    getFeedList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

/** 删除条目 */
function handleItemDelete(row?: BlogFeedItem) {
  const ids = row?.itemId ? [row.itemId] : itemIds.value
  proxy.$modal.confirm('是否确认删除条目编号为"' + ids + '"的数据项？').then(() => {
    return delFeedItem(ids)
  }).then(() => {
    getItemList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

/** 展示同步结果 */
function showSyncResult(result: BlogFeedSyncResult) {
  const msg = `共 ${result.total} 个源，成功 ${result.success}，失败 ${result.failed}，新增 ${result.newItems} 条`
  if (result.failed > 0) {
    // 单源失败是被隔离的，整体仍算跑完，用警告而非报错
    proxy.$modal.msgWarning(msg + "。失败原因见对应订阅源的「最后错误」列")
  } else {
    proxy.$modal.msgSuccess(msg)
  }
}

/** 全部同步 */
function handleSyncAll() {
  syncing.value = true
  syncAllFeeds().then(response => {
    showSyncResult(response.data as BlogFeedSyncResult)
    refreshBoth()
  }).finally(() => {
    syncing.value = false
  })
}

/** 同步单个订阅源 */
function handleSyncOne(row: BlogLink) {
  if (row.status !== "0") {
    proxy.$modal.msgWarning("该订阅源已停用，请先启用再同步")
    return
  }
  syncing.value = true
  syncOneFeed(row.linkId as number).then(response => {
    showSyncResult(response.data as BlogFeedSyncResult)
    refreshBoth()
  }).finally(() => {
    syncing.value = false
  })
}

getFeedList()
getItemList()
</script>

<style scoped>
.feed-card-header {
   display: flex;
   align-items: center;
   justify-content: space-between;
   font-weight: 700;
}

.feed-card-tip {
   font-size: 12px;
   font-weight: 400;
   color: #909399;
}

.feed-filter-tag {
   margin-left: 8px;
}

.feed-never,
.feed-ok {
   color: #909399;
}
</style>
