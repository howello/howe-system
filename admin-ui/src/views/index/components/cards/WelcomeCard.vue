<template>
  <HomeCard
    title="欢迎"
    icon-text="欢"
    link-text="个人中心"
    link-to="/user/profile"
    link-icon-name="link"
    link-title="进入个人中心"
  >
    <div class="welcome">
      <div class="welcome__avatar">{{ initial }}</div>
      <div class="welcome__main">
        <div class="welcome__hi">{{ greeting }}，{{ nickName || '同学' }}</div>
        <div class="welcome__meta">
          <span v-if="lastLoginTime">上次登录：{{ lastLoginTime }}</span>
          <span v-if="lastLoginIp"> · {{ lastLoginIp }}</span>
          <span v-if="!lastLoginTime && !lastLoginIp">首次登录，欢迎加入</span>
        </div>
        <!-- 一言：引用块样式（左侧竖线 + 装饰引号 + 正文 + 弱化署名） -->
        <figure class="welcome__hitokoto" :class="{ 'is-loading': hitokotoLoading }">
          <blockquote class="welcome__quote-block">
            <span class="welcome__text">{{ hitokotoText }}</span>
          </blockquote>
          <figcaption v-if="hitokotoFrom" class="welcome__from">{{ hitokotoFrom }}</figcaption>
        </figure>
      </div>
      <div class="welcome__side">
        <span v-for="role in roleNames" :key="role" class="welcome__role">{{ role }}</span>
        <el-button :icon="Refresh" circle title="刷新全部卡片" @click="emit('refresh')" />
      </div>
    </div>
  </HomeCard>
</template>

<script setup lang="ts" name="WelcomeCard">
// 欢迎卡：问候语 + 昵称 + 角色 + 上次登录 + 一言
// 一言取不到时静默降级为本地兜底句，不显示加载态、不阻塞首页
import { Refresh } from '@element-plus/icons-vue'
import HomeCard from '../HomeCard.vue'
import { getHomeWelcome } from '@/api/home/cards'
import type { AjaxResult, HomeWelcome } from '@/types'

const emit = defineEmits<{ (e: 'refresh'): void }>()

/** 一言接口：与 blog-ui 同源；用 JSON 以便取得来源署名 */
const HITOKOTO_API =
  'https://hitokoto.wyantao.com/?c=a&c=b&c=c&c=d&c=h&c=i&c=j&c=k&encode=json&charset=utf-8&min_length=8&max_length=40'

/** 本地兜底句：一言不可用时使用 */
const HITOKOTO_FALLBACK = '保持努力，保持进步。'

/** 一言请求超时（毫秒） */
const HITOKOTO_TIMEOUT = 3500

const info = ref<HomeWelcome | null>(null)
const hitokotoText = ref(HITOKOTO_FALLBACK)
const hitokotoFrom = ref('')
const hitokotoLoading = ref(true)

const nickName = computed(() => info.value?.nickName ?? '')
const roleNames = computed(() => info.value?.roleNames ?? [])
const lastLoginTime = computed(() => info.value?.lastLoginTime ?? '')
const lastLoginIp = computed(() => info.value?.lastLoginIp ?? '')

/** 头像首字母 */
const initial = computed(() => (nickName.value || 'U').charAt(0).toUpperCase())

/** 按当前时间问候 */
const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 12) return '早上好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

async function loadWelcome(): Promise<void> {
  try {
    const res: AjaxResult<HomeWelcome> = await getHomeWelcome()
    info.value = res.data ?? null
  } catch {
    // 欢迎信息失败不影响其它卡片，保持占位
  }
}

async function loadHitokoto(): Promise<void> {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), HITOKOTO_TIMEOUT)
  try {
    const res = await fetch(HITOKOTO_API, { signal: controller.signal })
    if (!res.ok) return
    const payload = (await res.json()) as { hitokoto?: string; from?: string; from_who?: string }
    const text = (payload.hitokoto ?? '').trim()
    // 长度与 HTML 特征双重校验，挡掉错误页与劫持内容
    if (!text || text.length > 60 || text.includes('<')) return
    hitokotoText.value = text
    // 来源署名：优先用 from_who（作者），退化为 from（出处）
    hitokotoFrom.value = (payload.from_who || payload.from || '').trim()
  } catch {
    // 静默：兜底句已就位
  } finally {
    clearTimeout(timer)
    hitokotoLoading.value = false
  }
}

onMounted(() => {
  loadWelcome()
  loadHitokoto()
})

/** 刷新：欢迎信息与一言一起重新拉取 */
function reload(): void {
  loadWelcome()
  loadHitokoto()
}

defineExpose({ reload })
</script>

<style scoped lang="scss">
.welcome {
  display: flex;
  align-items: center;
  gap: 18px;

  &__avatar {
    flex: none;
    width: 52px;
    height: 52px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 20px;
    font-weight: 600;
    color: var(--home-card-bg);
    background: linear-gradient(135deg, var(--home-accent), #7ab8ff);
  }

  &__main {
    min-width: 0;
    flex: 1;
  }

  &__hi {
    font-size: 17px;
    font-weight: 600;
    color: var(--home-text-primary);
  }

  &__meta {
    margin-top: 4px;
    font-size: 12px;
    color: var(--home-text-muted);
  }

  // 一言：引用块（左侧主色竖线 + 装饰引号 + 正文 + 弱化署名）
  &__hitokoto {
    position: relative;
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 3px;
    margin: 8px 0 0;
    padding: 2px 0 2px 20px;
    max-width: 760px;
    border-left: 3px solid rgba(64, 158, 255, 0.35);

    // 装饰性引号（绝对定位在竖线右侧上方）
    &::before {
      content: '“';
      position: absolute;
      left: 5px;
      top: -6px;
      font-size: 22px;
      line-height: 1;
      color: var(--home-accent);
      opacity: 0.45;
      font-family: Georgia, 'Times New Roman', serif;
    }

    &.is-loading .welcome__text {
      color: var(--home-text-muted);
      font-style: normal;
    }
  }

  // 正文：与署名同一行排布时右对齐，长句自动换行且不溢出
  &__quote-block {
    margin: 0;
    width: 100%;
    text-align: left;
  }

  &__text {
    font-size: 13px;
    line-height: 1.7;
    font-style: italic;
    color: var(--home-text-secondary);
    // 长句换行，且不撑破卡片
    word-break: break-word;
  }

  // 署名：弱化处理（更小字号 + 次要色 + 前缀破折号由 CSS 提供）
  &__from {
    font-size: 11px;
    color: var(--home-text-muted);
    font-style: normal;
    line-height: 1.4;

    &::before {
      content: '—— ';
      opacity: 0.7;
    }
  }

  &__side {
    flex: none;
    display: flex;
    align-items: center;
    gap: 6px;
  }

  &__role {
    font-size: 12px;
    padding: 3px 10px;
    border-radius: 999px;
    background: var(--home-tile-bg);
    border: 1px solid var(--home-line);
    color: var(--home-text-secondary);
    white-space: nowrap;
  }
}

@media (max-width: 768px) {
  .welcome {
    flex-direction: column;
    align-items: flex-start;

    &__side {
      width: 100%;
      justify-content: flex-start;
    }
  }
}
</style>
