<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="订单号" prop="orderNo">
        <el-input v-model="queryParams.orderNo" placeholder="请输入订单号" clearable style="width: 180px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="点餐人" prop="userName">
        <el-input v-model="queryParams.userName" placeholder="请输入点餐人" clearable style="width: 160px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="状态" clearable style="width: 150px">
          <el-option label="待接单" value="0" />
          <el-option label="制作中" value="1" />
          <el-option label="已完成" value="2" />
          <el-option label="已取消" value="3" />
        </el-select>
      </el-form-item>
      <el-form-item label="下单时间">
        <el-date-picker
          v-model="dateRange"
          value-format="YYYY-MM-DD"
          type="daterange"
          range-separator="-"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          style="width: 240px"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['meal:order:remove']">删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="orderList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="订单号" align="left" prop="orderNo" width="160">
        <template #default="scope">
          <a class="link-type" style="cursor: pointer" @click="handleDetail(scope.row)">{{ scope.row.orderNo }}</a>
        </template>
      </el-table-column>
      <el-table-column label="点餐人" align="center" prop="userName" width="100" />
      <el-table-column label="份数" align="center" prop="totalCount" width="80" />
      <el-table-column label="整体备注" align="left" prop="orderRemark" :show-overflow-tooltip="true" min-width="160" />
      <el-table-column label="状态" align="center" width="90">
        <template #default="scope">
          <el-tag :type="statusTag(scope.row.status)" size="small">{{ statusText(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="下单时间" align="center" prop="createTime" width="170" />
      <el-table-column label="接单人" align="center" prop="acceptBy" width="100" />
      <el-table-column label="完成人" align="center" prop="finishBy" width="100" />
      <el-table-column label="操作" align="center" width="200" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleDetail(scope.row)">详情</el-button>
          <el-button
            v-if="scope.row.status === '0'"
            link
            type="primary"
            icon="Check"
            @click="handleAccept(scope.row)"
            v-hasPermi="['meal:order:dispatch']"
          >
            接单
          </el-button>
          <el-button
            v-if="scope.row.status === '1'"
            link
            type="primary"
            icon="Select"
            @click="handleFinish(scope.row)"
            v-hasPermi="['meal:order:finish']"
          >
            完成
          </el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['meal:order:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog title="订单详情" v-model="open" width="640px" append-to-body>
      <el-descriptions v-if="detail" :column="2" border>
        <el-descriptions-item label="订单号">{{ detail.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusText(detail.status) }}</el-descriptions-item>
        <el-descriptions-item label="点餐人">{{ detail.userName }}</el-descriptions-item>
        <el-descriptions-item label="下单时间">{{ detail.createTime }}</el-descriptions-item>
        <el-descriptions-item label="接单人">{{ detail.acceptBy || "—" }}</el-descriptions-item>
        <el-descriptions-item label="接单时间">{{ detail.acceptTime || "—" }}</el-descriptions-item>
        <el-descriptions-item label="完成人">{{ detail.finishBy || "—" }}</el-descriptions-item>
        <el-descriptions-item label="完成时间">{{ detail.finishTime || "—" }}</el-descriptions-item>
        <el-descriptions-item label="整体备注" :span="2">{{ detail.orderRemark || "—" }}</el-descriptions-item>
      </el-descriptions>

      <el-table :data="detail?.items || []" style="margin-top: 16px">
        <el-table-column label="菜品" align="left" prop="dishName" min-width="140" />
        <el-table-column label="份数" align="center" prop="count" width="80" />
        <el-table-column label="单项备注" align="left" prop="remark" min-width="140" />
      </el-table>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="open = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="MealOrder">
import { listOrder, getOrder, acceptOrder, finishOrder, delOrder } from "@/api/meal/order"
import type { MealOrder, MealOrderQueryParams } from "@/types/api/meal/order"

const { proxy } = getCurrentInstance() as any

const STATUS_TEXT: Record<string, string> = {
  "0": "待接单",
  "1": "制作中",
  "2": "已完成",
  "3": "已取消"
}

const orderList = ref<MealOrder[]>([])
const detail = ref<MealOrder | null>(null)
const open = ref<boolean>(false)
const loading = ref<boolean>(true)
const showSearch = ref<boolean>(true)
const ids = ref<number[]>([])
const multiple = ref<boolean>(true)
const total = ref<number>(0)
const dateRange = ref<string[]>([])

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    orderNo: undefined,
    userName: undefined,
    status: undefined
  } as MealOrderQueryParams
})

const { queryParams } = toRefs(data)

function statusText(status?: string): string {
  return STATUS_TEXT[status || ""] || "未知"
}

function statusTag(status?: string): "success" | "info" | "warning" | "danger" | "primary" {
  if (status === "0") return "warning"
  if (status === "1") return "primary"
  if (status === "2") return "success"
  return "info"
}

function getList() {
  loading.value = true
  const params: MealOrderQueryParams = { ...queryParams.value }
  if (dateRange.value && dateRange.value.length === 2) {
    params.beginTime = dateRange.value[0]
    params.endTime = dateRange.value[1]
  }
  listOrder(params)
    .then((response: any) => {
      orderList.value = response.rows
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
  dateRange.value = []
  proxy.resetForm("queryRef")
  handleQuery()
}

function handleSelectionChange(selection: MealOrder[]) {
  ids.value = selection.map((item) => item.orderId as number)
  multiple.value = !selection.length
}

function handleDetail(row: MealOrder) {
  getOrder(row.orderId as number).then((response: any) => {
    detail.value = response.data
    open.value = true
  })
}

function handleAccept(row: MealOrder) {
  proxy.$modal
    .confirm(`确认接下订单 ${row.orderNo} 吗？`)
    .then(() => acceptOrder(row.orderId as number))
    .then(() => {
      proxy.$modal.msgSuccess("已接单")
      getList()
    })
    .catch(() => {})
}

function handleFinish(row: MealOrder) {
  proxy.$modal
    .confirm(`确认把订单 ${row.orderNo} 标记为已完成吗？`)
    .then(() => finishOrder(row.orderId as number))
    .then(() => {
      proxy.$modal.msgSuccess("已完成")
      getList()
    })
    .catch(() => {})
}

function handleDelete(row?: MealOrder) {
  const orderIds = row?.orderId ? [row.orderId] : ids.value
  proxy.$modal
    .confirm("是否确认删除选中的订单？删除后点餐端不再展示。")
    .then(() => delOrder(orderIds))
    .then(() => {
      getList()
      proxy.$modal.msgSuccess("删除成功")
    })
    .catch(() => {})
}

getList()
</script>
