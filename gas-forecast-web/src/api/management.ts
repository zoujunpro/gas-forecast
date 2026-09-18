import { readResponseResult } from '@/utils/http'

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

export const postJson = async <T = any>(url: string, data: Record<string, any> = {}) => {
  const response = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data)
  })
  return readResponseResult<T>(response)
}

export const listPage = async <T = Record<string, any>>(endpoint: string, params: PageRequest): Promise<PageData<T>> => {
  const result = await postJson(`${endpoint}/listPage`, params)
  const data = result.data || {}
  return {
    records: data.records || [],
    total: data.total || 0
  }
}

export const listAll = async <T = Record<string, any>>(endpoint: string, keyword?: string): Promise<T[]> => {
  const params = new URLSearchParams()
  if (keyword) params.set('keyword', keyword)
  const response = await fetch(`${endpoint}/list?${params.toString()}`)
  const result = await readResponseResult(response)
  return result.data || []
}

export const createRow = (endpoint: string, data: Record<string, any>) => postJson(`${endpoint}/create`, data)

export const updateRow = (endpoint: string, data: Record<string, any>) => postJson(`${endpoint}/update`, data)

export const saveRow = (endpoint: string, data: Record<string, any>) => postJson(`${endpoint}/save`, data)

export const deleteRow = async (endpoint: string, id: number | string) => {
  const params = new URLSearchParams({ id: String(id) })
  const response = await fetch(`${endpoint}/delete?${params.toString()}`)
  return readResponseResult(response)
}
