<template>
  <el-dialog :model-value="modelValue" :title="title" width="min(720px, 94vw)" append-to-body @update:model-value="$emit('update:modelValue', $event)">
    <el-descriptions :column="1" border>
      <el-descriptions-item v-for="(value, label) in fields" :key="label" :label="label"><pre class="mcp-admin-value">{{ display(value) }}</pre></el-descriptions-item>
    </el-descriptions>
  </el-dialog>
</template>
<script setup lang="ts" name="McpAdminDetail">
defineProps<{ modelValue: boolean; title: string; fields: Record<string, unknown> }>()
defineEmits<{ 'update:modelValue': [value: boolean] }>()
/** 白名单详情采用文本插值，客户端名称和回调中的 HTML 不执行。 */
function display(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  return typeof value === 'object' ? JSON.stringify(value, null, 2) : String(value)
}
</script>
<style scoped>
.mcp-admin-value { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; font-family: inherit; }
</style>
