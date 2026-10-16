<template>
  <HomeCard
    title="服务器资源"
    icon-text="服"
    layer-tag="角色层"
    link-text="详情"
    link-icon-name="monitor"
    link-title="查看系统信息 / JVM / 内存 / 磁盘"
    @link="dialogVisible = true"
  >
    <CardState :state="state" :message="errorMessage" @retry="reload">
      <div v-if="!server" class="srv-empty">暂无服务器数据</div>
      <template v-else>
        <!-- 关键指标：CPU / 内存 / 磁盘 / JVM 使用率（跨两行的高由卡片拉伸适配） -->
        <StatTiles :items="tiles" accent-color="var(--home-accent)" />

        <!-- 资源雷达：CPU / 内存 / 磁盘 / JVM -->
        <div ref="chartEl" class="srv-radar" />

        <!-- 系统与 JVM 摘要：两列紧凑排布，长值省略号（完整值见详情弹窗） -->
        <div class="srv-info-grid">
          <div class="srv-info">
            <span class="srv-info__k">主机名</span>
            <span class="srv-info__v" :title="text(server.sys?.computerName)">{{ text(server.sys?.computerName) }}</span>
          </div>
          <div class="srv-info">
            <span class="srv-info__k">操作系统</span>
            <span class="srv-info__v" :title="text(server.sys?.osName)">{{ text(server.sys?.osName) }}</span>
          </div>
          <div class="srv-info">
            <span class="srv-info__k">CPU 型号</span>
            <span class="srv-info__v" :title="text(server.cpu?.name)">{{ text(server.cpu?.name) }}</span>
          </div>
          <div class="srv-info">
            <span class="srv-info__k">CPU 核心</span>
            <span class="srv-info__v">{{ server.cpu?.cpuNum ?? '—' }} 核</span>
          </div>
          <div class="srv-info">
            <span class="srv-info__k">物理内存</span>
            <span class="srv-info__v">{{ server.mem?.used ?? 0 }} / {{ server.mem?.total ?? 0 }} GB</span>
          </div>
          <div class="srv-info">
            <span class="srv-info__k">JVM 堆内存</span>
            <span class="srv-info__v">{{ server.jvm?.used ?? 0 }} / {{ server.jvm?.total ?? 0 }} MB</span>
          </div>
          <div class="srv-info">
            <span class="srv-info__k">JDK 版本</span>
            <span class="srv-info__v" :title="text(server.jvm?.version)">{{ text(server.jvm?.version) }}</span>
          </div>
          <div class="srv-info">
            <span class="srv-info__k">运行时长</span>
            <span class="srv-info__v" :title="text(server.jvm?.runTime)">{{ text(server.jvm?.runTime) }}</span>
          </div>
        </div>

        <!-- 磁盘状态：各挂载点使用率 -->
        <div class="srv-disks">
          <div v-for="(file, i) in server.sysFiles ?? []" :key="i" class="srv-disk">
            <span class="srv-disk__k" :title="file.dirName">{{ file.dirName || '—' }}</span>
            <span class="meter">
              <i :class="levelClass(file.usage)" :style="{ width: pct(file.usage) }" />
            </span>
            <span class="srv-disk__v">{{ file.usage }}%</span>
          </div>
        </div>
      </template>
    </CardState>

    <!-- 服务器详情弹窗：系统 / 内存 / JVM / 磁盘（置于 CardState 之外，避免加载态影响弹窗） -->
    <el-dialog v-model="dialogVisible" title="服务器详情" width="760px" append-to-body>
      <template v-if="server">
        <section class="dlg-section">
          <h3>系统信息</h3>
          <div class="kv-list">
            <div class="kv"><span class="kv-k">服务器名称</span><span class="kv-v">{{ text(server.sys?.computerName) }}</span></div>
            <div class="kv"><span class="kv-k">服务器 IP</span><span class="kv-v">{{ text(server.sys?.computerIp) }}</span></div>
            <div class="kv"><span class="kv-k">操作系统</span><span class="kv-v">{{ text(server.sys?.osName) }}</span></div>
            <div class="kv"><span class="kv-k">系统架构</span><span class="kv-v">{{ text(server.sys?.osArch) }}</span></div>
            <div class="kv"><span class="kv-k">CPU 型号</span><span class="kv-v">{{ text(server.cpu?.name) }}</span></div>
            <div class="kv"><span class="kv-k">CPU 核心数</span><span class="kv-v">{{ server.cpu?.cpuNum ?? '—' }} 核</span></div>
            <div class="kv"><span class="kv-k">系统负载</span><span class="kv-v">{{ loadAverageText }}</span></div>
            <div class="kv"><span class="kv-k">运行时长</span><span class="kv-v">{{ text(server.jvm?.runTime) }}</span></div>
            <div class="kv"><span class="kv-k">项目路径</span><span class="kv-v">{{ text(server.sys?.userDir) }}</span></div>
          </div>
        </section>

        <section class="dlg-section">
          <h3>内存信息</h3>
          <div class="meter-row">
            <span class="meter-k">物理内存</span>
            <span class="meter"><i :class="levelClass(server.mem?.usage)" :style="{ width: pct(server.mem?.usage) }" /></span>
            <span class="meter-v">{{ server.mem?.used ?? 0 }} / {{ server.mem?.total ?? 0 }} GB（{{ server.mem?.usage ?? 0 }}%）</span>
          </div>
          <div class="meter-row">
            <span class="meter-k">JVM 堆内存</span>
            <span class="meter"><i :class="levelClass(server.jvm?.usage)" :style="{ width: pct(server.jvm?.usage) }" /></span>
            <span class="meter-v">{{ server.jvm?.used ?? 0 }} / {{ server.jvm?.total ?? 0 }} MB（{{ round(server.jvm?.usage) }}%）</span>
          </div>
          <div class="meter-row">
            <span class="meter-k">JVM 非堆内存</span>
            <span class="meter"><i :class="levelClass(server.jvm?.nonHeapUsage)" :style="{ width: pct(server.jvm?.nonHeapUsage) }" /></span>
            <span class="meter-v">{{ server.jvm?.nonHeapUsed ?? 0 }} / {{ server.jvm?.nonHeapTotal ?? 0 }} MB（{{ round(server.jvm?.nonHeapUsage) }}%）</span>
          </div>
          <div class="meter-row">
            <span class="meter-k">CPU 使用率</span>
            <span class="meter"><i :class="levelClass(server.cpu?.used)" :style="{ width: pct(server.cpu?.used) }" /></span>
            <span class="meter-v">{{ round(server.cpu?.used) }}%</span>
          </div>
        </section>

        <section class="dlg-section">
          <h3>JVM 信息</h3>
          <div class="kv-list">
            <div class="kv"><span class="kv-k">JDK 名称</span><span class="kv-v">{{ text(server.jvm?.name) }}</span></div>
            <div class="kv"><span class="kv-k">JDK 版本</span><span class="kv-v">{{ text(server.jvm?.version) }}</span></div>
            <div class="kv"><span class="kv-k">启动时间</span><span class="kv-v">{{ text(server.jvm?.startTime) }}</span></div>
            <div class="kv"><span class="kv-k">运行时长</span><span class="kv-v">{{ text(server.jvm?.runTime) }}</span></div>
            <div class="kv"><span class="kv-k">GC 次数</span><span class="kv-v">{{ server.jvm?.gcCount ?? '—' }} 次</span></div>
            <div class="kv"><span class="kv-k">GC 耗时</span><span class="kv-v">{{ server.jvm?.gcTime ?? '—' }} ms</span></div>
            <div class="kv"><span class="kv-k">安装路径</span><span class="kv-v">{{ text(server.jvm?.home) }}</span></div>
          </div>
        </section>

        <section class="dlg-section">
          <h3>磁盘状态</h3>
          <div v-for="(file, i) in server.sysFiles ?? []" :key="i" class="meter-row">
            <span class="meter-k">{{ file.dirName || '—' }}</span>
            <span class="meter"><i :class="levelClass(file.usage)" :style="{ width: pct(file.usage) }" /></span>
            <span class="meter-v">{{ file.used }} / {{ file.total }}（{{ file.usage }}%）</span>
          </div>
        </section>
      </template>
      <div v-else class="srv-empty">暂无服务器数据</div>
    </el-dialog>
  </HomeCard>
</template>

<script setup lang="ts" name="ServerResourceCard">
// 服务器资源卡：关键指标 + 雷达图 + 系统/JVM 摘要 + 磁盘状态；详情弹窗展示完整信息
// 卡片左侧跨两行，内容按高度自适应填充（与右侧两卡总高对齐）
import type { EChartsOption } from 'echarts'
import HomeCard from '../HomeCard.vue'
import CardState from '../CardState.vue'
import StatTiles from '../StatTiles.vue'
import type { StatItem } from '../StatTiles.vue'
import { getServer } from '@/api/monitor/server'
import { useEchart } from '@/utils/echarts'
import { CHART_COLORS, animationConfig, prefersReducedMotion } from '../../chart-config'
import type { AjaxResult, MonitorServer } from '@/types'
import { useCardData } from '../../use-card-data'
import { useChartTheme } from '../../use-chart-theme'

const reduceMotion = prefersReducedMotion()
const { themeVersion, refreshTheme } = useChartTheme()
const server = ref<MonitorServer | null>(null)
const dialogVisible = ref(false)
const chartEl = ref<HTMLElement | null>(null)

/** 关键指标：四项使用率（数字块，带滚动动画） */
const tiles = computed<StatItem[]>(() => [
  { label: 'CPU 使用率', value: round(server.value?.cpu?.used), suffix: '%' },
  { label: '内存使用率', value: round(server.value?.mem?.usage), suffix: '%' },
  { label: '磁盘使用率', value: round(server.value?.sysFiles?.[0]?.usage), suffix: '%' },
  { label: 'JVM 使用率', value: round(server.value?.jvm?.usage), suffix: '%' }
])

function buildOption(data: MonitorServer | null): EChartsOption {
  const cpu = data?.cpu?.used ?? 0
  const mem = data?.mem?.usage ?? 0
  const disk = data?.sysFiles?.[0]?.usage ?? 0
  const jvm = data?.jvm?.usage ?? 0
  return {
    ...animationConfig(reduceMotion),
    tooltip: {
      trigger: 'item',
      formatter: (params: unknown) => {
        const p = params as { value?: number[] }
        const labels = ['CPU', '内存', '磁盘', 'JVM']
        const values = p.value ?? []
        return labels.map((label, i) => `${label}：${values[i] ?? '-'}%`).join('<br/>')
      }
    },
    radar: {
      radius: '62%',
      indicator: [
        { name: 'CPU', max: 100 },
        { name: '内存', max: 100 },
        { name: '磁盘', max: 100 },
        { name: 'JVM', max: 100 }
      ],
      axisName: { color: CHART_COLORS.axisText, fontSize: 11 },
      splitLine: { lineStyle: { color: CHART_COLORS.grid } },
      splitArea: { show: false },
      axisLine: { lineStyle: { color: CHART_COLORS.grid } }
    },
    series: [
      {
        type: 'radar',
        data: [{ value: [cpu, mem, disk, jvm], name: '使用率 %' }],
        areaStyle: { opacity: 0.16, color: CHART_COLORS.blue },
        lineStyle: { width: 2, color: CHART_COLORS.blue },
        itemStyle: { color: CHART_COLORS.blue }
      }
    ]
  }
}

const { render } = useEchart(() => chartEl.value, buildOption(null))
const { state, errorMessage, reload } = useCardData(load)

/** 数据到达后容器才渲染出来（v-else 分支），等 DOM 更新完再绘制 */
watch(server, (val: MonitorServer | null) => {
  nextTick(() => render(buildOption(val)))
})

// 主题切换时用新配色重绘雷达图
watch(themeVersion, () => nextTick(() => render(buildOption(server.value))))

// 加载态结束后兜底重绘一次（容器可能在首次 nextTick 之后才就绪）
watch(state, (val: string) => {
  if (val === 'ready') {
    nextTick(() => {
      // 再等一帧，确保 v-else 的图表容器已经挂载
      requestAnimationFrame(() => render(buildOption(server.value)))
    })
  }
})

async function load(): Promise<void> {
  const res: AjaxResult<MonitorServer> = await getServer()
  server.value = (res.data as MonitorServer) ?? null
}

/** 空值统一占位 */
function text(value?: string | null): string {
  return value === undefined || value === null || value === '' ? '—' : value
}

/** 使用率取整（保留一位小数以内） */
function round(value?: number | null): number {
  return typeof value === 'number' ? Math.round(value * 10) / 10 : 0
}

/** 系统负载：1/5/15 分钟平均，缺失时占位 */
const loadAverageText = computed(() => {
  const load = server.value?.cpu?.loadAverage
  if (!load || load.length === 0) return '—'
  return load.map((v: number) => (typeof v === 'number' ? Math.round(v * 100) / 100 : '—')).join(' / ')
})

/** 进度条宽度（夹在 0-100%） */
function pct(value?: number | null): string {
  const v = typeof value === 'number' ? Math.max(0, Math.min(100, value)) : 0
  return `${v}%`
}

/** 进度条分级：正常 / 偏高 / 危险 */
function levelClass(value?: number | null): string {
  const v = typeof value === 'number' ? value : 0
  if (v >= 90) return 'is-danger'
  if (v >= 70) return 'is-warn'
  return ''
}

defineExpose({ reload, refreshTheme })
</script>

<style scoped lang="scss">
// 卡片内容按高度自适应：雷达图区域可伸缩，摘要与磁盘贴底
.srv-radar {
  width: 100%;
  height: 200px;
  flex: 1 1 auto;
  min-height: 180px;
}

.srv-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 240px;
  font-size: 13px;
  color: var(--home-text-muted);
}

/* ---------- 系统 / JVM 摘要：两列紧凑排布 ---------- */
.srv-info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px 10px;
}

.srv-info {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  padding: 6px 10px;
  border-radius: 8px;
  background: var(--home-tile-bg);
  border: 1px solid transparent;
  font-size: 12.5px;

  &__k {
    flex: none;
    color: var(--home-text-muted);
  }

  &__v {
    margin-left: auto;
    min-width: 0;
    color: var(--home-text-primary);
    font-weight: 500;
    text-align: right;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

/* ---------- 磁盘状态：各挂载点使用率 ---------- */
.srv-disks {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.srv-disk {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12.5px;

  &__k {
    flex: none;
    width: 92px;
    color: var(--home-text-muted);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__v {
    flex: none;
    width: 44px;
    text-align: right;
    color: var(--home-text-primary);
    font-variant-numeric: tabular-nums;
  }
}

/* ---------- 弹窗内的分区与进度条 ---------- */
.dlg-section {
  & + & {
    margin-top: 18px;
  }

  h3 {
    display: flex;
    align-items: center;
    gap: 7px;
    margin: 0 0 10px;
    font-size: 13px;
    font-weight: 600;
    color: var(--home-text-secondary);

    &::before {
      content: '';
      width: 3px;
      height: 13px;
      border-radius: 2px;
      background: var(--home-accent);
    }
  }
}

.kv-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
}

.kv {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: 8px;
  background: var(--home-tile-bg);
  border: 1px solid transparent;
  transition: all 0.16s;

  &:hover {
    border-color: var(--home-line);
    background: var(--home-card-bg);
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  }
}

.kv-k {
  color: var(--home-text-secondary);
  flex: none;
}

.kv-v {
  margin-left: auto;
  color: var(--home-text-primary);
  font-weight: 500;
  font-variant-numeric: tabular-nums;
  text-align: right;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meter-row {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;

  & + & {
    margin-top: 9px;
  }
}

.meter-k {
  flex: none;
  width: 108px;
  color: var(--home-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meter {
  flex: 1;
  height: 8px;
  border-radius: 4px;
  background: var(--home-tile-bg);
  overflow: hidden;

  i {
    display: block;
    height: 100%;
    border-radius: 4px;
    background: var(--home-accent);
    transition: width 0.4s;

    &.is-warn {
      background: var(--home-accent-yellow);
    }

    &.is-danger {
      background: var(--home-bad-text);
    }
  }
}

.meter-v {
  flex: none;
  width: 190px;
  text-align: right;
  color: var(--home-text-primary);
  font-variant-numeric: tabular-nums;
}

@media (max-width: 768px) {
  .srv-info-grid {
    grid-template-columns: 1fr;
  }

  .meter-k {
    width: 78px;
  }

  .meter-v {
    width: 120px;
    font-size: 12px;
  }
}
</style>
