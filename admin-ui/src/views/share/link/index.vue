<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
      <el-form-item label="文件名" prop="fileName">
        <el-input v-model="queryParams.fileName" placeholder="请输入文件名" clearable style="width: 180px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="短码" prop="code">
        <el-input v-model="queryParams.code" placeholder="请输入短码" clearable style="width: 140px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="源" prop="sourceId">
        <el-select v-model="queryParams.sourceId" placeholder="全部源" clearable style="width: 180px">
          <el-option v-for="s in sourceOptions" :key="s.sourceId" :label="s.name" :value="s.sourceId" />
        </el-select>
      </el-form-item>
      <el-form-item label="链接状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 140px">
          <el-option label="正常" value="0" />
          <el-option label="已失效" value="1" />
          <el-option label="已焚毁" value="2" />
        </el-select>
      </el-form-item>
      <el-form-item label="缓存状态" prop="cacheStatus">
        <el-select v-model="queryParams.cacheStatus" placeholder="全部" clearable style="width: 140px">
          <el-option label="待缓存" value="0" />
          <el-option label="缓存中" value="1" />
          <el-option label="就绪" value="2" />
          <el-option label="失败" value="3" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="handleQuery">搜索</el-button>
        <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain :icon="Plus" @click="handleAdd" v-hasPermi="['share:link:add']">创建分享</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain :icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['share:link:remove']">删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="linkList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="文件名" prop="fileName" min-width="180" show-overflow-tooltip />
      <el-table-column label="短链" min-width="240">
        <template #default="{ row }">
          <el-button v-if="row.shortUrl" link type="primary" :icon="CopyDocument" @click="copyUrl(row.shortUrl)">{{ row.shortUrl }}</el-button>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="源" prop="sourceName" width="130" show-overflow-tooltip />
      <el-table-column label="大小" width="100" align="right">
        <template #default="{ row }">{{ fmtSize(row.fileSize) }}</template>
      </el-table-column>
      <el-table-column label="存储" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" effect="plain">{{ row.storageType === '1' ? 'R2' : '本地' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="查看次数" width="110" align="center">
        <template #default="{ row }">
          {{ row.maxViews && row.maxViews > 0 ? row.viewCount + ' / ' + row.maxViews : row.viewCount + ' / 不限' }}
        </template>
      </el-table-column>
      <el-table-column label="缓存状态" width="100" align="center">
        <template #default="{ row }">
          <el-tooltip :disabled="row.cacheStatus !== '3'" :content="row.cacheError || ''">
            <el-tag :type="cacheTagType(row.cacheStatus)" size="small">{{ cacheLabel(row.cacheStatus) }}</el-tag>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="链接状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="过期时间" align="center" prop="expireAt" width="160">
        <template #default="{ row }">{{ row.expireAt || '长期有效' }}</template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="300" fixed="right" class-name="small-padding fixed-width">
        <template #default="{ row }">
          <el-button link type="primary" :icon="Edit" @click="handleUpdate(row)" v-hasPermi="['share:link:edit']">修改</el-button>
          <el-button v-if="row.status === '0'" link type="warning" @click="handleChangeStatus(row, '1')" v-hasPermi="['share:link:edit']">失效</el-button>
          <el-button v-else-if="row.status === '1'" link type="success" @click="handleChangeStatus(row, '0')" v-hasPermi="['share:link:edit']">恢复</el-button>
          <el-button v-if="row.cacheStatus === '3'" link type="warning" @click="handleRecache(row)" v-hasPermi="['share:link:recache']">重新缓存</el-button>
          <el-button link type="primary" @click="handleLogs(row)" v-hasPermi="['share:link:query']">访问记录</el-button>
          <el-button link type="danger" :icon="Delete" @click="handleDelete(row)" v-hasPermi="['share:link:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 创建 / 修改 -->
    <el-dialog :title="title" v-model="open" width="640px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="WebDAV 源" prop="sourceId">
          <el-select v-model="form.sourceId" placeholder="请选择源" style="width: 100%" :disabled="!!form.linkId">
            <el-option v-for="s in sourceOptions" :key="s.sourceId" :label="s.name" :value="s.sourceId" />
          </el-select>
        </el-form-item>
        <el-form-item label="文件" prop="filePath">
          <el-input v-model="form.filePath" placeholder="点击选择文件" readonly class="file-pick-input" @click="openBrowser">
            <template #prefix>
              <el-icon><FolderOpened /></el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="访问密码" prop="password">
          <el-input v-model="form.password" type="password" show-password
                    :placeholder="form.linkId ? '留空表示不修改密码' : '留空表示无密码'" />
        </el-form-item>
        <el-form-item v-if="form.linkId" label=" ">
          <el-checkbox v-model="clearPassword">清除访问密码</el-checkbox>
        </el-form-item>
        <el-form-item label="过期时间" prop="expireAt">
          <el-date-picker v-model="form.expireAt" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                          placeholder="留空表示不过期" style="width: 100%" :shortcuts="expireShortcuts" />
        </el-form-item>
        <el-form-item label="最大查看次数" prop="maxViews">
          <el-input-number v-model="form.maxViews" :min="0" :max="99999" controls-position="right" />
          <span class="form-tip">0 表示不限次数</span>
        </el-form-item>
        <el-form-item label="阅后即焚" prop="burnAfterRead">
          <el-switch v-model="form.burnAfterRead" active-value="1" inactive-value="0" />
          <span class="form-tip">开启后，最后一次查看结束即焚毁文件</span>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 访问记录 -->
    <el-dialog title="访问记录" v-model="logsOpen" width="820px" append-to-body>
      <el-table v-loading="logsLoading" :data="logs" max-height="440">
        <el-table-column label="时间" prop="createTime" width="160" />
        <el-table-column label="动作" prop="action" width="100" />
        <el-table-column label="结果" width="90">
          <template #default="{ row }">
            <el-tag :type="row.result === 'success' ? 'success' : 'danger'" size="small">{{ row.result }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="IP" prop="ip" width="140" />
        <el-table-column label="说明" prop="message" show-overflow-tooltip />
        <el-table-column label="User-Agent" prop="userAgent" min-width="200" show-overflow-tooltip />
        <template #empty>
          <el-empty description="暂无访问记录" :image-size="80" />
        </template>
      </el-table>
    </el-dialog>

    <!-- 目录浏览选文件 -->
    <file-browser-dialog
      v-model="browseOpen"
      :source-id="form.sourceId"
      :source-name="browseSourceName"
      :root-path="browseRootPath"
      :selectable="true"
      @select="onFileSelected"
    />
  </div>
</template>

<script setup lang="ts" name="ShareLink">
import {
  listLink,
  getLink,
  addLink,
  updateLink,
  delLink,
  changeLinkStatus,
  recacheLink,
  listLinkLogs
} from '@/api/share/link'
import { listEnabledSource } from '@/api/share/source'
import type { ShareLink, ShareLinkQueryParams, ShareSource, WebDavEntry, ShareAccessLog } from '@/types'
import FileBrowserDialog from '@/views/share/components/FileBrowserDialog.vue'
import { Search, Refresh, Plus, Delete, Edit, CopyDocument, FolderOpened } from '@element-plus/icons-vue'

const { proxy } = getCurrentInstance() as any

const linkList = ref<ShareLink[]>([])
const sourceOptions = ref<ShareSource[]>([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref<number[]>([])
const multiple = ref(true)
const total = ref(0)
const title = ref('')

const browseOpen = ref(false)
const browseSourceName = ref('')
const browseRootPath = ref('/')

const logsOpen = ref(false)
const logsLoading = ref(false)
const logs = ref<ShareAccessLog[]>([])

const clearPassword = ref(false)

/** 过期时间快捷项 */
function hoursLater(h: number) {
  const d = new Date()
  d.setHours(d.getHours() + h)
  return d
}
function daysLater(n: number) {
  const d = new Date()
  d.setDate(d.getDate() + n)
  return d
}
function monthsLater(n: number) {
  const d = new Date()
  d.setMonth(d.getMonth() + n)
  return d
}
function yearsLater(n: number) {
  const d = new Date()
  d.setFullYear(d.getFullYear() + n)
  return d
}
const expireShortcuts = [
  { text: '1小时', value: () => hoursLater(1) },
  { text: '3小时', value: () => hoursLater(3) },
  { text: '8小时', value: () => hoursLater(8) },
  { text: '12小时', value: () => hoursLater(12) },
  { text: '一天', value: () => daysLater(1) },
  { text: '一周', value: () => daysLater(7) },
  { text: '一月', value: () => monthsLater(1) },
  { text: '一年', value: () => yearsLater(1) }
]

const data = reactive({
  form: {} as ShareLink,
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    fileName: undefined,
    code: undefined,
    sourceId: undefined,
    status: undefined,
    cacheStatus: undefined
  } as ShareLinkQueryParams,
  rules: {
    sourceId: [{ required: true, message: '请选择 WebDAV 源', trigger: 'change' }],
    filePath: [{ required: true, message: '请选择文件', trigger: 'change' }]
  }
})

const { queryParams, form, rules } = toRefs(data)

/** 查询分享列表 */
function getList() {
  loading.value = true
  listLink(queryParams.value)
    .then((response) => {
      linkList.value = response.rows
      total.value = response.total
    })
    .finally(() => {
      loading.value = false
    })
}

/** 查询可用源 */
function getSources() {
  listEnabledSource().then((response) => {
    sourceOptions.value = response.data || []
  })
}

function cancel() {
  open.value = false
  reset()
}

function reset() {
  clearPassword.value = false
  form.value = {
    linkId: undefined,
    sourceId: undefined,
    filePath: undefined,
    password: undefined,
    expireAt: undefined,
    maxViews: 1,
    burnAfterRead: '0',
    remark: undefined
  }
  proxy.resetForm('formRef')
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm('queryRef')
  handleQuery()
}

function handleSelectionChange(selection: ShareLink[]) {
  ids.value = selection.map((item) => item.linkId as number)
  multiple.value = !selection.length
}

function handleAdd() {
  reset()
  open.value = true
  title.value = '创建分享'
}

function handleUpdate(row: ShareLink) {
  reset()
  getLink(row.linkId as number).then((response) => {
    const detail = response.data
    if (!detail) {
      proxy.$modal.msgError('未获取到分享详情')
      return
    }
    form.value = {
      linkId: detail.linkId,
      sourceId: detail.sourceId,
      filePath: detail.filePath,
      password: undefined,
      expireAt: detail.expireAt,
      maxViews: detail.maxViews,
      burnAfterRead: detail.burnAfterRead,
      remark: detail.remark
    }
    open.value = true
    title.value = '修改分享'
  })
}

function submitForm() {
  proxy.$refs['formRef'].validate((valid: boolean) => {
    if (!valid) return
    if (form.value.linkId) {
      submitUpdate()
    } else {
      submitCreate()
    }
  })
}

function submitCreate() {
  addLink({
    sourceId: form.value.sourceId,
    filePath: form.value.filePath,
    password: form.value.password || undefined,
    expireAt: form.value.expireAt || undefined,
    maxViews: form.value.maxViews,
    burnAfterRead: form.value.burnAfterRead,
    remark: form.value.remark
  }).then((response) => {
    const url = response.data?.shortUrl
    proxy.$modal.msgSuccess('创建成功')
    open.value = false
    getList()
    if (url) {
      copyUrl(url, '短链已复制到剪贴板')
    }
  })
}

function submitUpdate() {
  const payload: any = {
    linkId: form.value.linkId,
    expireAt: form.value.expireAt || null,
    maxViews: form.value.maxViews,
    burnAfterRead: form.value.burnAfterRead,
    remark: form.value.remark
  }
  if (clearPassword.value) {
    payload.password = ''
  } else if (form.value.password) {
    payload.password = form.value.password
  }
  updateLink(payload).then(() => {
    proxy.$modal.msgSuccess('修改成功')
    open.value = false
    getList()
  })
}

function handleDelete(row?: ShareLink) {
  const linkIds = row?.linkId ? [row.linkId] : ids.value
  proxy.$modal
    .confirm('是否确认删除选中的分享？删除后短链立即失效，且会清理已缓存的文件。')
    .then(() => delLink(linkIds))
    .then(() => {
      getList()
      proxy.$modal.msgSuccess('删除成功')
    })
    .catch(() => {})
}

function handleChangeStatus(row: ShareLink, status: string) {
  const action = status === '1' ? '失效' : '恢复'
  const tip = status === '1'
    ? '是否确认失效该分享？失效后短链将立即不可访问。'
    : '是否确认恢复该分享？恢复后访问次数清零并重新缓存（若已过有效期仍不可访问）。'
  proxy.$modal
    .confirm(tip)
    .then(() => changeLinkStatus(row.linkId as number, status))
    .then(() => {
      getList()
      proxy.$modal.msgSuccess(`${action}成功`)
    })
    .catch(() => {})
}

function handleRecache(row: ShareLink) {
  recacheLink(row.linkId as number).then(() => {
    proxy.$modal.msgSuccess('已重新提交缓存')
    getList()
  })
}

function handleLogs(row: ShareLink) {
  logsOpen.value = true
  logsLoading.value = true
  listLinkLogs(row.linkId as number)
    .then((response) => {
      logs.value = response.data || []
    })
    .finally(() => {
      logsLoading.value = false
    })
}

function openBrowser() {
  if (!form.value.sourceId) {
    proxy.$modal.msgWarning('请先选择 WebDAV 源')
    return
  }
  const source = sourceOptions.value.find((s: ShareSource) => s.sourceId === form.value.sourceId)
  browseSourceName.value = source?.name || ''
  browseRootPath.value = source?.rootPath || '/'
  browseOpen.value = true
}

function onFileSelected(entry: WebDavEntry) {
  form.value.filePath = entry.path
}

function copyUrl(url: string, tip = '已复制到剪贴板') {
  if (navigator.clipboard) {
    navigator.clipboard.writeText(url).then(() => proxy.$modal.msgSuccess(tip))
  } else {
    proxy.$modal.msgSuccess(url)
  }
}

function fmtSize(bytes?: number) {
  if (bytes == null) return '-'
  const units = ['B', 'KB', 'MB', 'GB']
  let n = bytes
  let i = 0
  while (n >= 1024 && i < units.length - 1) {
    n = n / 1024
    i++
  }
  return (i === 0 ? n : n.toFixed(1)) + ' ' + units[i]
}

function cacheLabel(status?: string) {
  return { '0': '待缓存', '1': '缓存中', '2': '就绪', '3': '失败' }[status || ''] || '-'
}

function cacheTagType(status?: string) {
  return { '0': 'info', '1': 'warning', '2': 'success', '3': 'danger' }[status || ''] || 'info'
}

function statusLabel(status?: string) {
  return { '0': '正常', '1': '已失效', '2': '已焚毁' }[status || ''] || '-'
}

function statusTagType(status?: string) {
  return { '0': 'success', '1': 'info', '2': 'warning' }[status || ''] || 'info'
}

getSources()
getList()
</script>

<style scoped>
.form-tip {
  margin-left: 10px;
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}
.file-pick-input :deep(.el-input__wrapper) {
  cursor: pointer;
}
</style>
