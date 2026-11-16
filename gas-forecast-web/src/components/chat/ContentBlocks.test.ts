import { describe, it, expect, vi } from 'vitest'
import { defineComponent } from 'vue'
import { postJson } from '@/api/management'
import { flushPromises, shallowMount } from '@vue/test-utils'
import ContentBlocks from './ContentBlocks.vue'
import TableBlock from './TableBlock.vue'
vi.mock('@/api/management', () => ({ postJson: vi.fn() }))

describe('ContentBlocks', () => {
  it('preserves compact task presentation for the agent page', () => {
    const wrapper = shallowMount(ContentBlocks, { props: { compactTasks: true, blocks: [
      { type: 'TASK_CARD', data: { taskNo: 'TASK-1' } }
    ] } })
    expect(wrapper.findComponent({ name: 'AiExecutionTask' }).props('compact')).toBe(true)
  })
  it('renders all tables and charts in order, skipping legacy execution envelopes', () => {
    const wrapper = shallowMount(ContentBlocks, { props: { blocks: [
      { type: 'TABLE', data: { rows: [{ value: 1 }] } },
      { type: 'CHART', data: { rows: [{ value: 2 }] } },
      { type: 'TABLE', data: { rows: [] } },
      { type: 'TABLE', data: { resultType: 'TASK', data: {} } }
    ] } })
    expect(wrapper.findAllComponents({ name: 'TableBlock' })).toHaveLength(2)
    expect(wrapper.findAllComponents({ name: 'ChartBlock' })).toHaveLength(1)
    expect(wrapper.html().indexOf('table-block-stub')).toBeLessThan(wrapper.html().indexOf('chart-block-stub'))
  })
  it('rejects executable result links', () => {
    const wrapper = shallowMount(ContentBlocks, { props: { blocks: [
      { type: 'RESULT_LINK', data: { url: 'javascript:alert(1)' } },
      { type: 'RESULT_LINK', data: { url: '//example.com' } },
      { type: 'RESULT_LINK', data: { url: '/tasks/1', title: '查看任务' } }
    ] } })
    expect(wrapper.findAll('a')).toHaveLength(1)
    expect(wrapper.get('a').attributes('href')).toBe('/tasks/1')
  })
})

const tableStub = defineComponent({ name: 'ElTable', props: ['data'], template: '<div />' })
const pageStub = defineComponent({ name: 'ElPagination', emits: ['current-change'], template: '<div />' })
const tableOptions = { directives: { loading: () => {} }, stubs: { ElTable: tableStub, ElPagination: pageStub, ElAlert: true, ElTableColumn: true } }
describe('TableBlock', () => {
  it('shows total and loaded counts and slices complete inline rows', async () => {
    const wrapper = shallowMount(TableBlock, { props: { data: {
      rows: Array.from({ length: 35 }, (_, value) => ({ value })), total: 35, pageSize: 20
    } }, global: tableOptions })
    expect(wrapper.text()).toContain('共 35 条，当前加载 35 条')
    expect(wrapper.findComponent(tableStub).props('data')).toHaveLength(20)
    wrapper.findComponent(pageStub).vm.$emit('current-change', 2)
    await flushPromises()
    expect(wrapper.findComponent(tableStub).props('data')).toHaveLength(15)
  })
})


describe('remote paging', () => {
  it('uses bound filters and keeps the previous page on failure', async () => {
    vi.mocked(postJson).mockResolvedValueOnce({ data: { records: [{ value: 2 }], total: 350 } })
    const wrapper = shallowMount(TableBlock, { props: { data: {
      rows: [{ value: 1 }], total: 350, pageSize: 20,
      pagination: { resource: 'customers', filters: { regionCode: 'R-1' } }
    } }, global: tableOptions })
    wrapper.findComponent(pageStub).vm.$emit('current-change', 2)
    await flushPromises()
    expect(postJson).toHaveBeenCalledWith('/base-customer/agentPage', { regionCode: 'R-1', page: 2, size: 20 })
    expect(wrapper.findComponent(tableStub).props('data')).toEqual([{ value: 2 }])
    vi.mocked(postJson).mockRejectedValueOnce(new Error('无权限'))
    wrapper.findComponent(pageStub).vm.$emit('current-change', 3)
    await flushPromises()
    expect(wrapper.findComponent(tableStub).props('data')).toEqual([{ value: 2 }])
  })
})
