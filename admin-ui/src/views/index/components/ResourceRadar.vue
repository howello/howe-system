<template>
  <div ref="chartRef" class="resource-radar" />
</template>

<script setup lang="ts" name="ResourceRadar">
import { useEchart } from '@/utils/echarts'
import type { EChartsOption } from 'echarts'
import type { MonitorServer } from '@/types'

const props = defineProps<{ server: MonitorServer }>()

const chartRef = ref<HTMLDivElement | null>(null)

function usageOf(): number[] {
  const cpu = props.server.cpu
  // 后端 Cpu.getUsed() 已返回使用率百分比（used/total*100），直接取用，不要再除以 total；
  // 与 views/monitor/server/index.vue 的 {{ server.cpu.used }}% 取值一致
  const cpuUsage = cpu ? (cpu.used ?? 0) : 0
  const mem = props.server.mem
  const disk = props.server.sysFiles && props.server.sysFiles[0]
  return [
    Math.round(cpuUsage * 100) / 100,
    mem ? mem.usage : 0,
    disk ? disk.usage : 0,
    props.server.jvm ? props.server.jvm.usage : 0
  ]
}

function buildOption(): EChartsOption {
  const [cpu, mem, disk, jvm] = usageOf()
  return {
    tooltip: {
      trigger: 'item',
      formatter: (params: unknown) => {
        const p = params as { value?: number[] }
        const labels = ['CPU', '内存', '磁盘', 'JVM']
        const values = p.value ?? []
        return labels.map((label, i) => `${label}：${values[i] ?? '-'}%`).join('<br/>')
      }
    },
    legend: {
      bottom: 0,
      data: ['使用率 %']
    },
    radar: {
      radius: '66%',
      indicator: [
        { name: 'CPU', max: 100 },
        { name: '内存', max: 100 },
        { name: '磁盘', max: 100 },
        { name: 'JVM', max: 100 }
      ]
    },
    series: [
      {
        type: 'radar',
        data: [{ value: [cpu, mem, disk, jvm], name: '使用率 %' }],
        areaStyle: { opacity: 0.2 },
        lineStyle: { width: 2 }
      }
    ]
  }
}

const { render } = useEchart(() => chartRef.value, buildOption())

watch(
  () => props.server,
  () => render(buildOption()),
  { deep: true }
)
</script>

<style scoped lang="scss">
.resource-radar {
  width: 100%;
  height: 320px;
}
</style>
