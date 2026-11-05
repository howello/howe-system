import request from '@/utils/request'
import type { MealProposalQueryParams, MealProposal, MealProposalAuditBody, AjaxResult, TableDataInfo } from '@/types'

// 查询提案列表
export function listProposal(query: MealProposalQueryParams): Promise<TableDataInfo<MealProposal[]>> {
  return request({
    url: '/meal/proposal/list',
    method: 'get',
    params: query
  })
}

// 查询提案详情（详情弹窗用，取服务端最新数据）
export function getProposal(proposalId: number): Promise<AjaxResult<MealProposal>> {
  return request({
    url: '/meal/proposal/' + proposalId,
    method: 'get'
  })
}

// 审核提案（通过时自动生成菜品）
export function auditProposal(data: MealProposalAuditBody): Promise<AjaxResult> {
  return request({
    url: '/meal/proposal/audit',
    method: 'put',
    data: data
  })
}

// 删除提案
export function delProposal(proposalId: number | number[]): Promise<AjaxResult> {
  return request({
    url: '/meal/proposal/' + proposalId,
    method: 'delete'
  })
}
