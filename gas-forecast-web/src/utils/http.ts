export const displayValue = (value: unknown) => {
  if (value === null || value === undefined || value === '') return '-'
  return value
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
