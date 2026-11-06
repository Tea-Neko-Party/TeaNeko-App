// 2026/11/06：只在收到匹配的确定回执后结束待确认状态；协议错误不自动重试。
export interface ManagementReceipt {
  operationId: string; status: string; target: string; generation: number; revision: number; note: string
}
export type ManagementResource = 'memory' | 'personality' | 'layers' | 'schedules'
export interface PendingManagement { resource: ManagementResource; body: Record<string, unknown> }

const statuses: Record<ManagementResource, readonly string[]> = {
  memory: ['APPLIED', 'CONFLICT', 'NOT_FOUND', 'QUOTA_EXCEEDED', 'TOO_LARGE'],
  personality: ['APPLIED', 'UNCHANGED', 'CONFLICT', 'NOT_FOUND'],
  layers: ['APPLIED'],
  schedules: ['APPLIED', 'UNCHANGED', 'CONFLICT', 'NOT_FOUND'],
}

/** 接口返回 unknown，泛型断言不能替代运行时回执检查。 */
export function confirmedReceipt(value: unknown, pending: PendingManagement | undefined): ManagementReceipt {
  const invalid = () => new Error('结果未确认：管理回执无效或不匹配。请保留原操作编号，查询或按原编号恢复。')
  if (!pending || !value || typeof value !== 'object' || Array.isArray(value)) throw invalid()
  const data = value as Record<string, unknown>
  if (data.operationId !== pending.body.operationId || typeof data.operationId !== 'string'
    || typeof data.status !== 'string' || !statuses[pending.resource].includes(data.status)
    || typeof data.target !== 'string' || typeof data.note !== 'string'
    || !Number.isSafeInteger(data.generation) || !Number.isSafeInteger(data.revision)) throw invalid()
  if (data.status === 'APPLIED' || data.status === 'UNCHANGED') {
    // 克隆会返回新目标，其余成功动作按资源合同核对，不能拿空目标伪装成功。
    const body = pending.body
    const expected = pending.resource === 'memory' ? body.namespace
      : pending.resource === 'personality' ? body.key
      : pending.resource === 'layers' ? (body.action === 'REPLAY' ? body.namespace : body.id)
      : body.id
    if (!data.target || body.action !== 'CLONE' && body.action !== 'BACKUP' && data.target !== expected) throw invalid()
  }
  return data as unknown as ManagementReceipt
}

/** 指令台的受理也是明确回执；无效响应不能丢掉用于查询的原编号。 */
export function checkConsoleReceipt(value: unknown, operationId: string | undefined): void {
  const data = value as Record<string, unknown> | null
  if (!data || !operationId || data.operationId !== operationId || typeof data.status !== 'string'
    || !['ACCEPTED', 'RUNNING', 'COMPLETED', 'REJECTED', 'INDETERMINATE'].includes(data.status)
    || typeof data.commandStatus !== 'string' || typeof data.note !== 'string' || typeof data.at !== 'string'
    || !Array.isArray(data.output) || !data.output.every(item => typeof item === 'string')
    || !Array.isArray(data.confirmedMessages) || !data.confirmedMessages.every(item => typeof item === 'string')) {
    throw new Error('结果未确认：指令回执无效或不匹配，请使用原操作编号查询。')
  }
}
