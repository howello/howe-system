import { getCurrentInstance } from 'vue'

/** 在组件 setup 中取得已初始化的实例，避免全局覆写 Vue 的原始类型。 */
export function useAppProxy() {
  const proxy = getCurrentInstance()?.proxy
  if (!proxy) throw new Error('当前代码必须在组件 setup 中调用')
  return proxy
}

/** 字符串模板引用使用的表单校验契约。 */
interface FormRef {
  validate(callback: (valid: boolean) => void): void
}

/** 校验动态模板引用具有表单能力后返回类型，引用未挂载时明确报错。 */
export function formRef(value: unknown): FormRef {
  if (!value || typeof (value as FormRef).validate !== 'function') {
    throw new Error('表单尚未初始化')
  }
  return value as FormRef
}

/** 读取已有明确组件契约的动态模板引用，未挂载时明确报错。 */
export function templateRef<T>(value: unknown): T {
  if (value == null) throw new Error('组件尚未初始化')
  return value as T
}
