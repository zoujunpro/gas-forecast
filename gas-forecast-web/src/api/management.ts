import { apiFetch, readResponseResult } from '@/utils/http'

export interface PageRequest {
  page: number
  size: number
  keyword?: string
  [key: string]: any
}

export interface PageData<T = Record<string, any>> {
  records: T[]
  total: number
}

export interface ResponseData<T = any> {
  data?: T
  code?: string
  message?: string
}

export const postJson = async <T = any>(url: string, data: Record<string, any> = {}, signal?: AbortSignal, timeoutMs?: number) => {
  const response = await apiFetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
    signal,
    timeoutMs
  })
  return readResponseResult<T>(response)
}

/** 读取 POST SSE；事件 data 为 JSON 时自动解析，供聊天逐段展示使用。 */
export const postSse = async <T = any>(url: string, data: Record<string, any>, onEvent: (event: string, value: any) => void, signal?: AbortSignal) => {
  const response = await apiFetch(url, {
    method: 'POST', headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
    body: JSON.stringify(data), signal, timeoutMs: 150_000
  })
  if (!response.ok || !response.body) throw new Error(`请求失败：HTTP ${response.status}`)
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let done: T | undefined
  while (true) {
    const chunk = await reader.read()
    if (chunk.done) break
    buffer += decoder.decode(chunk.value, { stream: true })
    const blocks = buffer.split('\n\n'); buffer = blocks.pop() || ''
    for (const block of blocks) {
      const event = block.match(/^event:\s*(.+)$/m)?.[1]?.trim() || 'message'
      const raw = block.match(/^data:\s*(.+)$/m)?.[1]
      if (!raw) continue
      let value: any = raw
      try { value = JSON.parse(raw); if (typeof value === 'string') value = JSON.parse(value) } catch { /* plain text */ }
      onEvent(event, value)
      if (event === 'done') done = value as T
      if (event === 'error') throw new Error(typeof value === 'string' ? value : '处理失败')
    }
  }
  if (!done) throw new Error('流式响应未返回最终结果')
  return done
}

export const getJson = async <T = any>(url: string, signal?: AbortSignal): Promise<ResponseData<T>> => {
  const response = await apiFetch(url, { signal })
  return readResponseResult<ResponseData<T>>(response)
}

export const listPage = async <T = Record<string, any>>(
  endpoint: string,
  params: PageRequest,
  signal?: AbortSignal
): Promise<PageData<T>> => {
  const result = await postJson(`${endpoint}/listPage`, params, signal)
  const data = result.data || {}
  return {
    records: data.records || [],
    total: data.total || 0
  }
}

export const listAll = async <T = Record<string, any>>(endpoint: string, keyword?: string): Promise<T[]> => {
  const params = new URLSearchParams()
  if (keyword) params.set('keyword', keyword)
  const response = await apiFetch(`${endpoint}/list?${params.toString()}`)
  const result = await readResponseResult(response)
  return result.data || []
}

export const createRow = (endpoint: string, data: Record<string, any>) => postJson(`${endpoint}/create`, data)

export const updateRow = (endpoint: string, data: Record<string, any>) => postJson(`${endpoint}/update`, data)

export const saveRow = (endpoint: string, data: Record<string, any>) => postJson(`${endpoint}/save`, data)

export const deleteRow = async (endpoint: string, id: number | string) => {
  const params = new URLSearchParams({ id: String(id) })
  const response = await apiFetch(`${endpoint}/delete?${params.toString()}`)
  return readResponseResult(response)
}
