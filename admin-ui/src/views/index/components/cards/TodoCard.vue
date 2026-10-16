<template>
  <HomeCard
    title="待办"
    icon-text="待"
    icon-class="ci-orange"
    subtitle="仅显示你有权处理的"
    link-text="我的订单"
    link-to="/meal/order"
    link-icon-name="order"
    link-title="进入订单列表"
  >
    <CardState :state="state" :message="errorMessage" @retry="reload">
      <div v-if="items.length === 0" class="todo-empty">暂无待办事项</div>
      <div v-else>
        <div
          v-for="item in items"
          :key="item.label"
          class="todo-row"
          :class="{ 'has-todo': item.count > 0 }"
          @click="go(item.path)"
        >
          <span class="todo-label">{{ item.label }}</span>
          <span class="todo-count" :class="{ hot: item.count > 0, zero: item.count === 0 }">{{ item.count }}</span>
        </div>
      </div>
    </CardState>
  </HomeCard>
</template>

<script setup lang="ts" name="TodoCard">
// 待办聚合卡：只展示当前用户有权限处理的事项，每项可跳转对应列表页
import { useRouter } from 'vue-router'
import HomeCard from '../HomeCard.vue'
import CardState from '../CardState.vue'
import { getHomeTodos } from '@/api/home/cards'
import type { AjaxResult, HomeTodoItem } from '@/types'
import { useCardData } from '../../use-card-data'

const router = useRouter()
const items = ref<HomeTodoItem[]>([])

async function load(): Promise<void> {
    const res: AjaxResult<HomeTodoItem[]> = await getHomeTodos()
    items.value = res.data ?? []
}

function go(path: string): void {
  if (path) router.push(path)
}

const { state, errorMessage, reload } = useCardData(load)

defineExpose({ reload })
</script>

<style scoped lang="scss">
.todo-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  border-radius: 8px;
  background: var(--home-tile-bg);
  border: 1px solid transparent;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.16s;

  & + & {
    margin-top: 8px;
  }

  &:hover {
    border-color: var(--home-accent);
    background: var(--home-card-bg);
  }

  // 有待处理项时左侧加主色强调条
  &.has-todo {
    border-left: 2px solid var(--home-accent-orange);
  }
}

.todo-label {
  color: var(--home-text-secondary);
}

.todo-count {
  margin-left: auto;
  font-size: 17px;
  font-weight: 650;
  letter-spacing: -0.01em;
  font-feature-settings: 'tnum' 0;
  color: var(--home-text-primary);

  &.hot {
    color: var(--home-accent-orange);
  }

  &.zero {
    color: var(--home-text-muted);
    font-weight: 500;
  }
}

.todo-empty {
  padding: 20px 0;
  text-align: center;
  font-size: 13px;
  color: var(--home-text-muted);
}
</style>
