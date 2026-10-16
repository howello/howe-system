<template>
  <HomeCard
    title="个人足迹"
    icon-text="我"
    icon-class="ci-ink"
    subtitle="只统计你自己的数据"
    link-text="个人中心"
    link-to="/user/profile"
    link-icon-name="link"
    link-title="进入个人中心"
  >
    <CardState :state="state" :message="errorMessage" @retry="reload">
      <div v-if="tiles.length === 0" class="fp-empty">暂无可用统计</div>
      <!-- 复用 StatTiles：与模块卡同一套数字卡语言与滚动动画；
           每项带 path，点击数字块直接跳转（不再渲染额外按钮行） -->
      <StatTiles v-else :items="tiles" accent-color="var(--home-accent)" />
    </CardState>
  </HomeCard>
</template>

<script setup lang="ts" name="FootprintCard">
// 个人足迹卡：只统计当前用户自己的数据；模块不可用或无权限的项不展示
// 跳转方式：点击数字块直接进入对应列表页
import HomeCard from '../HomeCard.vue'
import CardState from '../CardState.vue'
import StatTiles from '../StatTiles.vue'
import type { StatItem } from '../StatTiles.vue'
import { getHomeFootprint } from '@/api/home/cards'
import type { AjaxResult, HomeFootprint } from '@/types'
import { useCardData } from '../../use-card-data'

const data = ref<HomeFootprint | null>(null)

/** value 为 null 表示模块不可用或无权限，整项不展示 */
const tiles = computed<StatItem[]>(() => {
  const d = data.value
  if (!d) return []
  const all: StatItem[] = [
    { label: '我发布的文章', path: '/blog/article', value: d.articles },
    { label: '我下过的订单', path: '/meal/order', value: d.orders },
    { label: '我提交的提案', path: '/meal/proposal', value: d.proposals },
    { label: '我创建的分享', path: '/share/link', value: d.links }
  ]
  return all.filter((t) => t.value !== null)
})

async function load(): Promise<void> {
  const res: AjaxResult<HomeFootprint> = await getHomeFootprint()
  data.value = res.data ?? null
}

const { state, errorMessage, reload } = useCardData(load)

defineExpose({ reload })
</script>

<style scoped lang="scss">
.fp-empty {
  padding: 20px 0;
  text-align: center;
  font-size: 13px;
  color: var(--home-text-muted);
}
</style>
