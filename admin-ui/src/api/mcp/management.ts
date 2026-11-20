import request from '@/utils/request'
import type { AjaxResult, TableDataInfo, McpAdminQuery, McpAdminClient, McpAdminClientDetail, McpAdminGrant, McpAdminConfiguration } from '@/types'
/** 客户端登记列表使用现有后台 JWT 请求层。 */
export function listMcpClients(params: McpAdminQuery): Promise<TableDataInfo<McpAdminClient[]>> { return request({ url: '/mcp/manage/client/list', params }) }
/** 客户端详情仅返回公开元数据。 */
export function getMcpClient(id: string): Promise<AjaxResult<McpAdminClientDetail>> { return request({ url: '/mcp/manage/client/' + encodeURIComponent(id) }) }
/** 停用客户端需要独立 edit 权限。 */
export function setMcpClientStatus(id: string, status: string): Promise<AjaxResult> { return request({ url: `/mcp/manage/client/${encodeURIComponent(id)}/status`, method: 'put', data: { status } }) }
/** 全站授权列表与本人 self 接口分开。 */
export function listMcpGrants(params: McpAdminQuery): Promise<TableDataInfo<McpAdminGrant[]>> { return request({ url: '/mcp/manage/grant/list', params }) }
/** 查询授权详情不包含凭据。 */
export function getMcpGrant(id: number): Promise<AjaxResult<McpAdminGrant>> { return request({ url: '/mcp/manage/grant/' + id }) }
/** 管理员撤销一条授权。 */
export function revokeMcpGrant(id: number): Promise<AjaxResult> { return request({ url: `/mcp/manage/grant/${id}/revoke`, method: 'post' }) }
/** 获取已生效地址和重启提示，不修改任何参数。 */
export function getMcpConfiguration(): Promise<AjaxResult<McpAdminConfiguration>> { return request({ url: '/mcp/manage/configuration' }) }
