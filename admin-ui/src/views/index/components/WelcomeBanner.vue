<template>
  <div class="welcome-banner">
    <div class="welcome-banner__content">
      <h2 class="welcome-banner__greeting">{{ greetingLine }}</h2>
      <p class="welcome-banner__date">{{ dateText }}</p>
      <p v-if="showHitokoto" ref="hitokotoEl" class="welcome-banner__hitokoto"></p>
    </div>
  </div>
</template>

<script setup lang="ts">
import useUserStore from '@/store/modules/user'

// 一言签名行：原生 fetch（不经 utils/request 封装），失败/超时/空串或跨域 CORS 未配时静默隐藏
const HITOKOTO_URL = 'https://hitokoto.wyantao.com/?c=a&c=b&c=c&c=d&c=h&c=i&c=j&c=k&encode=text&charset=utf-8&min_length=8&max_length=20'
const HITOKOTO_TIMEOUT = 3000
const WEEKDAY_NAMES = ['日', '一', '二', '三', '四', '五', '六']

const userStore = useUserStore()

/** 用户名：优先昵称，其次登录名 */
const displayName = computed(() => userStore.nickName || userStore.name)

/** 时段问候：5-11 早上好 / 12-17 下午好 / 18-22 晚上好 / 其余 夜深了 */
const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour >= 5 && hour < 12) {
    return '早上好'
  }
  if (hour >= 12 && hour < 18) {
    return '下午好'
  }
  if (hour >= 18 && hour < 23) {
    return '晚上好'
  }
  return '夜深了'
})

const greetingLine = computed(() => (displayName.value ? `${greeting.value}，${displayName.value}` : greeting.value))

/** 日期：yyyy年M月d日 星期X */
const dateText = computed(() => {
  const now = new Date()
  return `${now.getFullYear()}年${now.getMonth() + 1}月${now.getDate()}日 星期${WEEKDAY_NAMES[now.getDay()]}`
})

const showHitokoto = ref(false)
const hitokotoEl = ref<HTMLElement | null>(null)

onMounted(async () => {
  const controller = new AbortController()
  const timer = window.setTimeout(() => controller.abort(), HITOKOTO_TIMEOUT)
  try {
    const res = await fetch(HITOKOTO_URL, { signal: controller.signal })
    const text = (await res.text()).trim()
    if (res.ok && text) {
      showHitokoto.value = true
      nextTick(() => {
        if (hitokotoEl.value) {
          // textContent 插入，防止注入
          hitokotoEl.value.textContent = text
        }
      })
    }
  } catch {
    // 失败/超时/跨域 CORS 未配 → 静默隐藏一言行，不影响布局
  } finally {
    window.clearTimeout(timer)
  }
})
</script>

<style lang="scss" scoped>
.welcome-banner {
  position: relative;
  overflow: hidden;
  padding: 24px 28px;
  margin-bottom: 20px;
  border-radius: 8px;
  color: #fff;
  background: linear-gradient(135deg, #4176f4 0%, #5fa2f8 45%, #7fd0fb 100%);
  box-shadow: 4px 4px 40px rgba(0, 0, 0, .08);

  &__content {
    position: relative;
    z-index: 1;
  }

  &__greeting {
    margin: 0;
    font-size: 22px;
    font-weight: 600;
    line-height: 1.4;
  }

  &__date {
    margin: 8px 0 0;
    font-size: 14px;
    opacity: .92;
  }

  &__hitokoto {
    margin: 14px 0 0;
    padding-top: 12px;
    font-size: 15px;
    letter-spacing: .5px;
    opacity: .95;
    border-top: 1px solid rgba(255, 255, 255, .35);
  }
}

@media (max-width: 550px) {
  .welcome-banner {
    padding: 18px 20px;

    &__greeting {
      font-size: 18px;
    }
  }
}
</style>