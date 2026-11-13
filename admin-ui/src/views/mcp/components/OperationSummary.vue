<template>
  <section class="mcp-summary" aria-label="变更摘要">
    <div v-if="orderItems.length" class="mcp-summary__item">
      <div class="mcp-summary__label">下单菜品</div>
      <ul class="mcp-summary__value">
        <li v-for="(item, index) in orderItems" :key="`${item.dishId}-${index}`">
          {{ item.dishName }}（#{{ item.dishId }}）× {{ item.quantity }} 份
          <span v-if="item.remark">，备注：{{ item.remark }}</span>
        </li>
      </ul>
    </div>
    <div class="mcp-summary__item" v-for="item in rows" :key="item.key">
      <div class="mcp-summary__label">{{ item.label }}</div>
      <div class="mcp-summary__value">{{ item.value }}</div>
    </div>
  </section>
</template>

<script setup lang="ts" name="OperationSummary">
import { computed } from 'vue'

const props = defineProps<{ summary?: Record<string, unknown> | null }>()

/** 固定字段中文名；未知键不透传展示。 */
const labels: Record<string, string> = {
  title: '标题', slug: '文章标识', targetType: '目标类型', targetId: '目标', expectedVersion: '期望版本',
  contentChars: '正文字符数', status: '公开状态', isTop: '置顶', tagCount: '标签数量',
  fields: '修改字段', effect: '执行后果', count: '公告数量', noticeIds: '公告编号', linkId: '分享ID',
  // 点餐工具摘要白名单
  orderId: '订单', itemCount: '菜品项数', totalQuantity: '总份数', assigned: '是否指派厨师',
  orderRemark: '整单备注', assigneeName: '指派厨师', assigneeId: '厨师编号',
  currentStatus: '当前状态', candidateVersion: '候选版本', candidateRound: '候选轮次',
  score: '评分', anonymous: '匿名评价', proposalId: '提案', decision: '审核决定', dishId: '关联菜品',
  descriptionChars: '介绍字符数', notesChars: '备注字符数', aiNotice: 'AI 服务说明'
}

/** 只渲染白名单字段，文本化输出，不解释 HTML。 */
const rows = computed(() => Object.entries(props.summary ?? {})
  .filter(([key]) => labels[key])
  .map(([key, value]) => ({
    key,
    label: labels[key],
    value: Array.isArray(value) ? value.join('、') : value === null || value === undefined ? '—' : String(value)
  })))

/** 只渲染后端固定字段，Vue 模板会转义菜名与备注。 */
const orderItems = computed(() => {
  const items = props.summary?.items
  if (!Array.isArray(items)) return []
  return items.filter((item): item is { dishId: string; dishName: string; quantity: number; remark?: string } =>
    item !== null && typeof item === 'object' && typeof item.dishId === 'string' &&
    typeof item.dishName === 'string' && typeof item.quantity === 'number')
})
</script>
