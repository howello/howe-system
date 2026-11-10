<template>
  <div class="mcp-brand">
    <span class="mcp-brand__mark" aria-hidden="true"><el-icon><Connection /></el-icon></span>
    <div class="mcp-brand__text">
      <div class="mcp-brand__name">MCP 授权中心</div>
      <div class="mcp-brand__sub">{{ subtitle || '连接你的 AI 客户端与系统账号' }}</div>
    </div>
    <span v-if="badge" class="mcp-brand__badge">{{ badge }}</span>
  </div>
</template>

<script setup lang="ts" name="McpBrand">
import { Connection } from '@element-plus/icons-vue'

/** 独立授权页统一品牌头部，与后台管理页保持同一视觉语言。 */
withDefaults(defineProps<{
  /** 品牌行右侧的状态徽标 */
  badge?: string
  /** 品牌下方的说明 */
  subtitle?: string
}>(), { badge: '', subtitle: '' })
</script>
