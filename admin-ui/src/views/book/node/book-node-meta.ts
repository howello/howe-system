import type { BookNode, BookNodeContent, BookNodeType } from '@/types'

/** 节点类型显示名。 */
export const NODE_TYPE_LABELS: Record<BookNodeType, string> = {
  category: '分类',
  book: '书本',
  note: '感悟',
  remark: '备注'
}

/** 节点类型可选值，用于下拉与筛选。 */
export const NODE_TYPE_OPTIONS = (Object.keys(NODE_TYPE_LABELS) as BookNodeType[])
  .map(value => ({ label: NODE_TYPE_LABELS[value], value }))

/** 子节点类型约束：分类 → 书本 → 感悟 → 备注 → 备注。 */
export function childTypeOf(parent?: BookNode | null): BookNodeType {
  if (!parent) return 'category'
  if (parent.nodeType === 'category') return 'book'
  if (parent.nodeType === 'book') return 'note'
  return 'remark'
}

/** 节点正文里可选的父节点类型，与后端校验保持一致。 */
export function parentTypesOf(type: BookNodeType): BookNodeType[] {
  if (type === 'category' || type === 'book') return ['category']
  if (type === 'note') return ['book']
  return ['note', 'remark']
}

/** 解析节点正文，坏 JSON 一律回退为空对象。 */
export function parseNodeContent(node?: BookNode | null): BookNodeContent {
  if (!node?.contentJson) return {}
  try {
    const value = JSON.parse(node.contentJson)
    return value && typeof value === 'object' ? value as BookNodeContent : {}
  } catch {
    return {}
  }
}

/** 取一行摘要：分类说明、书本作者 + 简介、感悟章节 + 正文、备注正文。 */
export function excerptOf(node: BookNode): string {
  const content = parseNodeContent(node)
  if (node.nodeType === 'category') {
    return content.desc || ''
  }
  if (node.nodeType === 'book') {
    const author = content.author || ''
    const intro = content.intro || content.text || ''
    return [author, intro].filter(Boolean).join(' · ')
  }
  const chapter = content.chapter || ''
  const body = (content.blocks || [])
    .map(block => block.text || block.caption || '')
    .filter(Boolean)
    .join(' ')
    || content.summary || content.text || ''
  return [chapter, body].filter(Boolean).join(' · ')
}

/** 截断过长文本。 */
export function truncate(text: string, max = 80): string {
  const single = text.replace(/\s+/g, ' ').trim()
  return single.length > max ? single.slice(0, max) + '…' : single
}
