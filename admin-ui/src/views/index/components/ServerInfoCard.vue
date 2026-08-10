<template>
  <el-collapse v-model="activeNames" class="server-info-card">
    <el-collapse-item title="系统信息" name="sys">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="服务器名称">{{ text(server.sys?.computerName) }}</el-descriptions-item>
        <el-descriptions-item label="服务器IP">{{ text(server.sys?.computerIp) }}</el-descriptions-item>
        <el-descriptions-item label="操作系统">{{ text(server.sys?.osName) }}</el-descriptions-item>
        <el-descriptions-item label="系统架构">{{ text(server.sys?.osArch) }}</el-descriptions-item>
      </el-descriptions>
    </el-collapse-item>

    <el-collapse-item title="JVM 信息" name="jvm">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="JDK 名称">{{ text(server.jvm?.name) }}</el-descriptions-item>
        <el-descriptions-item label="JDK 版本">{{ text(server.jvm?.version) }}</el-descriptions-item>
        <el-descriptions-item label="启动时间">{{ text(server.jvm?.startTime) }}</el-descriptions-item>
        <el-descriptions-item label="运行时长">{{ text(server.jvm?.runTime) }}</el-descriptions-item>
        <el-descriptions-item label="安装路径" :span="2">{{ text(server.jvm?.home) }}</el-descriptions-item>
        <el-descriptions-item label="运行参数" :span="2">
          <span class="server-info-card__args">{{ text(server.jvm?.inputArgs) }}</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-collapse-item>

    <el-collapse-item title="内存信息" name="mem">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="总内存">{{ size(server.mem?.total) }}</el-descriptions-item>
        <el-descriptions-item label="已用内存">{{ size(server.mem?.used) }}</el-descriptions-item>
        <el-descriptions-item label="剩余内存">{{ size(server.mem?.free) }}</el-descriptions-item>
        <el-descriptions-item label="使用率">{{ percent(server.mem?.usage) }}</el-descriptions-item>
      </el-descriptions>
    </el-collapse-item>

    <el-collapse-item title="磁盘状态" name="disk">
      <el-table :data="diskRows" border size="small">
        <el-table-column prop="dirName" label="盘符路径" min-width="110" show-overflow-tooltip />
        <el-table-column prop="sysTypeName" label="文件系统" min-width="100" show-overflow-tooltip />
        <el-table-column prop="typeName" label="盘符类型" min-width="100" show-overflow-tooltip />
        <el-table-column prop="total" label="总大小" min-width="90" />
        <el-table-column prop="free" label="可用大小" min-width="90" />
        <el-table-column prop="used" label="已用大小" min-width="90" />
        <el-table-column prop="usageText" label="已用百分比" min-width="100" />
      </el-table>
    </el-collapse-item>
  </el-collapse>
</template>

<script setup lang="ts" name="ServerInfoCard">
import type { MonitorServer, ServerFile } from '@/types'

const props = defineProps<{ server: MonitorServer }>()

const activeNames = ref<string[]>(['sys'])

/** 磁盘表格行（使用率提前格式化，避免在模板里写无类型回调） */
interface DiskRow {
  dirName: string
  sysTypeName: string
  typeName: string
  total: string
  free: string
  used: string
  usageText: string
}

const diskRows = computed<DiskRow[]>(() =>
  (props.server.sysFiles ?? []).map((file: ServerFile) => ({
    dirName: text(file.dirName),
    sysTypeName: text(file.sysTypeName),
    typeName: text(file.typeName),
    total: text(file.total),
    free: text(file.free),
    used: text(file.used),
    usageText: percent(file.usage)
  }))
)

/** 空值统一占位 */
function text(value?: string | null): string {
  return value === undefined || value === null || value === '' ? '-' : value
}

/** 内存单位为 GB */
function size(value?: number | null): string {
  return typeof value === 'number' ? `${value} GB` : '-'
}

function percent(value?: number | null): string {
  return typeof value === 'number' ? `${value}%` : '-'
}
</script>

<style scoped lang="scss">
.server-info-card {
  :deep(.el-collapse-item__header) {
    font-weight: 600;
  }

  &__args {
    word-break: break-all;
  }
}
</style>
