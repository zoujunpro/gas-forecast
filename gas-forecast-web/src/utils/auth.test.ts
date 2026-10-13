import { beforeEach, describe, expect, it, vi } from 'vitest'
import { collectManagementMenuPaths, filterMenusByWorkspace, getProfile, hasProfile, type AuthMenu } from './auth'

const menus: AuthMenu[] = [
  { id: 1, name: '工作台首页', path: '/' },
  { id: 14, name: 'AI智能问答', path: '/ai-assistant' },
  {
    id: 10,
    name: '智能体中心',
    children: [{ id: 11, parentId: 10, name: '冬季保供预测', path: '/agent/winter-supply' }]
  },
  {
    id: 60,
    name: '模型管理',
    path: '/model',
    children: [
      {
        id: 61,
        parentId: 60,
        name: '模型列表',
        path: '/model/list',
        component: 'views/model/ModelConfigManagement.vue'
      }
    ]
  }
]

describe('workspace menu filtering', () => {
  it('keeps AI Q&A and agents in the AI center only', () => {
    expect(filterMenusByWorkspace(menus, 'ai').map((menu) => menu.name)).toEqual([
      '工作台首页',
      'AI智能问答',
      '智能体中心'
    ])
  })

  it('accepts directory menus whose path is null', () => {
    const menusWithNullPath: AuthMenu[] = [
      {
        id: 10,
        name: '智能体中心',
        path: null,
        children: [{ id: 11, name: '冬季保供预测', path: '/agent/winter-supply' }]
      }
    ]
    expect(filterMenusByWorkspace(menusWithNullPath, 'ai')).toHaveLength(1)
    expect(filterMenusByWorkspace(menusWithNullPath, 'management')).toHaveLength(0)
  })

  it('removes AI center entries from management and its default paths', () => {
    expect(filterMenusByWorkspace(menus, 'management').map((menu) => menu.path)).toEqual(['/', '/model'])
    expect(collectManagementMenuPaths(menus)).toEqual(['/model/list'])
  })
})

describe('cached profile validation', () => {
  beforeEach(() => {
    const values = new Map<string, string>()
    vi.stubGlobal('localStorage', {
      getItem: (key: string) => values.get(key) ?? null,
      setItem: (key: string, value: string) => values.set(key, value),
      removeItem: (key: string) => values.delete(key)
    })
  })

  it.each(['null', '[]', '"old-profile"', '{broken'])('recovers from invalid cached data: %s', (cached) => {
    localStorage.setItem('gas_forecast_profile', cached)
    expect(getProfile()).toEqual({})
    expect(hasProfile()).toBe(false)
  })
})
