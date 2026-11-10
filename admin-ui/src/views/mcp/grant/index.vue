<template>
  <div class="app-container mcp-admin">
    <McpPageHeader
      title="用户授权"
      eyebrow="全站授权关系"
      description="查看所有用户对 MCP 客户端的授权并撤销。撤销后，客户端需要用户重新授权才能继续操作。"
    >
      <template #icon><el-icon><Lock /></el-icon></template>
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </template>
    </McpPageHeader>

    <section class="mcp-filter">
      <el-form :model="query" :inline="true" @submit.prevent="search">
        <el-form-item label="用户 ID">
          <el-input-number v-model="query.userId" :min="1" :precision="0" :controls="false" />
        </el-form-item>
        <el-form-item label="客户端">
          <el-input v-model="query.keyword" placeholder="名称或客户端标识" clearable maxlength="128" style="width: 220px" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="授权状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 140px">
            <el-option label="有效" value="0" />
            <el-option label="已撤销" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="search">搜索</el-button>
          <el-button :icon="Refresh" @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="mcp-panel mcp-table">
      <div class="mcp-panel__head">
        <div>
          <h2 class="mcp-panel__title">授权列表</h2>
          <p class="mcp-panel__desc">共 {{ total }} 条授权；已撤销的记录保留供追溯，不能恢复旧凭据。</p>
        </div>
      </div>
      <div class="mcp-panel__body mcp-panel__body--flush">
        <el-table :data="rows" v-loading="loading" row-key="grantId" empty-text="暂无用户授权">
          <el-table-column prop="grantId" label="授权 ID" width="100" />
          <el-table-column prop="userId" label="用户 ID" width="100" />
          <el-table-column prop="clientName" label="客户端" min-width="180" show-overflow-tooltip />
          <el-table-column label="授权状态" width="110">
            <template #default="{ row }">
              <el-tag :type="row.status === '0' ? 'success' : 'info'" effect="light" round>{{ row.status === '0' ? '有效' : '已撤销' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="客户端状态" width="120">
            <template #default="{ row }">
              <span class="mcp-status" :class="{ 'mcp-status--off': row.clientStatus !== '0' }">
                {{ row.clientStatus === '0' ? '正常' : '停用' }}
              </span>
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
      </div>
      <div v-if="total > 0" class="mcp-panel__foot">
        <pagination :total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="load" />
      </div>
    </section>

    <AdminDetail v-model="open" title="用户授权详情" :fields="fields" />
  </div>
</template>

<script setup lang="ts" name="McpGrant">
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleClose, Lock, Refresh, Search, View } from '@element-plus/icons-vue'
import { listMcpGrants, getMcpGrant, revokeMcpGrant } from '@/api/mcp/management'
import type { McpAdminGrant, McpAdminQuery } from '@/types'
import AdminDetail from '../components/AdminDetail.vue'
import McpPageHeader from '../components/McpPageHeader.vue'
import '../admin.css'

const query = reactive<McpAdminQuery>({ pageNum: 1, pageSize: 20 })
const rows = ref<McpAdminGrant[]>([]), total = ref(0), loading = ref(false), changing = ref(false), open = ref(false)
const fields = ref<Record<string, unknown>>({})
let loadVersion = 0
/** 后台全站查询仍由方法权限控制。 */
async function load(): Promise<void> {
  const version = ++loadVersion
  loading.value = true
  try {
    const result = await listMcpGrants({ ...query })
    if (version === loadVersion) { rows.value = result.rows; total.value = result.total }
  } catch { if (version === loadVersion) { rows.value = []; total.value = 0 } }
  finally { if (version === loadVersion) loading.value = false }
}
/** 条件改变重置页码。 */
function search(): void { query.pageNum = 1; void load() }
/** 清除所有可选条件。 */
function reset(): void { query.userId = undefined; query.keyword = undefined; query.status = undefined; search() }
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
  try { await revokeMcpGrant(row.grantId); ElMessage.success('授权已撤销'); await load() }
  catch { /* 请求层已显示后台错误。 */ }
  finally { changing.value = false }
}
void load()
</script>
