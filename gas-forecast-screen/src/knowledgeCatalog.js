import {canReadKnowledge} from './knowledgeDemo.js'
// Cache searchable text once per data change. Only the requested page reaches the DOM.
export function createKnowledgeCatalog(documents) {
  const counts = new Map(), byId = new Map()
  const entries = documents.map(document => {
    counts.set(document.category, (counts.get(document.category) || 0) + 1)
    byId.set(document.id, document)
    return {
      document,
      readable: canReadKnowledge(document),
      text: [document.title, document.summary, document.searchText||'', ...(document.sections || []).map(section => `${section.heading} ${section.text}`)].join(' ').toLocaleLowerCase()
    }
  }).sort((a, b) => String(b.document.updatedAt || '').localeCompare(String(a.document.updatedAt || '')) || String(a.document.id).localeCompare(String(b.document.id)))
  return { entries, counts, byId, total: documents.length }
}
export function knowledgePage(catalog, { query = '', category = '全部资料', readability = '', page = 1, pageSize = 20, order = 'updated' } = {}) {
  const size = [20, 50, 100].includes(Number(pageSize)) ? Number(pageSize) : 20
  const term = query.trim().toLocaleLowerCase()
  const matches = catalog.entries.filter(entry =>
    (category === '全部资料' || entry.document.category === category) &&
    (!readability || (readability === 'readable' ? entry.readable : !entry.readable)) &&
    (!term || entry.text.includes(term)))
  if (order === 'title') matches.sort((a, b) => String(a.document.title).localeCompare(String(b.document.title), 'zh-CN') || String(a.document.id).localeCompare(String(b.document.id)))
  const total = matches.length, pages = Math.max(1, Math.ceil(total / size))
  const current = Math.max(1, Math.min(pages, Math.floor(Number(page)) || 1)), offset = (current - 1) * size
  return { documents: matches.slice(offset, offset + size).map(entry => entry.document), total, pages, page: current, pageSize: size, start: total ? offset + 1 : 0, end: Math.min(total, offset + size) }
}
