import test from 'node:test'
import assert from 'node:assert/strict'
import {createKnowledgeCatalog,knowledgePage} from '../src/knowledgeCatalog.js'
const documents=Array.from({length:30007},(_,index)=>({id:String(index).padStart(6,'0'),title:`资料 ${index}`,category:index%2?'行业报告':'业务规则',summary:'统计口径',updatedAt:'2026-11-20',status:index%3?'可阅读':'待解析',sections:index%3?[{heading:'备注',text:index===30005?'FullTextUnique':'天然气销量'}]:[]}))
const catalog=createKnowledgeCatalog(documents)
test('large catalogs return bounded, stable pages with accurate counts and clamped navigation',()=>{
 const first=knowledgePage(catalog),second=knowledgePage(catalog,{page:2}),last=knowledgePage(catalog,{page:1e6})
 assert.equal(first.total,30007);assert.equal(first.documents.length,20);assert.equal(first.pages,1501)
 assert.equal(last.page,1501);assert.equal(last.documents.length,7);assert.equal(last.start,30001);assert.equal(last.end,30007)
 assert.equal(new Set([...first.documents,...second.documents].map(d=>d.id)).size,40)
 assert.deepEqual(knowledgePage(catalog).documents.map(d=>d.id),first.documents.map(d=>d.id))
 assert.equal(knowledgePage(catalog,{pageSize:100}).documents.length,100)
 assert.equal(knowledgePage(catalog,{pageSize:99999,page:-1}).documents.length,20)
 assert.equal(catalog.counts.get('业务规则'),15004)
})
test('search reaches full text beyond the first page, combines filters and handles empty results',()=>{
 const match=knowledgePage(catalog,{query:'fulltextunique',category:'行业报告',readability:'readable',page:999})
 assert.equal(match.total,1);assert.equal(match.documents[0].id,'030005');assert.equal(match.page,1)
 assert.equal(knowledgePage(catalog,{query:'fulltextunique',readability:'pending'}).total,0)
 const empty=knowledgePage(catalog,{query:'not-present'})
 assert.equal(empty.pages,1);assert.equal(empty.page,1);assert.equal(empty.start,0);assert.equal(empty.end,0)
 assert.ok(knowledgePage(catalog,{readability:'pending'}).documents.every(d=>d.sections.length===0))
})
test('updates produce new searchable entries without mutating inputs or losing saved record references',()=>{
 const upload={id:'new',title:'新增资料',category:'上传资料',updatedAt:'2026-11-21',sections:[{heading:'正文',text:'上传后可检索'}]}
 const updated=createKnowledgeCatalog([...documents,upload])
 assert.equal(knowledgePage(updated).documents[0],upload);assert.equal(knowledgePage(updated,{query:'上传后可检索'}).total,1)
 assert.equal(catalog.total,30007);assert.equal(updated.byId.get('new'),upload)
})
