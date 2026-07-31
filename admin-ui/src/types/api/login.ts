import type { AjaxResult } from './common'
import type { SysUser } from './system/user'

// 登录响应
export interface LoginInfoResult extends AjaxResult {
  /** 令牌 */
  token: string
}

/** 用户信息响应 */
export interface UserInfoResult extends AjaxResult {
  /** 用户信息 */
  user: SysUser
  /** 角色数据 */
  roles: string[]
  /** 权限数据 */
  permissions: string[]
  /** 初始密码是否提醒修改 */
  isDefaultModifyPwd?: boolean
  /** 密码是否过期 */
  isPasswordExpired?: boolean
}

/** 验证码响应 */
export interface CaptchaInfoResult extends AjaxResult {
  /** 验证码缓存key */
  uuid: string;
  /** 验证码图片Base64，不含 data URI 前缀 */
  img: string;
  /** 图片格式，png 或 gif（GIF 动态验证码不是 png，拼 data URI 时要用这个值） */
  imgType?: 'png' | 'gif'
  /** 验证码类型：char/math/line/circle/shear/gif */
  captchaType?: string
  /** 验证码开关 */
  captchaEnabled: boolean
  /** Cloudflare 人机校验开关，与验证码开关互相独立 */
  turnstileEnabled?: boolean
  /** Cloudflare 人机校验站点密钥，仅在开关打开时下发 */
  turnstileSiteKey?: string
}

/** 注册提交信息 */
export interface RegisterForm {
  username: string
  password: string
  confirmPassword: string
  code: string
  uuid: string
  /** Cloudflare 人机校验令牌，未开启人机校验时可不传 */
  turnstileToken?: string
}

/** 登录提交信息 */
export interface LoginForm {
  username: string
  password: string
  rememberMe?: boolean | string
  code: string
  uuid: string
  /** Cloudflare 人机校验令牌，未开启人机校验时可不传 */
  turnstileToken?: string
}
