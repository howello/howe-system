<template>
   <div class="app-container">
      <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="80px">
         <el-form-item label="场景标识" prop="scene">
            <el-input v-model="queryParams.scene" placeholder="按场景标识搜索" clearable style="width: 200px" @keyup.enter="handleQuery" />
         </el-form-item>
         <el-form-item label="能力类型" prop="capability">
            <el-select v-model="queryParams.capability" placeholder="全部能力" clearable style="width: 150px">
               <el-option v-for="item in capabilityOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
         </el-form-item>
         <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 130px">
               <el-option label="成功" value="SUCCESS" />
               <el-option label="失败" value="FAILED" />
            </el-select>
         </el-form-item>
         <el-form-item label="调用时间">
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
            <el-button type="warning" plain icon="DataAnalysis" @click="handleStats" v-hasPermi="['ai:calllog:stats']">用量统计</el-button>
         </el-col>
         <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table v-loading="loading" :data="logList">
         <el-table-column label="调用时间" align="center" prop="createTime" width="160">
            <template #default="scope">
               <span>{{ parseTime(scope.row.createTime, '{y}-{m}-{d} {h}:{i}:{s}') }}</span>
            </template>
         </el-table-column>
         <el-table-column label="场景" align="left" prop="scene" :show-overflow-tooltip="true" min-width="180" />
         <el-table-column label="能力" align="center" width="100">
            <template #default="scope">
               <el-tag size="small">{{ capabilityLabel(scope.row.capability) }}</el-tag>
            </template>
         </el-table-column>
         <el-table-column label="服务商" align="center" prop="providerCode" width="140" />
         <el-table-column label="模型ID" align="center" prop="modelId" width="90" />
         <el-table-column label="尝试" align="center" prop="attempt" width="70" />
         <el-table-column label="耗时" align="center" width="100">
            <template #default="scope">
               <span>{{ scope.row.elapsedMs }} ms</span>
            </template>
         </el-table-column>
         <el-table-column label="用量" align="center" width="150">
            <template #default="scope">
               <span v-if="scope.row.imageCount">{{ scope.row.imageCount }} 张图</span>
               <span v-else-if="scope.row.inputTokens || scope.row.outputTokens">
                  {{ scope.row.inputTokens || 0 }} / {{ scope.row.outputTokens || 0 }}
               </span>
               <span v-else class="ai-muted">-</span>
            </template>
         </el-table-column>
         <el-table-column label="状态" align="center" width="100">
            <template #default="scope">
               <el-tag v-if="scope.row.status === 'SUCCESS'" type="success" size="small">成功</el-tag>
               <el-tooltip v-else :content="scope.row.errorCode || '失败'" placement="top">
                  <el-tag type="danger" size="small">{{ scope.row.errorCode || "失败" }}</el-tag>
               </el-tooltip>
            </template>
         </el-table-column>
         <el-table-column label="触发人" align="center" prop="operator" width="110" />
         <el-table-column label="操作" align="center" width="90" fixed="right" class-name="small-padding fixed-width">
            <template #default="scope">
               <el-button link type="primary" icon="View" @click="handleDetail(scope.row)" v-hasPermi="['ai:calllog:query']">详情</el-button>
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

      <!-- 详情对话框 -->
      <el-dialog title="调用记录详情" v-model="open" width="640px" append-to-body>
         <el-descriptions :column="2" border>
            <el-descriptions-item label="调用时间">{{ detail.createTime }}</el-descriptions-item>
            <el-descriptions-item label="链路ID">{{ detail.traceId || "-" }}</el-descriptions-item>
            <el-descriptions-item label="场景">{{ detail.scene || "-" }}</el-descriptions-item>
            <el-descriptions-item label="能力">{{ capabilityLabel(detail.capability) }}</el-descriptions-item>
            <el-descriptions-item label="服务商">{{ detail.providerCode || "-" }}</el-descriptions-item>
            <el-descriptions-item label="渠道ID">{{ detail.channelId || "-" }}</el-descriptions-item>
            <el-descriptions-item label="模型ID">{{ detail.modelId || "-" }}</el-descriptions-item>
            <el-descriptions-item label="第几次尝试">{{ detail.attempt }}</el-descriptions-item>
            <el-descriptions-item label="输入 token">{{ detail.inputTokens || "-" }}</el-descriptions-item>
            <el-descriptions-item label="输出 token">{{ detail.outputTokens || "-" }}</el-descriptions-item>
            <el-descriptions-item label="图片张数">{{ detail.imageCount || "-" }}</el-descriptions-item>
            <el-descriptions-item label="耗时">{{ detail.elapsedMs }} ms</el-descriptions-item>
            <el-descriptions-item label="状态">{{ detail.status }}</el-descriptions-item>
            <el-descriptions-item label="错误码">{{ detail.errorCode || "-" }}</el-descriptions-item>
            <el-descriptions-item label="触发人">{{ detail.operator || "-" }}</el-descriptions-item>
            <el-descriptions-item label="入参摘要" :span="2">
               <span class="ai-mono">{{ detail.requestDigest || "-" }}</span>
            </el-descriptions-item>
         </el-descriptions>
         <template #footer>
            <div class="dialog-footer">
               <el-button @click="open = false">关 闭</el-button>
            </div>
         </template>
      </el-dialog>

      <!-- 用量统计对话框 -->
      <el-dialog title="用量统计" v-model="statsOpen" width="760px" append-to-body>
         <el-form :inline="true" label-width="80px">
            <el-form-item label="聚合维度">
               <el-select v-model="statsParams.groupBy" style="width: 160px" @change="loadStats">
                  <el-option label="按场景" value="scene" />
                  <el-option label="按模型" value="model" />
                  <el-option label="按天" value="day" />
               </el-select>
            </el-form-item>
         </el-form>
         <el-table v-loading="statsLoading" :data="statsList" max-height="400">
            <el-table-column label="维度值" align="left" prop="groupKey" min-width="160" />
            <el-table-column label="调用次数" align="center" prop="totalCount" width="100" />
            <el-table-column label="成功" align="center" prop="successCount" width="80" />
            <el-table-column label="失败" align="center" prop="failedCount" width="80" />
            <el-table-column label="输入 token" align="center" prop="inputTokens" width="110" />
            <el-table-column label="输出 token" align="center" prop="outputTokens" width="110" />
            <el-table-column label="图片张数" align="center" prop="imageCount" width="100" />
            <el-table-column label="平均耗时" align="center" width="110">
               <template #default="scope">
                  <span>{{ scope.row.avgElapsedMs }} ms</span>
               </template>
            </el-table-column>
         </el-table>
         <template #footer>
            <div class="dialog-footer">
               <el-button @click="statsOpen = false">关 闭</el-button>
            </div>
         </template>
      </el-dialog>
   </div>
</template>

<script setup lang="ts" name="AiCallLog">
import { listCallLog, getCallLog, getCallLogStats } from "@/api/ai/calllog"
import type { AiCallLog, AiCallLogQueryParams, AiCallLogStats } from "@/types/api/ai/calllog"

const { proxy } = getCurrentInstance()

const logList = ref<AiCallLog[]>([])
const statsList = ref<AiCallLogStats[]>([])
const open = ref<boolean>(false)
const statsOpen = ref<boolean>(false)
const loading = ref<boolean>(true)
const statsLoading = ref<boolean>(false)
const showSearch = ref<boolean>(true)
const total = ref<number>(0)
const dateRange = ref<string[]>([])
const detail = ref<AiCallLog>({})

const capabilityOptions = [
  { label: "文本对话", value: "CHAT" },
  { label: "图片理解", value: "VISION" },
  { label: "文生图", value: "IMAGE" },
  { label: "文本向量", value: "EMBEDDING" }
]

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    scene: undefined,
    capability: undefined,
    status: undefined
  } as AiCallLogQueryParams,
  statsParams: {
    groupBy: "scene"
  }
})

const { queryParams, statsParams } = toRefs(data)

/** 能力类型转展示名 */
function capabilityLabel(capability?: string): string {
  const matched = capabilityOptions.find((item: { label: string; value: string }) => item.value === capability)
  return matched?.label || capability || "-"
}

/** 查询调用记录列表 */
function getList() {
  loading.value = true
  listCallLog(proxy.addDateRange(queryParams.value, dateRange.value)).then(response => {
    logList.value = response.rows
    total.value = response.total
    loading.value = false
  }).catch(() => {
    loading.value = false
  })
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

/** 查看详情 */
function handleDetail(row: AiCallLog) {
  getCallLog(row.id as number).then(response => {
    detail.value = response.data as AiCallLog
    open.value = true
  })
}

/** 打开用量统计 */
function handleStats() {
  statsOpen.value = true
  loadStats()
}

/** 加载用量统计 */
function loadStats() {
  statsLoading.value = true
  const range = dateRange.value || []
  getCallLogStats({
    groupBy: statsParams.value.groupBy,
    beginTime: range.length === 2 ? range[0] : undefined,
    endTime: range.length === 2 ? range[1] : undefined
  }).then(response => {
    statsList.value = response.data || []
    statsLoading.value = false
  }).catch(() => {
    statsLoading.value = false
  })
}

getList()
</script>

<style scoped>
.ai-muted {
   color: #909399;
}

.ai-mono {
   font-family: Consolas, Monaco, monospace;
   font-size: 12px;
}
</style>
