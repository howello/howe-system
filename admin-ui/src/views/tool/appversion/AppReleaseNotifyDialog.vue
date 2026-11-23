<template>
  <el-dialog
    v-model="open"
    :title="`发版通知 · ${appName}`"
    width="min(640px, 94vw)"
    :close-on-click-modal="false"
    :close-on-press-escape="!saving"
    :show-close="!saving"
    append-to-body
  >
    <el-table v-loading="loading" :data="configs" empty-text="尚未配置发版机器人">
      <el-table-column prop="label" label="群备注" min-width="100" show-overflow-tooltip />
      <el-table-column prop="maskedKey" label="Webhook Key" min-width="130" />
      <el-table-column label="通知" width="80">
        <template #default="{ row }">{{ row.enabled ? '启用' : '停用' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button link type="primary" :disabled="saving" @click="edit(row)">编辑</el-button>
          <el-button link type="danger" :disabled="saving" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-form class="mt16" :model="form" :disabled="saving" label-width="110px" @submit.prevent="save">
      <el-form-item label="群备注">
        <el-input v-model="form.label" maxlength="80" placeholder="选填，例如版本测试群" />
      </el-form-item>
      <el-form-item label="Webhook Key" :required="configId === undefined">
        <el-input v-model.trim="form.webhookKey" type="password" maxlength="128" autocomplete="new-password"
          :placeholder="configId === undefined ? '填写群机器人 Webhook 的 key 参数' : '留空时继续使用原 Key'" />
      </el-form-item>
      <el-form-item label="启用通知">
        <el-switch v-model="form.enabled" aria-label="启用通知" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" native-type="submit" :loading="saving" :disabled="loading">
          {{ configId === undefined ? '添加机器人' : '保存修改' }}
        </el-button>
        <el-button v-if="configId !== undefined" @click="resetForm">取消编辑</el-button>
      </el-form-item>
    </el-form>
  </el-dialog>
</template>

<script setup lang="ts">
import { deleteReleaseNotifyConfig, listReleaseNotifyConfigs, saveReleaseNotifyConfig } from '@/api/tool/appReleaseNotify'
import type { AppReleaseNotifyConfig, AppReleaseNotifyForm } from '@/api/tool/appReleaseNotify'
import type { AppReleaseKey } from '@/types/api/meal/appversion'
import { useAppProxy } from '@/utils/appProxy'

const props = defineProps<{ appKey: AppReleaseKey; appName: string }>()
const open = defineModel<boolean>({ required: true })
const proxy = useAppProxy()
const configs = ref<AppReleaseNotifyConfig[]>([])
const configId = ref<number>()
const form = reactive<AppReleaseNotifyForm>({ label: '', webhookKey: '', enabled: false })
const loading = ref(false)
const saving = ref(false)
let loadId = 0

watch(open, (visible) => {
  resetForm()
  if (visible) {
    configs.value = []
    void loadConfigs()
  }
})

async function loadConfigs() {
  const requestId = ++loadId
  loading.value = true
  try {
    const response = await listReleaseNotifyConfigs(props.appKey)
    if (requestId === loadId) configs.value = response.data ?? []
  } catch {
    // 请求层已提示失败。
  } finally {
    if (requestId === loadId) loading.value = false
  }
}

function resetForm() {
  configId.value = undefined
  Object.assign(form, { label: '', webhookKey: '', enabled: false })
}

function edit(row: AppReleaseNotifyConfig) {
  configId.value = row.configId
  Object.assign(form, { label: row.label, webhookKey: '', enabled: row.enabled })
}

async function save() {
  if (saving.value || loading.value) return
  if (configId.value === undefined && !form.webhookKey.trim()) {
    proxy.$modal.msgWarning('请填写 Webhook Key')
    return
  }
  saving.value = true
  try {
    await saveReleaseNotifyConfig(props.appKey, { ...form }, configId.value)
    proxy.$modal.msgSuccess('保存成功')
    resetForm()
    await loadConfigs()
  } catch {
    // 请求层已提示失败。
  } finally {
    saving.value = false
  }
}

async function remove(row: AppReleaseNotifyConfig) {
  try {
    await proxy.$modal.confirm(`确认删除发版机器人「${row.label || row.maskedKey}」吗？`)
  } catch {
    return
  }
  saving.value = true
  try {
    await deleteReleaseNotifyConfig(props.appKey, row.configId)
    if (configId.value === row.configId) resetForm()
    proxy.$modal.msgSuccess('删除成功')
    await loadConfigs()
  } catch {
    // 请求层已提示失败。
  } finally {
    saving.value = false
  }
}
</script>
