<template>
  <section class="home-card">
    <header class="home-card__head">
      <span class="home-card__icon" :class="iconClass">{{ iconText }}</span>
      <span class="home-card__title">{{ title }}</span>
      <span v-if="layerTag" class="home-card__layer">{{ layerTag }}</span>
      <span v-if="subtitle && !linkText" class="home-card__sub">{{ subtitle }}</span>
      <router-link v-if="linkText && linkTo" class="home-card__link" :to="linkTo" :title="linkTitle || linkText">
        <el-icon><component :is="linkIcon" /></el-icon>
        {{ linkText }}
      </router-link>
      <button v-else-if="linkText" class="home-card__link" type="button" :title="linkTitle || linkText" @click="emit('link')">
        <el-icon><component :is="linkIcon" /></el-icon>
        {{ linkText }}
      </button>
    </header>
    <div class="home-card__body">
      <slot />
    </div>
  </section>
</template>

<script setup lang="ts" name="HomeCard">
// 首页卡片统一外框：图标 + 标题 +（层级徽标）+（右上角跳转入口）
// 入口固定贴右上角：卡片头最后一个元素吃剩余空间，标题与徽标保持左对齐
import { Link, Setting, Monitor, Document, ShoppingCart, ForkSpoon, Download, List, Picture, Share, Connection } from '@element-plus/icons-vue'

const props = withDefaults(
  defineProps<{
    /** 卡片标题 */
    title: string
    /** 图标文字（单字，如「博」） */
    iconText: string
    /** 图标主色类名（ci-orange / ci-aqua / ci-yellow / ci-ink，默认主色蓝） */
    iconClass?: string
    /** 层级徽标文案（如「角色层」「模块层 · blog」） */
    layerTag?: string
    /** 无跳转入口时展示在右侧的说明文字 */
    subtitle?: string
    /** 跳转入口文案；不传则不渲染入口 */
    linkText?: string
    /** 跳转目标路由；不传则渲染为按钮（配合 link 事件） */
    linkTo?: string
    /** 入口 title 提示 */
    linkTitle?: string
    /** 入口图标名（见下方 ICONS 映射） */
    linkIconName?: string
  }>(),
  { iconClass: '', layerTag: '', subtitle: '', linkText: '', linkTo: '', linkTitle: '', linkIconName: 'link' }
)

const emit = defineEmits<{ (e: 'link'): void }>()

/** 入口图标映射：按语义选用，避免各卡片各写一套 SVG */
/** 图标组件类型：仓库 global.d.ts 屏蔽了 vue 官方 Component 导出，这里用宽松别名 */
type IconComponent = unknown

const ICONS: Record<string, IconComponent> = {
  link: Link,
  manage: Setting,
  monitor: Monitor,
  article: Document,
  order: ShoppingCart,
  dish: ForkSpoon,
  version: Download,
  calllog: List,
  image: Picture,
  channel: Connection,
  share: Share
}

const linkIcon = computed<IconComponent>(() => ICONS[props.linkIconName] ?? Link)
</script>

<style scoped lang="scss">
.home-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px 18px;
  background: var(--home-card-bg);
  border: 1px solid var(--home-line);
  border-radius: 10px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06), 0 8px 24px rgba(0, 0, 0, 0.08);
  box-sizing: border-box;
  min-width: 0;

  &__head {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  // 入口（或右侧说明）稳定贴右上角
  &__head > :nth-child(n + 3):last-child {
    margin-left: auto;
  }

  &__icon {
    flex: none;
    width: 22px;
    height: 22px;
    border-radius: 6px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    font-size: 12px;
    color: var(--home-card-bg);
    background: var(--home-accent);
  }

  &__title {
    font-size: 14px;
    font-weight: 600;
    color: var(--home-text-primary);
  }

  &__layer {
    flex: none;
    font-size: 11px;
    padding: 1px 7px;
    border-radius: 999px;
    border: 1px solid var(--home-line);
    color: var(--home-text-muted);
    white-space: nowrap;
    margin-left: 2px;
  }

  &__sub {
    font-size: 12px;
    color: var(--home-text-muted);
    white-space: nowrap;
  }

  &__link {
    flex: none;
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: 12px;
    color: var(--home-text-secondary);
    padding: 4px 10px;
    border-radius: 7px;
    border: 1px solid var(--home-line);
    background: var(--home-tile-bg);
    text-decoration: none;
    cursor: pointer;
    transition: all 0.16s;
    font-family: inherit;
    line-height: 1.3;

    &:hover {
      color: var(--home-accent);
      border-color: var(--home-accent);
      background: var(--home-card-bg);
    }
  }

  &__body {
    display: flex;
    flex-direction: column;
    gap: 12px;
    min-width: 0;
    // 行内等高时，多余空间留在卡片底部（内容靠上，不拉伸子元素）
    flex: 1;
  }
}

.ci-orange {
  background: var(--home-accent-orange);
}
.ci-aqua {
  background: var(--home-accent-aqua);
}
.ci-yellow {
  background: var(--home-accent-yellow);
}
.ci-ink {
  background: var(--home-text-muted);
}
</style>
