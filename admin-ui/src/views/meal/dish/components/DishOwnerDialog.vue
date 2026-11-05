<template>
  <el-dialog title="调整归属" v-model="visible" width="480px" append-to-body :close-on-click-modal="false">
    <div class="owner-current">
      当前归属：<el-tag :type="expectedDeptId === 0 ? 'warning' : 'success'" size="small">{{ currentLabel }}</el-tag>
    </div>
    <el-form label-width="90px">
      <el-form-item label="调整后">
        <el-radio-group v-model="targetType">
          <el-radio value="public">平台公共菜谱</el-radio>
          <el-radio value="family">指定家庭</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="targetType === 'family'" label="目标家庭">
        <el-select
          v-model="targetFamilyId"
          placeholder="搜索并选择家庭"
          filterable
          remote
          :remote-method="loadFamilies"
          :loading="familyLoading"
          style="width: 100%"
        >
          <el-option
            v-for="item in families"
            :key="item.deptId"
            :label="familyLabel(item)"
            :value="item.deptId as number"
          />
        </el-select>
      </el-form-item>
    </el-form>
    <el-alert
      type="warning"
      :closable="false"
      show-icon
      title="调整后将改变菜谱可见范围：公共菜谱对所有家庭可见，家庭私菜只有该家庭可见"
    />
    <template #footer>
      <div class="dialog-footer">
        <el-button type="primary" :loading="submitting" :disabled="!canSubmit" @click="submit">确 定</el-button>
        <el-button :disabled="submitting" @click="visible = false">取 消</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts" name="DishOwnerDialog">
import type { DishOwnerOption } from '@/types/api/meal/dish'
import { listDishOwnerOptions, changeDishOwner } from '@/api/meal/dish'

/** 弹窗对外暴露：dishId + 当前归属 */
const props = defineProps<{
  dishId?: number
  expectedDeptId?: number
}>()

const emit = defineEmits<{
  (e: 'changed'): void
}>()

const visible = ref<boolean>(false)
const targetType = ref<'public' | 'family'>('public')
const targetFamilyId = ref<number | undefined>(undefined)
const families = ref<DishOwnerOption[]>([])
const familyLoading = ref<boolean>(false)
const submitting = ref<boolean>(false)

const currentLabel = computed(() => (props.expectedDeptId === 0 ? '公共菜谱' : `家庭 #${props.expectedDeptId}`))

const canSubmit = computed(() => targetType.value === 'public' || targetFamilyId.value != null)

/** 打开弹窗：展示当前归属，供并发校验用的旧值来自调用方 */
function open() {
  targetType.value = props.expectedDeptId === 0 ? 'family' : 'public'
  targetFamilyId.value = undefined
  visible.value = true
  loadFamilies('')
}

defineExpose({ open })

/** 远程搜索家庭 */
function loadFamilies(keyword: string) {
  familyLoading.value = true
  listDishOwnerOptions(keyword).then(response => {
    families.value = (response.data || []) as DishOwnerOption[]
    familyLoading.value = false
  }).catch(() => {
    familyLoading.value = false
  })
}

/** 家庭标签：同名家庭靠上级名称区分 */
function familyLabel(item: DishOwnerOption): string {
  return item.parentName ? `${item.name}（${item.parentName}）` : item.name
}

/** 提交迁移 */
function submit() {
  if (!props.dishId || props.expectedDeptId == null) {
    return
  }
  const targetDeptId = targetType.value === 'public' ? 0 : targetFamilyId.value
  if (targetDeptId == null) {
    return
  }
  submitting.value = true
  changeDishOwner(props.dishId, {
    targetDeptId,
    expectedDeptId: props.expectedDeptId
  }).then(() => {
    submitting.value = false
    visible.value = false
    emit('changed')
  }).catch(() => {
    // 并发冲突或目标非法：保持弹窗打开，用户可刷新后重试
    submitting.value = false
  })
}
</script>

<style scoped>
.owner-current {
  margin-bottom: 16px;
  font-size: 14px;
  color: var(--el-text-color-regular);
}
</style>
