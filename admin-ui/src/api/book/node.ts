import request from '@/utils/request'
import type { AjaxResult, BookNode, BookNodeQuery, BookShelfStats, TableDataInfo } from '@/types'

/** 查询读书节点：传 parentId 只查该父节点下一层，不传查全部（搜索用）。 */
export function listBookNodes(query: BookNodeQuery): Promise<TableDataInfo<BookNode[]>> {
  return request({
    url: '/book/admin/nodes',
    method: 'get',
    params: query
  })
}

/** 查询单个读书节点。 */
export function getBookNode(id: string): Promise<AjaxResult<BookNode>> {
  return request({
    url: '/book/admin/nodes/' + id,
    method: 'get'
  })
}

/** 新建读书节点。 */
export function addBookNode(data: BookNode): Promise<AjaxResult<BookNode>> {
  return request({
    url: '/book/admin/nodes',
    method: 'post',
    data
  })
}

/** 修改读书节点。 */
export function updateBookNode(id: string, data: BookNode): Promise<AjaxResult<BookNode>> {
  return request({
    url: '/book/admin/nodes/' + id,
    method: 'put',
    data
  })
}

/** 删除读书节点及其所有后代。 */
export function delBookNode(id: string): Promise<AjaxResult<string[]>> {
  return request({
    url: '/book/admin/nodes/' + id,
    method: 'delete'
  })
}

/** 查询共享书架统计。 */
export function getBookStats(): Promise<AjaxResult<BookShelfStats>> {
  return request({
    url: '/book/nodes/stats',
    method: 'get'
  })
}
