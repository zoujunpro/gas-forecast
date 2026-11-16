import { describe, it, expect } from 'vitest'
import { presentationColumns } from './presentationFields'
describe('master data presentation columns', () => {
  it('hides codes in historical tables and preserves names', () => {
    expect(presentationColumns([{ id: 1, customerCode: 'C1', customerName: '客户', regionCode: 'R1', regionName: '地区' }])).toEqual([
      { field: 'customerName', title: '客户名称' }, { field: 'regionName', title: '地区名称' }
    ])
  })
  it('filters explicit legacy columns without reintroducing hidden columns', () => {
    expect(presentationColumns([{ id: 1, industryName: '行业' }], [{ field: 'id', title: '编号' }, { field: 'industryName', title: 'industryName' }])).toEqual([{ field: 'industryName', title: '行业名称' }])
    expect(presentationColumns([{ customerName: '客户' }], [])).toEqual([])
  })
})
