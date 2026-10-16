<template>
  <HomeCard
    title="快捷入口"
    icon-text="快"
    icon-class="ci-aqua"
    subtitle="按权限过滤"
    link-text="菜单管理"
    link-to="/system/menu"
    link-icon-name="manage"
    link-title="进入菜单管理"
  >
    <div v-if="visibleEntries.length === 0" class="quick-empty">暂无可用的快捷操作</div>
    <div v-else class="quick-grid">
      <div v-for="entry in visibleEntries" :key="entry.label" class="quick-item" @click="go(entry.path)">
        <el-icon class="quick-icon"><component :is="entry.icon" /></el-icon>
        <span>{{ entry.label }}</span>
      </div>
    </div>
  </HomeCard>
</template>

<script setup lang="ts" name="QuickEntryCard">
// 快捷入口卡：入口由前端配置、按用户权限过滤；全部无权限时整卡由卡片目录决定不下发
import { useRouter } from 'vue-router'
import { EditPen, ForkSpoon, Share, UserFilled } from '@element-plus/icons-vue'
import HomeCard from '../HomeCard.vue'
import useUserStore from '@/store/modules/user'

/** 快捷入口定义 */
/** 图标组件类型：仓库 global.d.ts 屏蔽了 vue 官方 Component 导出，这里用宽松别名 */
type IconComponent = unknown

interface QuickEntry {
  label: string
  path: string
  icon: IconComponent
  permission: string
}

const ENTRIES: QuickEntry[] = [
  { label: '写文章', path: '/blog/article', icon: EditPen, permission: 'blog:article:add' },
  { label: '新增菜品', path: '/meal/dish', icon: ForkSpoon, permission: 'meal:dish:add' },
  { label: '创建分享', path: '/share/link', icon: Share, permission: 'share:link:add' },
  { label: '新增用户', path: '/system/user', icon: UserFilled, permission: 'system:user:add' }
]

const router = useRouter()
const userStore = useUserStore()

/** 与仓库既有 hasPermi 语义一致：*:*:* 通配或逐项包含 */
function hasPermi(permission: string): boolean {
  const permissions = userStore.permissions
  if (permissions.includes('*:*:*')) return true
  return permissions.includes(permission)
}

const visibleEntries = computed(() => ENTRIES.filter((e) => hasPermi(e.permission)))

function go(path: string): void {
  router.push(path)
}
</script>

<style scoped lang="scss">
.quick-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.quick-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--home-tile-bg);
  border: 1px solid transparent;
  font-size: 13px;
  color: var(--home-text-secondary);
  cursor: pointer;
  transition: all 0.16s;

  &:hover {
    border-color: var(--home-accent);
    color: var(--home-accent);
    background: var(--home-card-bg);
  }
}

.quick-icon {
  font-size: 15px;
}

.quick-empty {
  padding: 20px 0;
  text-align: center;
  font-size: 13px;
  color: var(--home-text-muted);
}

@media (max-width: 768px) {
  .quick-grid {
    grid-template-columns: 1fr;
  }
}
</style>
