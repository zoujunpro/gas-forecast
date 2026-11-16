// 历史消息没有工具元数据，按明确的主数据名称字段识别，不能按表格标题判断。
const fields = [
  { field: 'customerName', title: '客户名称' },
  { field: 'regionName', title: '地区名称' },
  { field: 'industryName', title: '行业名称' }
]
export function presentationColumns(rows: Record<string, any>[], columns?: { field: string; title: string }[]) {
  const keys = new Set(rows.flatMap(row => Object.keys(row)))
  const masterFields = fields.filter(column => keys.has(column.field))
  if (masterFields.length) {
    const allowed = new Map(masterFields.map(column => [column.field, column.title]))
    return columns === undefined ? masterFields : columns.filter(column => allowed.has(column.field)).map(column => ({ ...column, title: allowed.get(column.field)! }))
  }
  return columns ?? Array.from(keys).map(field => ({ field, title: field }))
}
