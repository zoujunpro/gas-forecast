import { describe, expect, it } from 'vitest'
import { Document, House } from '@element-plus/icons-vue'
import { menuIconOptions, resolveMenuIcon } from './menuIcons'

describe('menu icons', () => {
  it('provides unique values for the menu icon picker', () => {
    const values = menuIconOptions.map((option) => option.value)
    expect(new Set(values).size).toBe(values.length)
  })

  it('resolves configured icons and falls back for unknown names', () => {
    expect(resolveMenuIcon('House')).toBe(House)
    expect(resolveMenuIcon('NotAnIcon')).toBe(Document)
    expect(resolveMenuIcon()).toBe(Document)
  })
})
