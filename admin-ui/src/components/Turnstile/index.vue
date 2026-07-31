<template>
  <div ref="containerRef" class="turnstile-widget"></div>
</template>

<script setup lang="ts">
/**
 * Cloudflare Turnstile 人机校验组件
 *
 * 站点密钥由后端 /captchaImage 随开关一起下发，前端不写死。
 * 令牌是一次性的：登录失败后必须 reset() 换一个，否则后端第二次校验必然不通过。
 */
declare global {
  interface Window {
    turnstile?: {
      render: (container: HTMLElement, options: Record<string, unknown>) => string
      reset: (widgetId?: string) => void
      remove: (widgetId?: string) => void
    }
  }
}

const props = withDefaults(
  defineProps<{
    /** 令牌，v-model 绑定 */
    modelValue?: string
    /** 站点密钥 */
    siteKey: string
    /** 主题 */
    theme?: 'auto' | 'light' | 'dark'
    /** 组件尺寸 */
    size?: 'normal' | 'flexible' | 'compact'
    /** 语言，默认跟随浏览器 */
    language?: string
  }>(),
  { modelValue: '', theme: 'auto', size: 'flexible', language: 'zh-cn' }
)

const emit = defineEmits<{
  (e: 'update:modelValue', token: string): void
  (e: 'error', message: string): void
}>()

const SCRIPT_ID = 'cf-turnstile-script'
const SCRIPT_SRC = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit'

const containerRef = ref<HTMLElement>()
let widgetId: string | undefined
let scriptPromise: Promise<void> | undefined

/** 按需加载官方脚本，多个实例共用同一个 promise，避免重复插入 script */
function loadScript(): Promise<void> {
  if (window.turnstile) {
    return Promise.resolve()
  }
  if (scriptPromise) {
    return scriptPromise
  }
  scriptPromise = new Promise<void>((resolve, reject) => {
    const exist = document.getElementById(SCRIPT_ID) as HTMLScriptElement | null
    if (exist) {
      exist.addEventListener('load', () => resolve())
      exist.addEventListener('error', () => reject(new Error('Turnstile 脚本加载失败')))
      return
    }
    const script = document.createElement('script')
    script.id = SCRIPT_ID
    script.src = SCRIPT_SRC
    script.async = true
    script.defer = true
    script.onload = () => resolve()
    script.onerror = () => reject(new Error('Turnstile 脚本加载失败'))
    document.head.appendChild(script)
  })
  return scriptPromise
}

function render(): void {
  if (!containerRef.value || !window.turnstile || !props.siteKey) {
    return
  }
  widgetId = window.turnstile.render(containerRef.value, {
    sitekey: props.siteKey,
    theme: props.theme,
    size: props.size,
    language: props.language,
    callback: (token: string) => emit('update:modelValue', token),
    'expired-callback': () => {
      // 令牌有效期约 5 分钟，过期后清空，避免拿着废票去登录
      emit('update:modelValue', '')
      window.turnstile?.reset(widgetId)
    },
    'error-callback': () => {
      emit('update:modelValue', '')
      emit('error', '人机校验加载失败，请刷新页面重试')
    }
  })
}

/** 重置挑战，登录失败后必须调用 */
function reset(): void {
  emit('update:modelValue', '')
  if (widgetId !== undefined) {
    window.turnstile?.reset(widgetId)
  }
}

onMounted(() => {
  loadScript()
    .then(() => nextTick())
    .then(() => render())
    .catch((e: Error) => emit('error', e.message))
})

onBeforeUnmount(() => {
  if (widgetId !== undefined) {
    window.turnstile?.remove(widgetId)
    widgetId = undefined
  }
})

defineExpose({ reset })
</script>

<style scoped>
.turnstile-widget {
  width: 100%;
  min-height: 65px;
}
</style>
