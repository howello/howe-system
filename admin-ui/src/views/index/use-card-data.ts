/**
 * 首页卡片取数 composable：统一「加载中 / 失败重试 / 数据到达」三态
 *
 * 每张卡片独立请求、独立失败态：失败时卡片内显示失败原因与重试入口，
 * 其它卡片正常显示；不设置失败状态就等于静默吞掉错误，因此统一在此实现。
 */
import type { CardLoadState } from '@/views/index/components/CardState.vue'

/**
 * 卡片取数封装
 *
 * @param loader 取数函数（负责把结果写入调用方的 ref）
 * @returns 状态与重试方法；`reload` 供刷新与重试复用
 */
export function useCardData(loader: () => Promise<void>) {
  const state = ref<CardLoadState>('loading')
  const errorMessage = ref('')

  async function reload(): Promise<void> {
    state.value = state.value === 'ready' ? 'ready' : 'loading'
    errorMessage.value = ''
    try {
      await loader()
      state.value = 'ready'
    } catch (e) {
      state.value = 'failed'
      errorMessage.value = e instanceof Error ? e.message : '加载失败'
    }
  }

  onMounted(reload)

  return { state, errorMessage, reload }
}
