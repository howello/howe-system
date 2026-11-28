/** 读书节点类型：分类 / 书本 / 感悟 / 备注。 */
export type BookNodeType = 'category' | 'book' | 'note' | 'remark'

/** 感悟正文块。 */
export interface BookNodeBlock {
  id?: string
  type: 'text' | 'image' | 'audio' | 'quote' | string
  /** 文字、引用或语音转写正文 */
  text?: string
  /** 已上传媒体地址 */
  url?: string
  /** 图片说明 */
  caption?: string
  /** 引用页码 */
  page?: number
  /** 语音时长（秒） */
  duration?: number
}

/** 节点正文 JSON，不同节点类型使用其中不同字段。 */
export interface BookNodeContent {
  /** 分类说明 */
  desc?: string
  /** 封面配色与图案 */
  color?: string
  art?: string
  /** 书本书目信息 */
  author?: string
  translator?: string
  publisher?: string
  year?: string
  pages?: string
  isbn?: string
  rating?: string
  status?: string
  progress?: number
  intro?: string
  tags?: string[]
  /** 感悟章节 */
  chapter?: string
  summary?: string
  blocks?: BookNodeBlock[]
  /** 感悟 / 备注纯文本 */
  text?: string
  attachments?: unknown[]
}

/** 读书节点。 */
export interface BookNode {
  id?: string
  userId?: number
  parentId?: string | null
  nodeType: BookNodeType
  title: string
  sortOrder?: number
  coverKind?: string | null
  coverRef?: string | null
  contentJson?: string | null
  version?: number
  createdAt?: string
  updatedAt?: string
  childCount?: number
}

/** 节点查询参数：传 parentId 只查该父节点下一层，不传查全部。 */
export interface BookNodeQuery {
  /** 父节点 ID，传 0 表示顶层分类 */
  parentId?: string
  /** 节点类型 */
  type?: BookNodeType | ''
  /** 标题模糊查询 */
  title?: string
  userId?: number
  pageNum?: number
  pageSize?: number
}

/** 书架概览统计。 */
export interface BookShelfStats {
  categories: number
  books: number
  notes: number
  weekNotes: number
}
