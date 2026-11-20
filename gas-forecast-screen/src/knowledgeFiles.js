import {localId} from './localId.js'
const openDatabase = () => new Promise((resolve, reject) => {
  const request = indexedDB.open('gas-knowledge-files', 1)
  request.onupgradeneeded = () => request.result.createObjectStore('documents', { keyPath: 'id' })
  request.onsuccess = () => resolve(request.result)
  request.onerror = () => reject(request.error)
})
export async function knowledgeFiles(action, value) {
  const db = await openDatabase()
  try { return await new Promise((resolve, reject) => {
    const tx = db.transaction('documents', ['list', 'get'].includes(action) ? 'readonly' : 'readwrite')
    const store = tx.objectStore('documents')
    const request = action === 'list' ? store.getAll() : action === 'get' ? store.get(value) : store.put(value)
    let result
    request.onsuccess = () => { result = request.result }
    tx.oncomplete = () => resolve(result)
    tx.onerror = () => reject(tx.error)
    tx.onabort = () => reject(tx.error)
  }) } finally { db.close() }
}
export async function readKnowledgeFile(file, category = '上传资料') {
  if (!/\.(txt|md|markdown|csv|json|pdf|doc|docx|xlsx|pptx)$/i.test(file.name)) throw new Error(file.name + ' 文件格式暂不支持')
  if (file.size > 10 * 1024 * 1024) throw new Error(file.name + ' 超过10MB限制')
  const readable = /\.(txt|md|markdown|csv|json)$/i.test(file.name)
  let text = readable ? await file.text() : ''
  if (readable && !text.trim()) throw new Error(file.name + ' 内容为空')
  if (readable && text.length > 300000) throw new Error(file.name + ' 文本过长，请拆分为较小文件')
  return { id: localId(), title: file.name, category,
    summary: readable ? text.replace(/\s+/g, ' ').slice(0, 110) : '文件已保存，需后端解析后才能用于正文检索与问答。',
    version: '1.0', updatedAt: new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai' }).format(new Date()),
    source: '用户上传', sourceNote: '保存在当前浏览器本地，未上传服务器；文件内容仅作为参考资料。',
    status: readable ? '可阅读' : '待解析', readable, file,
    sections: readable ? [{ heading: '文件正文', text }] : [] }
}

// Return source passages only; this is local text matching, not model inference.
export function referenceExcerpt(content, query) {
  const terms = [...new Set((query.match(/[a-zA-Z0-9]+|[\u4e00-\u9fff]{2}/g) || []).map(s => s.toLowerCase()))]
  const paragraphs = content.split(/\n+/).filter(s => s.trim()).flatMap(s => s.match(/.{1,700}/gu) || [])
  const ranked = paragraphs.map((text, index) => ({ text, index, score: terms.reduce((n, term) => n + (text.toLowerCase().includes(term) ? 1 : 0), 0) }))
  const matches = ranked.filter(p => p.score > 0).sort((a, b) => b.score - a.score || a.index - b.index)
  return (matches.length ? matches : ranked).slice(0, 3).sort((a,b) => a.index - b.index).map(p => p.text).join('\n\n')
}
