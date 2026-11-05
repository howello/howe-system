<template>
  <div class="cover-editor">
    <!-- 封面大图：点击进入编辑；无图/坏图显示占位 -->
    <div class="cover-stage" @click="openEdit">
      <el-image
        v-if="modelValue && !loadFailed"
        :src="modelValue"
        :preview-src-list="[modelValue]"
        fit="cover"
        class="cover-image"
        preview-teleported
        @error="loadFailed = true"
        @load="loadFailed = false"
      />
      <div v-else class="cover-placeholder">
        <el-icon :size="28"><Picture /></el-icon>
        <span>{{ loadFailed ? '封面加载失败，点击重设' : '设置封面' }}</span>
      </div>
      <div class="cover-mask">
        <el-icon><EditPen /></el-icon>
        <span>更换封面</span>
      </div>
    </div>

    <div v-if="modelValue" class="cover-actions">
      <el-button link type="primary" icon="Edit" @click="openEdit">更换封面</el-button>
      <el-button link type="danger" icon="Delete" @click="clearCover">清除</el-button>
    </div>

    <!-- 编辑弹窗：链接 / 本地上传 / AI 生成 -->
    <el-dialog v-model="editOpen" title="设置菜品封面" width="520px" append-to-body :close-on-click-modal="false">
      <el-tabs v-model="mode">
        <el-tab-pane label="图片链接" name="link">
          <el-input v-model="draft" placeholder="https://... 或以 / 开头的站内资源地址" clearable />
          <div v-if="draft && draftValid" class="preview-row">
            <span class="preview-label">预览</span>
            <el-image :src="draft" fit="cover" class="preview-image" :preview-src-list="[draft]" preview-teleported />
          </div>
          <el-alert v-if="draft && !draftValid" type="error" :closable="false" show-icon
            title="封面地址必须以 http:// 或 https:// 开头，且不能是逗号拼接的多图串" />
        </el-tab-pane>
        <el-tab-pane label="本地上传" name="upload">
          <el-upload
            :show-file-list="false"
            :before-upload="beforeUpload"
            :http-request="handleUpload"
            accept="image/png,image/jpeg,image/webp"
          >
            <el-button type="primary" :loading="uploading">选择图片</el-button>
          </el-upload>
          <div class="upload-tip">支持 png / jpg / jpeg / webp，单张不超过 5MB</div>
          <div v-if="draft" class="preview-row">
            <span class="preview-label">已选</span>
            <el-image :src="draft" fit="cover" class="preview-image" :preview-src-list="[draft]" preview-teleported />
          </div>
        </el-tab-pane>
        <el-tab-pane label="AI 生成" name="ai">
          <el-input
            v-model="aiDescription"
            type="textarea"
            :rows="3"
            maxlength="300"
            show-word-limit
            :disabled="generating"
            placeholder="画面描述，如：暖色灯光下的家常餐桌，俯拍，突出油亮的酱汁"
          />
          <div class="upload-tip">
            按菜名「{{ dishName || '（未填写）' }}」与描述生成，走 meal.dish.cover 场景；生成后仍需点「确定」才会回填。
          </div>
          <el-button
            class="ai-generate-btn"
            type="primary"
            icon="MagicStick"
            :loading="generating"
            :disabled="!dishName || !aiDescription.trim()"
            @click="generateByAi"
          >
            {{ generating ? '生成中…' : '生成封面' }}
          </el-button>
          <div v-if="aiError" class="ai-error">{{ aiError }}</div>
          <div v-if="aiGenerated && draft" class="preview-row">
            <span class="preview-label">生成结果</span>
            <el-image :src="draft" fit="cover" class="preview-image" :preview-src-list="[draft]" preview-teleported />
          </div>
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :disabled="!draftValid" @click="confirmDraft">确 定</el-button>
          <el-button @click="editOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="DishCoverEditor">
import { getToken } from '@/utils/auth'
import { isExternal } from '@/utils/validate'
import { aiGenerateDishCover } from '@/api/meal/dish'
import { ElMessage } from 'element-plus'
import { Picture, EditPen } from '@element-plus/icons-vue'
import type { UploadFileResult } from '@/types/api/common'
import type { MealDishAiCoverResult } from '@/types/api/meal/dish'

/** 封面地址（单个 URL 字符串） */
const modelValue = defineModel<string | undefined>({ default: undefined })

const props = defineProps<{
  /** 菜名，AI 生成封面的主体 */
  dishName?: string
}>()

const emit = defineEmits<{
  /** AI 生成成功，父组件据此登记待转存任务 */
  (e: 'generated', payload: MealDishAiCoverResult): void
}>()

const editOpen = ref<boolean>(false)
const mode = ref<'link' | 'upload' | 'ai'>('link')
const draft = ref<string>('')
const uploading = ref<boolean>(false)
const loadFailed = ref<boolean>(false)
const aiDescription = ref<string>('')
const generating = ref<boolean>(false)
const aiError = ref<string>('')
/** 本弹窗内是否已由 AI 生成过，用于只展示生成结果预览 */
const aiGenerated = ref<boolean>(false)
/** 最近一次 AI 生成结果；只有用户点「确定」才通知父组件登记转存任务 */
const aiResult = ref<MealDishAiCoverResult | null>(null)

/** 上传地址：与全局 ImageUpload 一致走 /common/upload */
const uploadUrl = ref(import.meta.env.VITE_APP_BASE_API + '/common/upload')

/**
 * 校验草稿地址：
 * - 必须是 http/https 绝对地址或以 / 开头的站内路径
 * - 不允许逗号拼接（ImageUpload 的多值输出是逗号串，单图场景必须拦住）
 * - 不允许 javascript: / data: / file: 等协议
 */
const draftValid = computed(() => {
  const value = (draft.value || '').trim()
  if (!value) {
    return false
  }
  if (value.includes(',')) {
    return false
  }
  if (value.startsWith('/')) {
    return true
  }
  return isExternal(value) && /^https?:\/\//i.test(value)
})

watch(() => modelValue.value, () => {
  // 外部值变化后重置加载失败状态，避免换图后仍显示占位
  loadFailed.value = false
})

// 草稿被链接输入或上传改写后，丢弃上一次 AI 结果，避免把旧结果误登记为待转存
watch(draft, (value) => {
  if (aiResult.value && aiResult.value.cover !== value) {
    aiResult.value = null
    aiGenerated.value = false
  }
})

/** 打开编辑弹窗，草稿从当前值开始 */
function openEdit() {
  draft.value = modelValue.value || ''
  mode.value = 'link'
  aiError.value = ''
  aiGenerated.value = false
  aiResult.value = null
  editOpen.value = true
}

/**
 * 按菜名与画面描述调用文生图场景生成封面
 *
 * <p>强制重建必须带描述，与 meal-ui 的「AI 重新生成」保持同一接口语义；
 * 生成结果只写进草稿，用户点「确定」才真正回填，取消不改动原封面。</p>
 */
async function generateByAi() {
  const name = (props.dishName || '').trim()
  const description = aiDescription.value.trim()
  if (!name) {
    ElMessage.warning('请先填写菜名')
    return
  }
  if (!description) {
    ElMessage.warning('请填写画面描述')
    return
  }
  generating.value = true
  aiError.value = ''
  try {
    const response = await aiGenerateDishCover({
      name,
      current: { cover: '' },
      imageDescription: description,
      forceRegenerate: true
    })
    const result = response.data || {}
    if (!result.cover) {
      aiError.value = result.imageError || '封面生成失败，请稍后重试'
      return
    }
    draft.value = result.cover
    aiGenerated.value = true
    aiResult.value = result
    if (result.transferStatus === 'PENDING_TRANSFER') {
      ElMessage.warning('封面正在转存，请确认图片显示正常后再保存')
    } else {
      ElMessage.success('封面已生成')
    }
  } catch (error: any) {
    // 请求层已弹错误提示，这里只保留弹窗内的可读说明
    aiError.value = error?.message || '封面生成失败，请稍后重试'
  } finally {
    generating.value = false
  }
}

/** 上传前校验类型与大小 */
function beforeUpload(file: File): boolean {
  const allowed = ['image/png', 'image/jpeg', 'image/webp']
  if (!allowed.includes(file.type)) {
    ElMessage.error('只支持 png / jpg / jpeg / webp 图片')
    return false
  }
  if (file.size / 1024 / 1024 > 5) {
    ElMessage.error('图片大小不能超过 5MB')
    return false
  }
  return true
}

/** 自定义上传：成功后把返回地址写进草稿 */
function handleUpload(options: any) {
  uploading.value = true
  const formData = new FormData()
  formData.append('file', options.file)
  fetch(uploadUrl.value, {
    method: 'POST',
    headers: { Authorization: 'Bearer ' + getToken() },
    body: formData
  })
    .then(response => response.json() as Promise<UploadFileResult>)
    .then(result => {
      uploading.value = false
      if (result.code === 200 && result.fileName) {
        draft.value = result.fileName
        mode.value = 'upload'
        ElMessage.success('上传成功')
      } else {
        ElMessage.error(result.msg || '上传失败')
      }
    })
    .catch(() => {
      uploading.value = false
      ElMessage.error('上传失败')
    })
}

/** 确认：把草稿写回 modelValue；AI 生成结果在此刻才通知父组件登记转存 */
function confirmDraft() {
  if (!draftValid.value) {
    return
  }
  const published = draft.value.trim()
  modelValue.value = published
  loadFailed.value = false
  editOpen.value = false
  if (aiResult.value && aiResult.value.cover === published) {
    emit('generated', aiResult.value)
  }
  aiResult.value = null
}

/** 清除封面 */
function clearCover() {
  modelValue.value = undefined
  loadFailed.value = false
  aiResult.value = null
}
</script>

<style scoped>
.cover-editor {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.cover-stage {
  position: relative;
  width: 240px;
  height: 160px;
  border-radius: 10px;
  overflow: hidden;
  cursor: pointer;
  border: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-light);
}

.cover-image {
  width: 100%;
  height: 100%;
  display: block;
}

.cover-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--el-text-color-placeholder);
  font-size: 13px;
}

.cover-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: #fff;
  font-size: 13px;
  background: rgba(0, 0, 0, 0.45);
  opacity: 0;
  transition: opacity 0.2s;
}

.cover-stage:hover .cover-mask {
  opacity: 1;
}

.cover-actions {
  display: flex;
  gap: 4px;
}

.preview-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
}

.preview-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.preview-image {
  width: 96px;
  height: 64px;
  border-radius: 6px;
  border: 1px solid var(--el-border-color-lighter);
}

.upload-tip {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.ai-generate-btn {
  margin-top: 12px;
}

.ai-error {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-color-danger);
}
</style>
