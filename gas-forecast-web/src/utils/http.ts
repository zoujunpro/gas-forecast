export const displayValue = (value: unknown) => {
  if (value === null || value === undefined || value === '') return '-'
  return value
}

const DEFAULT_TIMEOUT_MS = 20_000

export interface RequestOptions extends RequestInit {
  timeoutMs?: number
}

export const apiFetch = async (input: RequestInfo | URL, options: RequestOptions = {}) => {
  const { timeoutMs = DEFAULT_TIMEOUT_MS, signal, ...init } = options
  const controller = new AbortController()
  const abort = () => controller.abort(signal?.reason)
  signal?.addEventListener('abort', abort, { once: true })
  const timer = window.setTimeout(() => controller.abort(new DOMException('请求超时', 'TimeoutError')), timeoutMs)

  try {
    return await fetch(input, { ...init, signal: controller.signal })
  } catch (error) {
    if (controller.signal.reason instanceof DOMException && controller.signal.reason.name === 'TimeoutError') {
      throw new Error(`请求超时（${Math.round(timeoutMs / 1000)}秒）`)
    }
    throw error
  } finally {
    window.clearTimeout(timer)
    signal?.removeEventListener('abort', abort)
  }
}

export const readErrorMessage = async (response: Response, fallback: string) => {
  try {
    const data = await response.json()
    return data.message || `${fallback}：HTTP ${response.status}`
  } catch {
    return `${fallback}：HTTP ${response.status}`
  }
}

export const readResponseResult = async <T = any>(response: Response): Promise<T> => {
  let result: any = {}
  const text = await response.text()
  if (text) {
    try {
      result = JSON.parse(text)
    } catch {
      throw new Error(`响应格式错误：HTTP ${response.status}`)
    }
  }

  if (!response.ok || result.code !== '0000') {
    throw new Error(result.message || `请求失败：HTTP ${response.status}`)
  }
  return result
}
