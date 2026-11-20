import test from 'node:test'
import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import {createKnowledgeDemoManifest,hydrateKnowledgeDemo,resolveKnowledgeDocument,canReadKnowledge} from '../src/knowledgeDemo.js'
import {createKnowledgeCatalog,knowledgePage} from '../src/knowledgeCatalog.js'
const manifest=JSON.parse(readFileSync(new URL('../public/knowledge-demo.json',import.meta.url),'utf8'))
const documents=hydrateKnowledgeDemo(manifest)
test('20,000 distinct, deterministic synthetic records span actual template, region and month combinations',()=>{
 assert.deepEqual(manifest,createKnowledgeDemoManifest());assert.equal(documents.length,20000)
 assert.equal(new Set(documents.map(d=>d.id)).size,20000);assert.equal(new Set(documents.map(d=>d.title)).size,20000)
 assert.equal(new Set(documents.map(d=>d.region)).size,25);assert.equal(new Set(documents.map(d=>d.period)).size,40)
 assert.ok(documents.every(d=>d.simulated===true&&d.sourceNote.includes('不是已发生业务记录')))
 assert.ok(documents.every(d=>d.sections===undefined),'body sections should stay out of the full index')
 const original=JSON.parse(readFileSync(new URL('../public/knowledge.json',import.meta.url),'utf8'))
 assert.equal(createKnowledgeCatalog([...original,...documents]).total,20006)
 assert.ok(original.every(d=>!documents.some(demo=>demo.id===d.id)))
})
test('synthetic documents resolve readable content lazily; pending records never become question evidence',()=>{
 const readable=documents.find(canReadKnowledge),pending=documents.find(d=>!canReadKnowledge(d))
 const resolved=resolveKnowledgeDocument(readable)
 assert.equal(resolved.sections.length,4);assert.match(resolved.sections[0].text,new RegExp(readable.region))
 assert.ok(resolved.sections.at(-1).text.includes('业务模板'))
 assert.equal(readable.sections,undefined);assert.equal(resolveKnowledgeDocument(pending),pending);assert.equal(pending.sections,undefined)
 const catalog=createKnowledgeCatalog(documents)
 const fullText=knowledgePage(catalog,{query:'100%-WAPE',readability:'readable'})
 assert.ok(fullText.total>100);assert.ok(fullText.documents.every(d=>resolveKnowledgeDocument(d).sections.some(s=>s.text.includes('100%-WAPE'))))
 assert.equal(knowledgePage(catalog,{query:'100%-WAPE',readability:'pending'}).total,0)
})
