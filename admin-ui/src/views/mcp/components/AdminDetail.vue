<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="min(780px, 94vw)"
    append-to-body
    class="mcp-detail-dialog"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div class="mcp-detail-list">
      <div
        v-for="(value, label) in fields"
        :key="label"
        class="mcp-detail-row"
        :class="{ 'mcp-detail-row--block': isBlock(value) }"
      >
        <span class="mcp-detail-label">{{ label }}</span>
        <div class="mcp-detail-value">
          <pre v-if="isBlock(value)" class="mcp-code">{{ display(value) }}</pre>
          <span v-else>{{ display(value) }}</span>
        </div>
      </div>
      <p v-if="!Object.keys(fields).length" class="mcp-empty">暂无可展示的字段</p>
    </div>
    <template #footer>
      <div class="mcp-detail-footer">
        <el-button @click="$emit('update:modelValue', false)">关 闭</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts" name="McpAdminDetail">
import './../admin.css'

/** 白名单详情字段：对象和长文本使用代码块展示，其余保持普通文本。 */
defineProps<{ modelValue: boolean; title: string; fields: Record<string, unknown> }>()
defineEmits<{ 'update:modelValue': [value: boolean] }>()

/** 对象、数组或含换行的长文本改用代码块，避免把描述列表撑破。 */
function isBlock(value: unknown): boolean {
  return (typeof value === 'object' && value !== null) || (typeof value === 'string' && value.length > 72)
}

/** 白名单详情采用文本插值，客户端名称和回调中的 HTML 不执行。 */
function display(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  return typeof value === 'object' ? JSON.stringify(value, null, 2) : String(value)
}
</script>
