// 2026/11/06：聊天与定时器各自验证端点合同，未知响应保留原请求及草稿。
export interface ChatReceipt { operationId: string; messageId: string; status: string; note: string }
export interface TimerReceipt { operationId: string; timer: string; revision: number; paused: boolean; at: string; note: string }
export interface TimerPending { id: string; change: { operationId: string; revision: number; paused: boolean } }

function object(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw invalid()
  return value as Record<string, unknown>
}

function invalid(): Error {
  return new Error('结果未确认：回执无效或与原请求不匹配。请保留原操作编号，查询原回执。')
}

/** 消息已持久化才允许清空草稿；后续分发状态不代表 Agent 已回复。 */
export function chatReceipt(value: unknown, operationId: string): ChatReceipt {
  const data = object(value)
  if (!operationId || data.operationId !== operationId || typeof data.messageId !== 'string' || !data.messageId
    || typeof data.note !== 'string' || typeof data.status !== 'string'
    || !['PERSISTED', 'DISPATCHED', 'REJECTED', 'INDETERMINATE'].includes(data.status)) throw invalid()
  return {operationId, messageId: data.messageId, status: data.status, note: data.note}
}

/** 定时器没有通用 status 字段；以目标、版本及暂停状态核对实际确认。 */
export function timerReceipt(value: unknown, operationId: string, timer: string, pending?: TimerPending): TimerReceipt {
  const data = object(value)
  if (!operationId || data.operationId !== operationId || data.timer !== timer || !timer
    || typeof data.revision !== 'number' || !Number.isSafeInteger(data.revision) || data.revision < 0
    || typeof data.paused !== 'boolean' || typeof data.note !== 'string'
    || typeof data.at !== 'string' || !Number.isFinite(Date.parse(data.at))) throw invalid()
  if (pending && (data.paused !== pending.change.paused
    || ![pending.change.revision, pending.change.revision + 1].includes(data.revision))) throw invalid()
  return {operationId, timer, revision: data.revision, paused: data.paused, at: data.at, note: data.note}
}
