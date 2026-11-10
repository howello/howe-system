<template>
  <header class="mcp-hero">
    <div class="mcp-hero__main">
      <span class="mcp-hero__icon" aria-hidden="true"><slot name="icon" /></span>
      <div class="mcp-hero__text">
        <div class="mcp-hero__eyebrow">
          <span class="mcp-hero__badge">MCP</span>
          <span v-if="eyebrow">{{ eyebrow }}</span>
        </div>
        <h1 class="mcp-hero__title">{{ title }}</h1>
        <p v-if="description" class="mcp-hero__desc">{{ description }}</p>
      </div>
    </div>
    <div v-if="$slots.actions" class="mcp-hero__actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<script setup lang="ts" name="McpPageHeader">
/** MCP 管理页统一页头：图标、所属分组、标题、说明与右侧操作区。 */
defineProps<{
  /** 页面标题 */
  title: string
  /** 标题下方的用途说明 */
  description?: string
  /** 标题上方的分组说明 */
  eyebrow?: string
}>()
</script>
