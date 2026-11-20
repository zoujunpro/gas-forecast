import test from 'node:test'
import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import {nodeColor,normalizeColorSettings,typeColor} from '../src/graphColorSettings.js'
const nodes=JSON.parse(readFileSync(new URL('../public/customer-graph.json',import.meta.url),'utf8')).nodes
test('same types share a color and defaults distinguish imported types',()=>{
  const settings=normalizeColorSettings(null),types=new Set(nodes.map(node=>node.type))
  for(const type of types){
    assert.equal(new Set(nodes.filter(node=>node.type===type).map(node=>nodeColor(node,settings))).size,1)
  }
  assert.equal(new Set([...types].map(type=>typeColor(type,settings))).size,types.size)
})
test('old independent colors are ignored while user type colors remain intact',()=>{
  const node=nodes.find(n=>n.type==='企业')
  const settings=normalizeColorSettings({mode:'node',typeColors:{企业:'#FF3300'},nodeColors:{[node.id]:'#7A33FF'}})
  assert.equal(settings.mode,'type')
  assert.equal('nodeColors' in settings,false)
  assert.equal(nodeColor(node,settings),'#ff3300')
  assert.equal(nodeColor({...node,id:'another-company'},settings),'#ff3300')
})
test('only valid type colors persist and reset returns to default type colors',()=>{
  const settings=normalizeColorSettings({mode:'other',typeColors:{企业:'red',品类:'#12FF99'},nodeColors:{good:'#AA33CC'}})
  assert.deepEqual(settings,{mode:'type',typeColors:{品类:'#12ff99'}})
  assert.deepEqual(normalizeColorSettings(JSON.parse(JSON.stringify(settings))),settings)
  assert.deepEqual(normalizeColorSettings(null),{mode:'type',typeColors:{}})
})
