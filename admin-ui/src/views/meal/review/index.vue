<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="评分" prop="score">
        <el-select v-model="queryParams.score" placeholder="评分" clearable style="width: 140px">
          <el-option v-for="item in [5, 4, 3, 2, 1]" :key="item" :label="`${item} 星`" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="评价时间">
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
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()" v-hasPermi="['meal:review:remove']">删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="reviewList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="订单号" align="left" prop="orderNo" width="160" />
      <el-table-column label="评价人" align="center" prop="userName" width="110">
        <template #default="scope">
          {{ scope.row.anonymous === "1" ? "匿名" : scope.row.userName }}
        </template>
      </el-table-column>
      <el-table-column label="评分" align="center" width="150">
        <template #default="scope">
          <el-rate :model-value="scope.row.score" disabled />
        </template>
      </el-table-column>
      <el-table-column label="评价内容" align="left" prop="content" :show-overflow-tooltip="true" min-width="240" />
      <el-table-column label="图片" align="center" width="90">
        <template #default="scope">
          <el-image
            v-if="firstImage(scope.row)"
            :src="firstImage(scope.row)"
            :preview-src-list="imageList(scope.row)"
            fit="cover"
            style="width: 40px; height: 40px; border-radius: 6px"
            preview-teleported
          />
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="匿名" align="center" width="80">
        <template #default="scope">
          <el-tag :type="scope.row.anonymous === '1' ? 'warning' : 'info'" size="small">
            {{ scope.row.anonymous === "1" ? "是" : "否" }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="评价时间" align="center" prop="createTime" width="170" />
      <el-table-column label="操作" align="center" width="100" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['meal:review:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
  </div>
</template>

<script setup lang="ts" name="MealReview">
import { listReview, delReview } from "@/api/meal/review"
import type { MealReview, MealReviewQueryParams } from "@/types/api/meal/review"

const { proxy } = getCurrentInstance() as any

const reviewList = ref<MealReview[]>([])
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
    score: undefined
  } as MealReviewQueryParams
})

const { queryParams } = toRefs(data)

function imageList(row: MealReview): string[] {
  if (!row.images) {
    return []
  }
  try {
    const parsed = JSON.parse(row.images)
    return Array.isArray(parsed) ? parsed : []
  } catch (e) {
    return []
  }
}

function firstImage(row: MealReview): string {
  return imageList(row)[0] || ""
}

function getList() {
  loading.value = true
  const params: MealReviewQueryParams = { ...queryParams.value }
  if (dateRange.value && dateRange.value.length === 2) {
    params.beginTime = dateRange.value[0]
    params.endTime = dateRange.value[1]
  }
  listReview(params)
    .then((response: any) => {
      reviewList.value = response.rows
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

function handleSelectionChange(selection: MealReview[]) {
  ids.value = selection.map((item) => item.reviewId as number)
  multiple.value = !selection.length
}

function handleDelete(row?: MealReview) {
  const reviewIds = row?.reviewId ? [row.reviewId] : ids.value
  proxy.$modal
    .confirm("是否确认删除选中的评价？")
    .then(() => delReview(reviewIds))
    .then(() => {
      getList()
      proxy.$modal.msgSuccess("删除成功")
    })
    .catch(() => {})
}

getList()
</script>
