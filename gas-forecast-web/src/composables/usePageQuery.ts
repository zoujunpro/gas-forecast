import { ref } from 'vue'
import { ElMessage } from 'element-plus'

export interface PageQueryContext {
  page: number
  size: number
  keyword: string
  signal: AbortSignal
}

export interface PageQueryResult<T> {
  records: T[]
  total?: number
}

export interface UsePageQueryOptions<T> {
  initialSize?: number
  errorMessage?: string
  fetcher: (context: PageQueryContext) => Promise<PageQueryResult<T>>
}

export const usePageQuery = <T extends Record<string, any>>(options: UsePageQueryOptions<T>) => {
  const loading = ref(false)
  const keyword = ref('')
  const page = ref(1)
  const size = ref(options.initialSize || 10)
  const total = ref(0)
  const records = ref<T[]>([])
  let activeController: AbortController | undefined

  const loadData = async () => {
    activeController?.abort()
    const controller = new AbortController()
    activeController = controller
    loading.value = true
    try {
      const result = await options.fetcher({
        page: page.value,
        size: size.value,
        keyword: keyword.value.trim(),
        signal: controller.signal
      })
      records.value = result.records
      total.value = result.total || 0
    } catch (error) {
      if (controller.signal.aborted) return
      ElMessage.error(error instanceof Error ? error.message : options.errorMessage || '加载失败')
    } finally {
      if (activeController === controller) {
        loading.value = false
      }
    }
  }

  const searchData = () => {
    page.value = 1
    void loadData()
  }

  const resetSearch = () => {
    keyword.value = ''
    searchData()
  }

  const handleSizeChange = () => {
    page.value = 1
    void loadData()
  }

  const rowIndex = (index: number) => (page.value - 1) * size.value + index + 1

  return {
    loading,
    keyword,
    page,
    size,
    total,
    records,
    loadData,
    searchData,
    resetSearch,
    handleSizeChange,
    rowIndex
  }
}
