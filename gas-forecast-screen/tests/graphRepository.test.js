import test from 'node:test'
import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import {createGraphRepository} from '../src/graphRepository.js'
import {overviewPositions} from '../src/graphLayout.js'
const raw=JSON.parse(readFileSync(new URL('../public/customer-graph.json',import.meta.url),'utf8'))
const repository=createGraphRepository(raw)
test('overview includes every original node and edge, without aggregation or overlap',()=>{
  const all=repository.overview(),positions=overviewPositions(all.nodes)
  assert.equal(all.nodes.length,raw.nodes.length)
  assert.equal(all.edges.length,raw.edges.length)
  assert.equal(positions.size,raw.nodes.length)
  assert.deepEqual(new Set(all.nodes.map(n=>n.id)),new Set(raw.nodes.map(n=>n.id)))
  const coordinates=new Set()
  for(const node of all.nodes){
    const point=positions.get(node.id)
    assert.ok(point.every(Number.isFinite))
    coordinates.add(point.join(','))
  }
  assert.equal(coordinates.size,all.nodes.length)
  assert.deepEqual(all.edges.map(e=>[e.source,e.target,e.type]),raw.edges.map(e=>[e.source,e.target,e.type]))
})
test('all original objects remain searchable and paginated, including readings',()=>{
  assert.equal(repository.nodes.length,2413)
  assert.equal(repository.edges.length,9218)
  const count=repository.search({type:'指标值',page:9999})
  assert.equal(count.total,2134)
  assert.equal(count.page,count.pages)
  assert.ok(count.nodes.length>0)
  assert.equal(repository.search({query:'万众吉利',type:'企业'}).nodes[0].name,'万众吉利')
  assert.equal(repository.search({query:'不存在的图谱对象'}).total,0)
})
test('company relations retain source direction and do not fabricate an industry or event edge',()=>{
  const company=repository.search({query:'万众吉利',type:'企业'}).nodes[0]
  const view=repository.neighborhood(company.id)
  assert.equal(view.hiddenValues,11)
  assert.ok(view.nodes.every(n=>n.type!=='指标值'))
  assert.equal(view.nodes.some(n=>n.type==='行业'||n.type==='事件'),false)
  const sale=view.edges.find(e=>e.type==='销售')
  assert.equal(sale.source,company.id)
  assert.equal(repository.node(sale.target).name,'管道天然气')
  assert.equal(repository.neighborhood(company.id,{includeValues:true}).nodes.filter(n=>n.type==='指标值').length,11)
  assert.deepEqual(repository.paths(company.id).edges,[])
  const rows=repository.sales(company.id)
  assert.equal(rows.length,11)
  assert.equal(rows[0].properties.月份,'2025-01')
  assert.equal(rows[0].properties.数值,'272274')
})
test('high-degree neighborhoods are bounded without losing subsequent pages',()=>{
  const product=repository.search({query:'管道天然气',type:'品类'}).nodes[0]
  const all=repository.neighborhood(product.id,{limit:9999})
  const ids=new Set()
  for(let offset=0;offset<all.totalNeighbors;offset+=18){
    const page=repository.neighborhood(product.id,{offset,limit:18})
    assert.ok(page.nodes.length<=19)
    page.nodes.filter(n=>n.id!==product.id).forEach(n=>ids.add(n.id))
  }
  assert.equal(ids.size,all.totalNeighbors)
  const schema=repository.schema()
  assert.equal(schema.edges.reduce((sum,e)=>sum+e.count,0),9218)
  assert.equal(schema.nodes.reduce((sum,n)=>sum+n.count,0),2413)
})
test('transmission traversal follows direction, terminates on cycles and excludes unrelated associations',()=>{
  const r=createGraphRepository({nodes:['a','b','c','d'].map(id=>({id,name:id,type:'事件',properties:{}})),edges:[{source:'a',target:'b',type:'触发'},{source:'b',target:'c',type:'传导'},{source:'c',target:'a',type:'影响'},{source:'a',target:'d',type:'关联'}]})
  assert.deepEqual(new Set(r.paths('a').nodes.map(n=>n.id)),new Set(['a','b','c']))
  assert.equal(r.paths('a',{depth:1}).nodes.length,2)
  assert.equal(r.paths('a',{limit:2}).nodes.length,2)
})

test('cached overview layout covers every original ID and rejects stale or invalid coordinates',async()=>{
  const {cachedOverviewPositions}=await import('../src/graphLayout.js')
  const cached=JSON.parse(readFileSync(new URL('../public/customer-graph-layout.json',import.meta.url),'utf8'))
  const positions=cachedOverviewPositions(cached,repository)
  assert.equal(positions.size,repository.nodes.length)
  assert.deepEqual(new Set(positions.keys()),new Set(repository.nodes.map(node=>node.id)))
  assert.equal(cachedOverviewPositions({...cached,sourceSha256:'different-source'},repository),null)
  assert.equal(cachedOverviewPositions({...cached,edgeCount:0},repository),null)
  assert.equal(cachedOverviewPositions({...cached,positions:{...cached.positions,[repository.nodes[0].id]:[NaN,0]}},repository),null)
})
