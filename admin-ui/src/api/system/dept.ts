import request from '@/utils/request'
import type { DeptQueryParams, SysDept, DeptSortParams, AjaxResult } from '@/types'

// 查询部门列表
export function listDept(query?: DeptQueryParams): Promise<AjaxResult<SysDept[]>> {
  return request({
    url: '/system/dept/list',
    method: 'get',
    params: query
  })
}

// 查询部门列表（排除节点）
export function listDeptExcludeChild(deptId: number): Promise<AjaxResult<SysDept[]>> {
  return request({
    url: '/system/dept/list/exclude/' + deptId,
    method: 'get'
  })
}

// 查询部门详细
export function getDept(deptId: number): Promise<AjaxResult<SysDept>> {
  return request({
    url: '/system/dept/' + deptId,
    method: 'get'
  })
}

// 新增部门
export function addDept(data: SysDept): Promise<AjaxResult> {
  return request({
    url: '/system/dept',
    method: 'post',
    data: data
  })
}

// 修改部门
export function updateDept(data: SysDept): Promise<AjaxResult> {
  return request({
    url: '/system/dept',
    method: 'put',
    data: data
  })
}

// 保存部门排序
export function updateDeptSort(data: DeptSortParams): Promise<AjaxResult> {
  return request({
    url: '/system/dept/updateSort',
    method: 'put',
    data: data
  })
}

// 删除部门
export function delDept(deptId: number): Promise<AjaxResult> {
  return request({
    url: '/system/dept/' + deptId,
    method: 'delete'
  })
}

/** 家庭邀请码信息 */
export interface FamilyCodeInfo {
  /** 邀请码；没有或已过期时为 null */
  code: string | null
  /** 剩余有效期（秒） */
  expireSeconds: number
  /** 有效期（天） */
  ttlDays: number
}

// 查询家庭邀请码
export function getFamilyCode(deptId: number): Promise<AjaxResult<FamilyCodeInfo>> {
  return request({
    url: '/system/dept/familyCode/' + deptId,
    method: 'get'
  })
}

// 生成（或重新生成）家庭邀请码
export function generateFamilyCode(deptId: number): Promise<AjaxResult<FamilyCodeInfo>> {
  return request({
    url: '/system/dept/familyCode/' + deptId,
    method: 'post'
  })
}
