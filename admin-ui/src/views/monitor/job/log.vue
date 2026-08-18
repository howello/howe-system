<template>
   <div class="app-container">
      <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
         <el-form-item label="任务名称" prop="jobName">
            <el-input
               v-model="queryParams.jobName"
               placeholder="请输入任务名称"
               clearable
               style="width: 240px"
               @keyup.enter="handleQuery"
            />
         </el-form-item>
         <el-form-item label="任务组名" prop="jobGroup">
            <el-select
               v-model="queryParams.jobGroup"
               placeholder="请选择任务组名"
               clearable
               style="width: 240px"
            >
               <el-option
                  v-for="dict in sys_job_group"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
               />
            </el-select>
         </el-form-item>
         <el-form-item label="执行状态" prop="status">
            <el-select
               v-model="queryParams.status"
               placeholder="请选择执行状态"
               clearable
               style="width: 240px"
            >
               <el-option
                  v-for="dict in sys_common_status"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
               />
            </el-select>
         </el-form-item>
         <el-form-item label="执行时间" style="width: 308px">
            <el-date-picker
               v-model="dateRange"
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
            <el-button
               type="danger"
               plain
               icon="Delete"
               :disabled="multiple"
               @click="handleDelete"
               v-hasPermi="['monitor:job:remove']"
            >删除</el-button>
         </el-col>
         <el-col :span="1.5">
            <el-button
               type="danger"
               plain
               icon="Delete"
               @click="handleClean"
               v-hasPermi="['monitor:job:remove']"
            >清空</el-button>
         </el-col>
         <el-col :span="1.5">
            <el-button
               type="warning"
               plain
               icon="Download"
               @click="handleExport"
               v-hasPermi="['monitor:job:export']"
            >导出</el-button>
         </el-col>
         <el-col :span="1.5">
            <el-button 
               type="warning" 
               plain 
               icon="Close"
               @click="handleClose"
            >关闭</el-button>
         </el-col>
         <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
      </el-row>

      <el-table v-loading="loading" :data="jobLogList" @selection-change="handleSelectionChange">
         <el-table-column type="selection" width="55" align="center" />
         <el-table-column label="日志编号" width="80" align="center" prop="jobLogId" />
         <el-table-column label="任务名称" align="center" prop="jobName" :show-overflow-tooltip="true" />
         <el-table-column label="任务组名" align="center" prop="jobGroup" :show-overflow-tooltip="true">
            <template #default="scope">
               <dict-tag :options="sys_job_group" :value="scope.row.jobGroup" />
            </template>
         </el-table-column>
         <el-table-column label="调用目标字符串" align="center" prop="invokeTarget" :show-overflow-tooltip="true" />
         <el-table-column label="日志信息" align="center" prop="jobMessage" :show-overflow-tooltip="true" />
         <el-table-column label="执行状态" align="center" prop="status">
            <template #default="scope">
               <el-tag v-if="scope.row.status == '0'" type="success">正常</el-tag>
               <el-tag v-else-if="scope.row.status == '1'" type="danger">失败</el-tag>
               <el-tag v-else type="warning">执行中</el-tag>
            </template>
         </el-table-column>
         <el-table-column label="执行时间" align="center" prop="createTime" width="180">
            <template #default="scope">
               <span>{{ parseTime(scope.row.createTime) }}</span>
            </template>
         </el-table-column>
         <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
            <template #default="scope">
               <el-button link type="primary" icon="View" @click="handleView(scope.row)" v-hasPermi="['monitor:job:query']">详细</el-button>
               <el-button link type="primary" icon="List" @click="handleSteps(scope.row)" v-hasPermi="['monitor:job:query']">步骤</el-button>
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

      <!-- 调度日志详细 -->
      <job-detail v-model:visible="open" :row="form" type="log" />

      <!-- 调度步骤明细 -->
      <el-dialog title="调度步骤" v-model="stepsOpen" width="1000px" append-to-body>
         <el-table v-loading="stepsLoading" :data="stepList" border>
            <el-table-column label="序号" prop="stepNo" width="70" align="center" />
            <el-table-column label="步骤" prop="stepName" min-width="150" />
            <el-table-column label="状态" prop="status" width="110" align="center">
               <template #default="scope">
                  <el-tag v-if="scope.row.status === 'SUCCESS'" type="success">成功</el-tag>
                  <el-tag v-else-if="scope.row.status === 'FAILED'" type="danger">失败</el-tag>
                  <el-tag v-else-if="scope.row.status === 'NEEDS_AUTH'" type="warning">需要认证</el-tag>
                  <el-tag v-else-if="scope.row.status === 'SKIPPED'" type="info">跳过</el-tag>
                  <el-tag v-else type="warning">执行中</el-tag>
               </template>
            </el-table-column>
            <el-table-column label="消息" prop="message" min-width="240" show-overflow-tooltip />
            <el-table-column label="开始时间" prop="startTime" width="180">
               <template #default="scope">{{ parseTime(scope.row.startTime) }}</template>
            </el-table-column>
            <el-table-column label="结束时间" prop="endTime" width="180">
               <template #default="scope">{{ parseTime(scope.row.endTime) }}</template>
            </el-table-column>
            <el-table-column label="耗时（毫秒）" prop="durationMs" width="120" align="right" />
            <el-table-column label="异常信息" prop="errorInfo" min-width="240" show-overflow-tooltip />
         </el-table>
         <el-empty v-if="!stepsLoading && stepList.length === 0" description="暂无步骤明细" />
      </el-dialog>
   </div>
</template>

<script setup lang="ts" name="JobLog">
import JobDetail from './detail.vue'
import { getJob } from "@/api/monitor/job"
import { listJobLog, listJobLogDetails, delJobLog, cleanJobLog } from "@/api/monitor/jobLog"
import type { SysJobLog, SysJobLogDetail, JobLogQueryParams } from '@/types/api/monitor/jobLog'
import type { SysJob } from '@/types/api/monitor/job'

const { proxy } = getCurrentInstance()
const { sys_common_status, sys_job_group } = useDict("sys_common_status", "sys_job_group")

const jobLogList = ref<SysJobLog[]>([])
const open = ref<boolean>(false)
const stepsOpen = ref<boolean>(false)
const stepsLoading = ref<boolean>(false)
const stepList = ref<SysJobLogDetail[]>([])
const loading = ref<boolean>(true)
const showSearch = ref<boolean>(true)
const ids = ref<number[]>([])
const multiple = ref<boolean>(true)
const total = ref<number>(0)
const dateRange = ref<string[]>([])
const route = useRoute()

const data = reactive({
  form: {} as SysJobLog,
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    jobName: undefined,
    jobGroup: undefined,
    status: undefined
  } as JobLogQueryParams
})

const { queryParams, form } = toRefs(data)

/** 查询调度日志列表 */
function getList() {
  loading.value = true
  listJobLog(proxy.addDateRange(queryParams.value, dateRange.value)).then(response => {
    jobLogList.value = response.rows
    total.value = response.total
    loading.value = false
  })
}

// 返回按钮
function handleClose() {
  const obj = { path: "/monitor/job" }
  proxy.$tab.closeOpenPage(obj)
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

// 多选框选中数据
function handleSelectionChange(selection: SysJobLog[]) {
  ids.value = selection.map(item => item.jobLogId!)
  multiple.value = !selection.length
}

/** 详细按钮操作 */
function handleView(row: SysJobLog) {
  open.value = true
  form.value = row
}

/** 查看步骤明细 */
function handleSteps(row: SysJobLog) {
  if (!row.jobLogId) return
  stepsOpen.value = true
  stepsLoading.value = true
  stepList.value = []
  listJobLogDetails(row.jobLogId).then(response => {
    stepList.value = response.data || []
  }).finally(() => {
    stepsLoading.value = false
  })
}

/** 删除按钮操作 */
function handleDelete() {
  proxy.$modal.confirm('是否确认删除调度日志编号为"' + ids.value + '"的数据项?').then(function () {
    return delJobLog(ids.value)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

/** 清空按钮操作 */
function handleClean() {
  proxy.$modal.confirm("是否确认清空所有调度日志数据项?").then(function () {
    return cleanJobLog()
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("清空成功")
  }).catch(() => {})
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download("monitor/jobLog/export", {
    ...queryParams.value,
  }, `job_log_${new Date().getTime()}.xlsx`)
}

(() => {
  const jobId = route.params && Number(route.params.jobId)
  if (jobId !== undefined && jobId != 0) {
    getJob(jobId).then(response => {
      queryParams.value.jobName = response.data!.jobName
      queryParams.value.jobGroup = response.data!.jobGroup
      getList()
    })
  } else {
    getList()
  }
})()
</script>
