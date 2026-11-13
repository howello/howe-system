<template>
  <div class="app-container">
    <el-form :model="query" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="用户 ID" prop="userId">
        <el-input-number v-model="query.userId" :min="1" :precision="0" :controls="false" style="width: 140px" />
      </el-form-item>
      <el-form-item label="客户端" prop="keyword">
        <el-input v-model="query.keyword" placeholder="名称或客户端标识" clearable maxlength="128" style="width: 220px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="授权状态" prop="status">
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 160px">
          <el-option label="有效" value="0" />
          <el-option label="已撤销" value="1" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button plain icon="Refresh" :loading="loading" @click="getList">刷新列表</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="rows" row-key="grantId" empty-text="暂无用户授权">
      <el-table-column prop="grantId" label="授权 ID" width="100" />
      <el-table-column prop="userId" label="用户 ID" width="100" />
      <el-table-column prop="clientName" label="客户端" min-width="180" show-overflow-tooltip />
      <el-table-column label="授权状态" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === '0' ? 'success' : 'info'" effect="light" round>{{ row.status === '0' ? '有效' : '已撤销' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="客户端状态" width="120" align="center">
        <template #default="{ row }">
          <el-tag :type="row.clientStatus === '0' ? 'success' : 'info'" effect="plain" size="small">
            {{ row.clientStatus === '0' ? '正常' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="authorizationVersion" label="授权版本" width="100" align="center" />
      <el-table-column prop="updatedAt" label="更新时间" min-width="190" />
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" :icon="View" v-hasPermi="['mcp:grant:query']" @click="detail(row)">详情</el-button>
          <el-button
            link
            type="danger"
            :icon="CircleClose"
            :disabled="changing || row.status !== '0'"
            v-hasPermi="['mcp:grant:revoke']"
            @click="revoke(row)"
          >撤销</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="query.pageNum"
      v-model:limit="query.pageSize"
      @pagination="getList"
    />

    <el-dialog v-model="open" title="用户授权详情" width="min(780px, 94vw)" append-to-body>
      <el-descriptions :column="1" border>
        <el-descriptions-item v-for="(value, label) in fields" :key="label" :label="label">{{ display(value) }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="open = false">关 闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="McpGrant">
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleClose, View } from '@element-plus/icons-vue'
import { listMcpGrants, getMcpGrant, revokeMcpGrant } from '@/api/mcp/management'
import type { McpAdminGrant, McpAdminQuery } from '@/types'

const showSearch = ref(true)
const query = reactive<McpAdminQuery>({ pageNum: 1, pageSize: 20 })
const rows = ref<McpAdminGrant[]>([]), total = ref(0), loading = ref(false), changing = ref(false), open = ref(false)
const fields = ref<Record<string, unknown>>({})

/** 后台全站查询仍由方法权限控制。 */
async function getList(): Promise<void> {
  loading.value = true
  try {
    const result = await listMcpGrants({ ...query })
    rows.value = result.rows
    total.value = result.total
  } catch { rows.value = []; total.value = 0 }
  finally { loading.value = false }
}
/** 条件改变重置页码。 */
function handleQuery(): void { query.pageNum = 1; void getList() }
/** 清除所有可选条件。 */
function resetQuery(): void { query.userId = undefined; query.keyword = undefined; query.status = undefined; handleQuery() }
/** 只读展示授权和客户端状态。 */
async function detail(row: McpAdminGrant): Promise<void> {
  try {
    const grant = (await getMcpGrant(row.grantId)).data!
    fields.value = { '授权 ID': grant.grantId, '用户 ID': grant.userId, '客户端': grant.clientName, '客户端标识': grant.clientId,
      '资源地址': grant.resource, '授权版本': grant.authorizationVersion, '授权状态': grant.status === '0' ? '有效' : '已撤销',
      '创建时间': grant.createdAt, '更新时间': grant.updatedAt, '撤销时间': grant.revokedAt }
    open.value = true
  } catch { /* 请求层已显示后台错误。 */ }
}
/** 撤销不会提供恢复旧凭据按钮。 */
async function revoke(row: McpAdminGrant): Promise<void> {
  if (changing.value) return
  try { await ElMessageBox.confirm(`撤销用户 ${row.userId} 对“${row.clientName}”的授权？`, '撤销授权', { type: 'warning', cancelButtonText: '取消', confirmButtonText: '撤销' }) }
  catch { return }
  changing.value = true
  try { await revokeMcpGrant(row.grantId); ElMessage.success('授权已撤销'); await getList() }
  catch { /* 请求层已显示后台错误。 */ }
  finally { changing.value = false }
}
/** 文本插值展示，空值使用占位。 */
function display(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  return typeof value === 'object' ? JSON.stringify(value, null, 2) : String(value)
}

void getList()
</script>
