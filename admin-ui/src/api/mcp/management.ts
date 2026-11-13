import request from '@/utils/request'
import type { AjaxResult, TableDataInfo, McpAdminQuery, McpAdminClient, McpAdminClientDetail, McpAdminTool, McpAdminToolQuery, McpAdminToolSummary, McpAdminToolStatusResult, McpAdminGrant, McpAdminCall, McpAdminConfiguration, McpAdminStatistics } from '@/types'
/** 客户端登记列表使用现有后台 JWT 请求层。 */
export function listMcpClients(params: McpAdminQuery): Promise<TableDataInfo<McpAdminClient[]>> { return request({ url: '/mcp/manage/client/list', params }) }
/** 客户端详情仅返回公开元数据。 */
export function getMcpClient(id: string): Promise<AjaxResult<McpAdminClientDetail>> { return request({ url: '/mcp/manage/client/' + encodeURIComponent(id) }) }
/** 停用客户端需要独立 edit 权限。 */
export function setMcpClientStatus(id: string, status: string): Promise<AjaxResult> { return request({ url: `/mcp/manage/client/${encodeURIComponent(id)}/status`, method: 'put', data: { status } }) }
/** 分页获取代码定义的工具目录；查询与分页由服务端内存目录执行。 */
export function listMcpTools(params: McpAdminToolQuery): Promise<TableDataInfo<McpAdminTool[]>> { return request({ url: '/mcp/manage/tool/list', params }) }
/** 获取代码目录全量统计与筛选选项。 */
export function getMcpToolSummary(): Promise<AjaxResult<McpAdminToolSummary>> { return request({ url: '/mcp/manage/tool/summary' }) }
/** 获取工具契约详情。 */
export function getMcpTool(name: string): Promise<AjaxResult<McpAdminTool>> { return request({ url: '/mcp/manage/tool/' + encodeURIComponent(name) }) }
/** 只修改工具开关，不接受动态实现或权限修改。 */
export function setMcpToolStatus(name: string, enabled: string): Promise<AjaxResult> { return request({ url: `/mcp/manage/tool/${encodeURIComponent(name)}/status`, method: 'put', data: { enabled } }) }
/** 全局切换代码目录全部工具，范围不受当前筛选与分页影响。 */
export function setAllMcpToolStatus(enabled: string): Promise<AjaxResult<McpAdminToolStatusResult>> { return request({ url: '/mcp/manage/tool/status', method: 'put', data: { enabled } }) }
/** 全站授权列表与本人 self 接口分开。 */
export function listMcpGrants(params: McpAdminQuery): Promise<TableDataInfo<McpAdminGrant[]>> { return request({ url: '/mcp/manage/grant/list', params }) }
/** 查询授权详情不包含凭据。 */
export function getMcpGrant(id: number): Promise<AjaxResult<McpAdminGrant>> { return request({ url: '/mcp/manage/grant/' + id }) }
/** 管理员撤销一条授权。 */
export function revokeMcpGrant(id: number): Promise<AjaxResult> { return request({ url: `/mcp/manage/grant/${id}/revoke`, method: 'post' }) }
/** 查询固定字段的调用元数据。 */
export function listMcpCalls(params: McpAdminQuery): Promise<TableDataInfo<McpAdminCall[]>> { return request({ url: '/mcp/manage/calllog/list', params }) }
/** 查询单条调用事件详情。 */
export function getMcpCall(id: number): Promise<AjaxResult<McpAdminCall>> { return request({ url: '/mcp/manage/calllog/' + id }) }

/** 获取已生效地址和重启提示，不修改任何参数。 */
export function getMcpConfiguration(): Promise<AjaxResult<McpAdminConfiguration>> { return request({ url: '/mcp/manage/configuration' }) }
/** 获取单进程指标，不能重置计数。 */
export function getMcpStatistics(): Promise<AjaxResult<McpAdminStatistics>> { return request({ url: '/mcp/manage/statistics' }) }
