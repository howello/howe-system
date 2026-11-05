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

      <!-- 详情对话框：分「场景信息」与「调用信息」两块 -->
      <el-dialog title="调用记录详情" v-model="open" :width="detailWidth" append-to-body top="6vh">
         <div v-loading="detailLoading" class="detail-wrap">
            <!-- 场景信息：这次调用对应的是哪个服务商的哪个模型 -->
            <section class="detail-block">
               <h4 class="detail-title">场景信息</h4>
               <el-descriptions :column="detailColumns" border>
                  <el-descriptions-item label="场景名称">{{ detail.sceneName || "-" }}</el-descriptions-item>
                  <el-descriptions-item label="场景标识">
                     <span class="ai-mono ai-break">{{ detail.scene || "-" }}</span>
                  </el-descriptions-item>
                  <el-descriptions-item label="能力">{{ capabilityLabel(detail.capability) }}</el-descriptions-item>
                  <el-descriptions-item label="服务商">{{ detail.providerName || detail.providerCode || "-" }}</el-descriptions-item>
                  <el-descriptions-item label="渠道名称">{{ detail.channelName || "-" }}</el-descriptions-item>
                  <el-descriptions-item label="渠道 ID">{{ detail.channelId ?? "-" }}</el-descriptions-item>
                  <el-descriptions-item label="模型名称">
                     <span class="ai-break">{{ detail.modelName || "-" }}</span>
                  </el-descriptions-item>
                  <el-descriptions-item label="模型 ID">{{ detail.modelId ?? "-" }}</el-descriptions-item>
               </el-descriptions>
            </section>

            <!-- 调用信息：这次调用本身的结果与开销 -->
            <section class="detail-block">
               <h4 class="detail-title">调用信息</h4>
               <el-descriptions :column="detailColumns" border>
                  <el-descriptions-item label="调用时间">{{ detail.createTime || "-" }}</el-descriptions-item>
                  <el-descriptions-item label="链路 ID">
                     <span class="ai-mono ai-break">{{ detail.traceId || "-" }}</span>
                  </el-descriptions-item>
                  <el-descriptions-item label="第几次尝试">{{ detail.attempt ?? "-" }}</el-descriptions-item>
                  <el-descriptions-item label="触发人">{{ detail.operator || "-" }}</el-descriptions-item>
                  <el-descriptions-item label="输入 token">{{ detail.inputTokens ?? "-" }}</el-descriptions-item>
                  <el-descriptions-item label="输出 token">{{ detail.outputTokens ?? "-" }}</el-descriptions-item>
                  <el-descriptions-item label="图片张数">{{ detail.imageCount ?? "-" }}</el-descriptions-item>
                  <el-descriptions-item label="耗时">{{ detail.elapsedMs != null ? detail.elapsedMs + " ms" : "-" }}</el-descriptions-item>
                  <el-descriptions-item label="首字延迟">
                     <span>{{ detail.firstTokenMs != null ? detail.firstTokenMs + " ms" : "—" }}</span>
                  </el-descriptions-item>
                  <el-descriptions-item label="状态">
                     <el-tag v-if="detail.status === 'SUCCESS'" type="success" size="small">成功</el-tag>
                     <el-tag v-else type="danger" size="small">失败</el-tag>
                  </el-descriptions-item>
                  <el-descriptions-item label="错误码">{{ detail.errorCode || "-" }}</el-descriptions-item>
               </el-descriptions>

               <div class="detail-sub-title">入参摘要</div>
               <pre class="ai-digest">{{ detail.requestDigest || "-" }}</pre>

               <div class="detail-sub-title">生成图片</div>
               <div v-if="detail.images && detail.images.length" class="detail-images">
                  <el-image
                     v-for="img in detail.images"
                     :key="img.id || img.objectKey"
                     :src="img.url"
                     :preview-src-list="detail.images.map((i: AiImageAsset) => i.url)"
                     fit="cover"
                     class="detail-image"
                     preview-teleported
                  />
               </div>
               <span v-else class="ai-muted">无生成图片</span>
            </section>
         </div>
         <template #footer>
            <div class="dialog-footer">
               <el-button @click="open = false">关 闭</el-button>
            </div>
         </template>
      </el-dialog>

      <!-- 用量统计对话框 -->
      <el-dialog title="用量统计" v-model="statsOpen" width="820px" append-to-body>
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
            <el-table-column :label="groupLabelTitle" align="left" min-width="180" :show-overflow-tooltip="true">
               <template #default="scope">
                  <span>{{ scope.row.groupLabel || scope.row.groupKey }}</span>
                  <span v-if="scope.row.groupLabel && scope.row.groupLabel !== scope.row.groupKey" class="ai-muted ai-key">
                     {{ scope.row.groupKey }}
                  </span>
               </template>
            </el-table-column>
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
import { useAppProxy } from '@/utils/appProxy'
import { listCallLog, getCallLog, getCallLogStats } from "@/api/ai/calllog"
import type { AiCallLog, AiCallLogQueryParams, AiCallLogStats } from "@/types/api/ai/calllog"
import type { AiImageAsset } from "@/types/api/ai/calllog"

const proxy = useAppProxy()

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
const detailLoading = ref<boolean>(false)

/** 详情弹窗宽度与描述列数随视口自适应，避免窄屏撑破 */
const viewportWidth = ref<number>(window.innerWidth)
const detailWidth = computed(() => Math.min(960, Math.round(viewportWidth.value * 0.94)) + 'px')
const detailColumns = computed(() => (viewportWidth.value < 900 ? 1 : 2))

/** 统计维度对应的列标题 */
const groupLabelTitle = computed(() => {
  if (statsParams.value.groupBy === 'model') return '模型'
  if (statsParams.value.groupBy === 'day') return '日期'
  return '场景'
})

function syncViewport() {
  viewportWidth.value = window.innerWidth
}
onMounted(() => window.addEventListener('resize', syncViewport))
onUnmounted(() => window.removeEventListener('resize', syncViewport))

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
  detailLoading.value = true
  open.value = true
  getCallLog(row.id as number).then(response => {
    detail.value = response.data as AiCallLog
    detailLoading.value = false
  }).catch(() => {
    // 失败时不展示上一条的旧数据
    detail.value = {}
    detailLoading.value = false
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

/* 长 ID、模型名允许断行，否则会把描述表格撑破 */
.ai-break {
   word-break: break-all;
   white-space: normal;
}

.detail-wrap {
   min-height: 120px;
}

.detail-block + .detail-block {
   margin-top: 18px;
}

.detail-title {
   margin: 0 0 10px;
   font-size: 14px;
   font-weight: 600;
   color: var(--el-text-color-primary);
   padding-left: 8px;
   border-left: 3px solid var(--el-color-primary);
}

.detail-sub-title {
   margin: 14px 0 8px;
   font-size: 13px;
   font-weight: 600;
   color: var(--el-text-color-regular);
}

/* 入参摘要可能很长：等宽、换行、限高滚动，且不用 v-html */
.ai-digest {
   margin: 0;
   padding: 10px 12px;
   max-height: 220px;
   overflow: auto;
   font-family: Consolas, Monaco, monospace;
   font-size: 12px;
   line-height: 1.6;
   white-space: pre-wrap;
   overflow-wrap: anywhere;
   background: var(--el-fill-color-light);
   border-radius: 6px;
}

.detail-images {
   display: flex;
   flex-wrap: wrap;
   gap: 10px;
}

.detail-image {
   width: 96px;
   height: 96px;
   border-radius: 6px;
}

.ai-key {
   margin-left: 8px;
   font-size: 12px;
}
</style>
