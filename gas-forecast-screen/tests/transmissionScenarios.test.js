import test from 'node:test'
import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import {createGraphRepository} from '../src/graphRepository.js'
import {mergeGraphDatasets} from '../src/graphDatasets.js'
const read=name=>JSON.parse(readFileSync(new URL('../public/'+name,import.meta.url),'utf8'))
const original=read('customer-graph.json'),overlay=read('transmission-scenarios.json'),merged=mergeGraphDatasets(original,overlay),repository=createGraphRepository(merged)
const rawNodes=new Map(original.nodes.map(node=>[node.id,node]))
test('97 source rows produce scoped scenarios with complete provenance, not recorded events',()=>{
 assert.equal(overlay.rules.length,97);assert.equal(overlay.scenarios.length,873)
 assert.equal(overlay.sourceAudit.summaryDeclaredTotal,102)
 assert.deepEqual(new Set(overlay.rules.map(rule=>rule.sequence)),new Set(Array.from({length:97},(_,index)=>index+1)))
 assert.equal(overlay.scenarios.filter(scenario=>scenario.variant==='base').length,291)
 for(const node of overlay.nodes){assert.equal(node.simulated,true);assert.ok(node.sourceRow>1);assert.ok(node.properties.数据类型);assert.ok(node.sourceFile)}
 for(const edge of overlay.edges){assert.equal(edge.simulated,true);assert.ok(edge.sourceRow>1)}
 assert.match(overlay.rules.find(rule=>rule.sequence===39).mechanisms.join(''),/碳成本增加/)
})
test('merging never overwrites original nodes, edges or actual sales',()=>{
 assert.deepEqual(merged.nodes.slice(0,original.nodes.length),original.nodes)
 assert.deepEqual(merged.edges.slice(0,original.edges.length),original.edges)
 assert.equal(repository.nodes.length,9233);assert.equal(repository.edges.length,24188)
 const base=createGraphRepository(original)
 for(const node of original.nodes.filter(node=>node.type==='企业'))assert.deepEqual(repository.sales(node.id),base.sales(node.id))
 assert.throws(()=>mergeGraphDatasets(original,{...overlay,nodes:[original.nodes[0]]}),/冲突/)
})
test('customer attachments match BOTH sector and province and carry scenario status',()=>{
 const scenarios=new Map(overlay.scenarios.map(scenario=>[scenario.id,scenario]))
 for(const edge of overlay.edges){
  const target=rawNodes.get(edge.target)
  if(target?.type!=='企业')continue
  const scope=scenarios.get(edge.scenarioId)
  assert.equal(target.properties.所在地,scope.region)
  assert.ok(scope.industries.includes(target.properties.行业类别))
  assert.equal(edge.properties.验证状态,'待业务验证')
 }
})
test('LNG transport scenarios do not assign truck or shipping demand to CNG stations',()=>{
 const lngOnly=new Set([47,74,75,76,80,81,82,83,84,85,86])
 for(const rule of overlay.rules.filter(rule=>lngOnly.has(rule.sequence)))assert.equal(rule.industries.includes('CNG'),false)
 const cng=original.nodes.find(node=>node.name==='CNG加气站')
 assert.ok(repository.scenariosFor(cng.id).length>10)
 for(const scenario of repository.scenariosFor(cng.id))assert.equal(lngOnly.has(overlay.rules.find(rule=>rule.id===scenario.ruleId).sequence),false)
})
test('paths stay within ONE selected region and severity even through shared original targets',()=>{
 const cng=original.nodes.find(node=>node.name==='CNG加气站'),candidates=repository.scenariosFor(cng.id)
 assert.equal(candidates[0].variant,'base')
 const path=repository.paths(cng.id),scenario=candidates[0]
 assert.equal(path.scenarioId,scenario.id)
 assert.equal(path.nodes.filter(node=>node.type==='需求变化').length,1)
 assert.ok(path.nodes.filter(node=>node.type==='需求变化').every(node=>node.properties.适用行业==='CNG'))
 assert.ok(repository.paths(cng.id,{depth:2}).nodes.every(node=>node.type!=='需求变化'))
 assert.ok(path.nodes.some(node=>node.id===cng.id));assert.ok(path.edges.length>3)
 for(const node of path.nodes.filter(node=>node.simulated))assert.equal(node.scenarioId,scenario.id)
 for(const edge of path.edges)assert.equal(edge.scenarioId,scenario.id)
 const different=candidates.find(item=>item.variant==='stress'&&item.region!==scenario.region)
 assert.equal(repository.paths(cng.id,{scenarioId:different.id}).scenarioId,different.id)
 const noPath=original.nodes.find(node=>node.type==='行业工厂'&&node.name==='自备气源用户')
 assert.equal(repository.scenariosFor(noPath.id).length,0)
 assert.equal(repository.paths(noPath.id,{scenarioId:scenario.id}).edges.length,0)
})
test('every generated scenario has complete endpoints, a reachable outcome and acyclic causal flow',()=>{
 const ids=new Set(repository.nodes.map(node=>node.id)),edgeById=new Map(overlay.edges.map(edge=>[edge.id,edge]))
 assert.equal(ids.size,repository.nodes.length)
 assert.equal(new Set(repository.edges.map(edge=>edge.id)).size,repository.edges.length)
 for(const scenario of overlay.scenarios){
  const edges=scenario.edgeIds.map(id=>edgeById.get(id)),reachable=new Set([scenario.eventId]),inDegree=new Map(scenario.nodeIds.map(id=>[id,0]))
  for(const edge of edges){assert.ok(ids.has(edge.source)&&ids.has(edge.target));inDegree.set(edge.target,inDegree.get(edge.target)+1)}
  let queue=[...inDegree].filter(([,count])=>count===0).map(([id])=>id),visited=0
  while(queue.length){const id=queue.shift();visited++;for(const edge of edges.filter(edge=>edge.source===id)){reachable.add(edge.target);inDegree.set(edge.target,inDegree.get(edge.target)-1);if(inDegree.get(edge.target)===0)queue.push(edge.target)}}
  assert.equal(visited,inDegree.size)
  assert.ok(scenario.nodeIds.some(id=>reachable.has(id)&&repository.node(id).type==='需求变化'))
  assert.ok(repository.paths(scenario.eventId).nodes.length<=40)
 }
})
