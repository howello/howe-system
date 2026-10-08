<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="菜名" prop="name">
        <el-input v-model="queryParams.name" placeholder="请输入菜名" clearable style="width: 180px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="状态" clearable style="width: 150px">
          <el-option label="待审核" value="0" />
          <el-option label="已通过" value="1" />
          <el-option label="已驳回" value="2" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['meal:proposal:remove']">删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="proposalList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="参考图" align="center" width="80">
        <template #default="scope">
          <el-image
            v-if="scope.row.image"
            :src="scope.row.image"
            :preview-src-list="[scope.row.image]"
            fit="cover"
            style="width: 40px; height: 40px; border-radius: 6px"
            preview-teleported
          />
          <el-avatar v-else :size="40">{{ (scope.row.name || "?").charAt(0) }}</el-avatar>
        </template>
      </el-table-column>
      <el-table-column label="菜名" align="left" prop="name" :show-overflow-tooltip="true" min-width="140" />
      <el-table-column label="提交人" align="center" prop="userName" width="100" />
      <el-table-column label="建议分类" align="center" prop="categoryName" width="110" />
      <el-table-column label="想吃的理由" align="left" prop="reason" :show-overflow-tooltip="true" min-width="180" />
      <el-table-column label="状态" align="center" width="90">
        <template #default="scope">
          <el-tag :type="statusTag(scope.row.status)" size="small">{{ statusText(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="审核意见" align="left" prop="auditRemark" :show-overflow-tooltip="true" min-width="160" />
      <el-table-column label="提交时间" align="center" prop="createTime" width="170" />
      <el-table-column label="操作" align="center" width="180" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button
            v-if="scope.row.status === '0'"
            link
            type="primary"
            icon="Check"
            @click="handleAudit(scope.row, '1')"
            v-hasPermi="['meal:proposal:audit']"
          >
            通过
          </el-button>
          <el-button
            v-if="scope.row.status === '0'"
            link
            type="danger"
            icon="Close"
            @click="handleAudit(scope.row, '2')"
            v-hasPermi="['meal:proposal:audit']"
          >
            驳回
          </el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['meal:proposal:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog :title="auditTitle" v-model="open" width="560px" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="菜名">
          <span>{{ current?.name }}</span>
        </el-form-item>
        <el-form-item label="审核意见">
          <el-input v-model="auditRemark" type="textarea" :rows="3" placeholder="通过时可以写「已上架，周末做给你们吃」" />
        </el-form-item>
        <el-alert
          v-if="auditStatus === '1'"
          type="info"
          :closable="false"
          title="通过后会自动在菜品表生成一条菜品（来源=新菜通过），并回写到点餐区。"
        />
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="submitting" @click="submitAudit">确 定</el-button>
          <el-button @click="open = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="MealProposal">
import { listProposal, auditProposal, delProposal } from "@/api/meal/proposal"
import type { MealProposal, MealProposalQueryParams } from "@/types/api/meal/proposal"

const { proxy } = getCurrentInstance() as any

const STATUS_TEXT: Record<string, string> = {
  "0": "待审核",
  "1": "已通过",
  "2": "已驳回"
}

const proposalList = ref<MealProposal[]>([])
const current = ref<MealProposal | null>(null)
const open = ref<boolean>(false)
const loading = ref<boolean>(true)
const submitting = ref<boolean>(false)
const showSearch = ref<boolean>(true)
const ids = ref<number[]>([])
const multiple = ref<boolean>(true)
const total = ref<number>(0)
const auditStatus = ref<string>("1")
const auditRemark = ref<string>("")

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    name: undefined,
    status: undefined
  } as MealProposalQueryParams
})

const { queryParams } = toRefs(data)

const auditTitle = computed(() => (auditStatus.value === "1" ? "通过提案" : "驳回提案"))

function statusText(status?: string): string {
  return STATUS_TEXT[status || ""] || "未知"
}

function statusTag(status?: string): "success" | "info" | "warning" | "danger" {
  if (status === "1") return "success"
  if (status === "2") return "danger"
  return "warning"
}

function getList() {
  loading.value = true
  listProposal(queryParams.value)
    .then((response: any) => {
      proposalList.value = response.rows
      total.value = response.total
    })
    .finally(() => {
      loading.value = false
    })
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

function handleSelectionChange(selection: MealProposal[]) {
  ids.value = selection.map((item) => item.proposalId as number)
  multiple.value = !selection.length
}

function handleAudit(row: MealProposal, status: string) {
  current.value = row
  auditStatus.value = status
  auditRemark.value = ""
  open.value = true
}

function submitAudit() {
  if (!current.value) {
    return
  }
  submitting.value = true
  auditProposal({
    proposalId: current.value.proposalId as number,
    status: auditStatus.value,
    auditRemark: auditRemark.value
  })
    .then(() => {
      proxy.$modal.msgSuccess(auditStatus.value === "1" ? "已通过并上架" : "已驳回")
      open.value = false
      getList()
    })
    .finally(() => {
      submitting.value = false
    })
}

function handleDelete(row?: MealProposal) {
  const proposalIds = row?.proposalId ? [row.proposalId] : ids.value
  proxy.$modal
    .confirm("是否确认删除选中的提案？")
    .then(() => delProposal(proposalIds))
    .then(() => {
      getList()
      proxy.$modal.msgSuccess("删除成功")
    })
    .catch(() => {})
}

getList()
</script>
