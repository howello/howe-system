/**
 * MCP 模块分组。
 *
 * 模块取能力分组第一段（`system.notice.write` → `system`），平台工具固定为 `mcp`。
 * 工具目录筛选与授权页 tab 共用同一顺序和名称，避免两处各自维护。
 */
export interface McpModuleMeta {
  /** 模块标识 */
  id: string
  /** 中文名称 */
  label: string
}

/** 固定展示顺序；未知模块排在末尾并使用原标识。 */
export const MCP_MODULES: McpModuleMeta[] = [
  { id: 'mcp', label: '平台' },
  { id: 'system', label: '系统' },
  { id: 'blog', label: '博客' },
  { id: 'meal', label: '点餐' },
  { id: 'share', label: '分享' },
  { id: 'ai', label: 'AI' },
  { id: 'other', label: '其他' }
]

/** 模块显示顺序索引，未知模块统一排在最后。 */
const MODULE_ORDER = new Map(MCP_MODULES.map((item, index) => [item.id, index]))

/**
 * 从能力分组提取模块标识。
 *
 * @param capabilityGroup 能力分组，如 `system.notice.write`
 * @returns 第一段标识；空值返回 `other`
 */
export function mcpModuleOf(capabilityGroup: string | null | undefined): string {
  if (!capabilityGroup) return 'other'
  const index = capabilityGroup.indexOf('.')
  return index < 0 ? capabilityGroup : capabilityGroup.slice(0, index)
}

/**
 * 返回模块中文名称。
 *
 * @param id 模块标识
 * @returns 已知模块的中文名，未知模块保留原标识
 */
export function mcpModuleLabel(id: string): string {
  return MCP_MODULES.find(item => item.id === id)?.label ?? id
}

/**
 * 按固定顺序排序模块标识。
 *
 * @param ids 模块标识集合
 * @returns 去重并排序后的新数组
 */
export function mcpSortModules(ids: string[]): string[] {
  return Array.from(new Set(ids)).sort((left, right) => {
    const leftIndex = MODULE_ORDER.get(left) ?? Number.MAX_SAFE_INTEGER
    const rightIndex = MODULE_ORDER.get(right) ?? Number.MAX_SAFE_INTEGER
    return leftIndex === rightIndex ? left.localeCompare(right) : leftIndex - rightIndex
  })
}
