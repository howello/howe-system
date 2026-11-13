<template>
  <el-popover
    v-model:visible="panelVisible"
    placement="bottom-start"
    :width="360"
    trigger="click"
    popper-class="meal-category-icon-popover"
  >
    <template #reference>
      <div class="icon-trigger" :class="{ 'is-disabled': disabled }">
        <span class="icon-trigger__value">{{ modelValue || DEFAULT_MEAL_CATEGORY_ICON }}</span>
        <span class="icon-trigger__text">{{ modelValue ? "点击更换图标" : "点击选择图标" }}</span>
        <el-icon v-if="modelValue && !disabled" class="icon-trigger__clear" @click.stop="clearIcon">
          <Close />
        </el-icon>
      </div>
    </template>

    <div class="icon-panel">
      <el-input v-model="keyword" placeholder="搜索图标分组" clearable size="small" class="icon-panel__search" />
      <div class="icon-panel__body">
        <div v-for="group in filteredGroups" :key="group.label" class="icon-group">
          <div class="icon-group__label">{{ group.label }}</div>
          <div class="icon-group__items">
            <button
              v-for="icon in group.icons"
              :key="icon"
              type="button"
              class="icon-item"
              :class="{ 'is-active': icon === modelValue }"
              :title="group.label"
              @click="selectIcon(icon)"
            >
              {{ icon }}
            </button>
          </div>
        </div>
        <el-empty v-if="!filteredGroups.length" description="没有匹配的图标" :image-size="60" />
      </div>
      <div class="icon-panel__footer">
        <span class="icon-panel__hint">只支持内置 emoji，不支持图片地址</span>
      </div>
    </div>
  </el-popover>
</template>

<script setup lang="ts" name="MealCategoryIconPicker">
import { DEFAULT_MEAL_CATEGORY_ICON, MEAL_CATEGORY_ICON_GROUPS } from "../icons";

const props = defineProps<{
  /** 当前图标（v-model） */
  modelValue?: string;
  /** 是否禁用 */
  disabled?: boolean;
}>();

const emit = defineEmits<{
  (event: "update:modelValue", value: string): void;
}>();

const panelVisible = ref(false);
const keyword = ref("");

/** 按分组名过滤（图标本身是 emoji，无法按文字搜索） */
const filteredGroups = computed(() => {
  const text = keyword.value.trim();
  if (!text) {
    return MEAL_CATEGORY_ICON_GROUPS;
  }
  return MEAL_CATEGORY_ICON_GROUPS.filter((group) => group.label.includes(text));
});

/** 选中图标并关闭面板 */
function selectIcon(icon: string) {
  if (props.disabled) {
    return;
  }
  emit("update:modelValue", icon);
  panelVisible.value = false;
}

/** 清空图标 */
function clearIcon() {
  if (props.disabled) {
    return;
  }
  emit("update:modelValue", "");
  panelVisible.value = false;
}
</script>

<style scoped>
.icon-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 32px;
  padding: 0 11px;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  cursor: pointer;
  transition: border-color 0.2s;
}

.icon-trigger:hover {
  border-color: var(--el-border-color-hover);
}

.icon-trigger.is-disabled {
  cursor: not-allowed;
  background: var(--el-disabled-bg-color);
  color: var(--el-disabled-text-color);
}

.icon-trigger__value {
  font-size: 18px;
  line-height: 1;
}

.icon-trigger__text {
  flex: 1;
  font-size: 13px;
  color: var(--el-text-color-placeholder);
}

.icon-trigger__clear {
  color: var(--el-text-color-placeholder);
}

.icon-trigger__clear:hover {
  color: var(--el-text-color-secondary);
}

.icon-panel__search {
  margin-bottom: 8px;
}

.icon-panel__body {
  max-height: 280px;
  overflow-y: auto;
}

.icon-group {
  margin-bottom: 10px;
}

.icon-group__label {
  margin-bottom: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.icon-group__items {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.icon-item {
  width: 34px;
  height: 34px;
  font-size: 20px;
  line-height: 1;
  background: var(--el-fill-color-lighter);
  border: 1px solid transparent;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s;
}

.icon-item:hover {
  background: var(--el-fill-color);
  border-color: var(--el-border-color);
}

.icon-item.is-active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.icon-panel__footer {
  padding-top: 6px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.icon-panel__hint {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}
</style>
