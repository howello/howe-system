<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
      <el-form-item label="源名称" prop="name">
        <el-input v-model="queryParams.name" placeholder="请输入源名称" clearable style="width: 200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="源状态" clearable style="width: 160px">
          <el-option v-for="dict in sys_normal_disable" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="handleQuery">搜索</el-button>
        <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain :icon="Plus" @click="handleAdd" v-hasPermi="['share:source:add']">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="success" plain :icon="Edit" :disabled="single" @click="handleUpdate()" v-hasPermi="['share:source:edit']">修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="danger" plain :icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['share:source:remove']">删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="sourceList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="源名称" prop="name" show-overflow-tooltip />
      <el-table-column label="根地址" prop="baseUrl" show-overflow-tooltip />
      <el-table-column label="账号" prop="username" width="180" show-overflow-tooltip />
      <el-table-column label="根目录" prop="rootPath" width="120" show-overflow-tooltip />
      <el-table-column label="密码" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.passwordSet ? 'success' : 'info'" size="small" effect="plain">
            {{ row.passwordSet ? '已配置' : '未配置' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" width="90">
        <template #default="{ row }">
          <dict-tag :options="sys_normal_disable" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" width="160" />
      <el-table-column label="操作" align="center" width="240" fixed="right" class-name="small-padding fixed-width">
        <template #default="{ row }">
          <el-button link type="primary" :icon="Edit" @click="handleUpdate(row)" v-hasPermi="['share:source:edit']">修改</el-button>
          <el-button link type="primary" :icon="Connection" :loading="testingId === row.sourceId" @click="handleTest(row)" v-hasPermi="['share:source:query']">测试</el-button>
          <el-button link type="primary" :icon="Folder" @click="handleBrowse(row)" v-hasPermi="['share:source:query']">浏览</el-button>
          <el-button link type="danger" :icon="Delete" @click="handleDelete(row)" v-hasPermi="['share:source:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <!-- 新增 / 修改 -->
    <el-dialog :title="title" v-model="open" width="600px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="源名称" prop="name">
          <el-input v-model="form.name" placeholder="如：我的坚果云" />
        </el-form-item>
        <el-form-item label="根地址" prop="baseUrl">
          <el-input v-model="form.baseUrl" placeholder="如：https://dav.jianguoyun.com/dav/" />
        </el-form-item>
        <el-form-item label="账号" prop="username">
          <el-input v-model="form.username" placeholder="WebDAV 账号" />
        </el-form-item>
        <el-form-item label="应用密码" prop="password">
          <el-input v-model="form.password" type="password" show-password
                    :placeholder="form.sourceId ? '留空表示不修改密码' : '请输入应用密码'" />
        </el-form-item>
        <el-form-item label="根目录" prop="rootPath">
          <el-input v-model="form.rootPath" placeholder="默认 /" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio v-for="dict in sys_normal_disable" :key="dict.value" :value="dict.value">{{ dict.label }}</el-radio>
          </el-radio-group>
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

    <!-- 目录浏览 -->
    <file-browser-dialog
      v-model="browseOpen"
      :source-id="browseSourceId"
      :source-name="browseSourceName"
      :root-path="browseRootPath"
      :selectable="false"
    />
  </div>
</template>

<script setup lang="ts" name="ShareSource">
import { listSource, getSource, addSource, updateSource, delSource, testSource } from '@/api/share/source'
import type { ShareSource, ShareSourceQueryParams } from '@/types'
import FileBrowserDialog from '@/views/share/components/FileBrowserDialog.vue'
import { Search, Refresh, Plus, Edit, Delete, Connection, Folder } from '@element-plus/icons-vue'

const { proxy } = getCurrentInstance() as any
const { sys_normal_disable } = useDict('sys_normal_disable')

const sourceList = ref<ShareSource[]>([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref<number[]>([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref('')
const testingId = ref<number | undefined>(undefined)

const browseOpen = ref(false)
const browseSourceId = ref<number | undefined>(undefined)
const browseSourceName = ref('')
const browseRootPath = ref('/')

const data = reactive({
  form: {} as ShareSource,
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    name: undefined,
    status: undefined
  } as ShareSourceQueryParams,
  rules: {
    name: [{ required: true, message: '源名称不能为空', trigger: 'blur' }],
    baseUrl: [{ required: true, message: 'WebDAV 根地址不能为空', trigger: 'blur' }],
    username: [{ required: true, message: '账号不能为空', trigger: 'blur' }]
  }
})

const { queryParams, form, rules } = toRefs(data)

/** 查询源列表 */
function getList() {
  loading.value = true
  listSource(queryParams.value)
    .then((response) => {
      sourceList.value = response.rows
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
    sourceId: undefined,
    name: undefined,
    baseUrl: undefined,
    username: undefined,
    password: undefined,
    rootPath: '/',
    status: '0',
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

function handleSelectionChange(selection: ShareSource[]) {
  ids.value = selection.map((item) => item.sourceId as number)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

function handleAdd() {
  reset()
  open.value = true
  title.value = '新增 WebDAV 源'
}

function handleUpdate(row?: ShareSource) {
  reset()
  const sourceId = row?.sourceId || ids.value[0]
  getSource(sourceId).then((response) => {
    form.value = response.data
    form.value.password = undefined
    open.value = true
    title.value = '修改 WebDAV 源'
  })
}

function submitForm() {
  proxy.$refs['formRef'].validate((valid: boolean) => {
    if (!valid) return
    const payload = { ...form.value }
    if (payload.sourceId && !payload.password) {
      delete payload.password
    }
    const action = payload.sourceId ? updateSource(payload) : addSource(payload)
    action.then(() => {
      proxy.$modal.msgSuccess(payload.sourceId ? '修改成功' : '新增成功')
      open.value = false
      getList()
    })
  })
}

function handleDelete(row?: ShareSource) {
  const sourceIds = row?.sourceId || ids.value
  proxy.$modal
    .confirm('是否确认删除选中的 WebDAV 源？')
    .then(() => delSource(sourceIds))
    .then(() => {
      getList()
      proxy.$modal.msgSuccess('删除成功')
    })
    .catch(() => {})
}

function handleTest(row: ShareSource) {
  testingId.value = row.sourceId
  testSource(row.sourceId as number)
    .then(() => proxy.$modal.msgSuccess('连通性测试通过'))
    .finally(() => {
      testingId.value = undefined
    })
}

function handleBrowse(row: ShareSource) {
  browseSourceId.value = row.sourceId
  browseSourceName.value = row.name || ''
  browseRootPath.value = row.rootPath || '/'
  browseOpen.value = true
}

getList()
</script>
