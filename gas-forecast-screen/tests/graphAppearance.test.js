import test from 'node:test'
import assert from 'node:assert/strict'
import {nodeLabelStyle} from '../src/graphAppearance.js'
import {mergeNeighborhoods} from '../src/graphExpansion.js'
test('local graph labels show business names and remain readable after zoom',()=>{
  const node={data:{name:'万众吉利',type:'企业',diameter:44,overview:false}}
  assert.equal(nodeLabelStyle(node,.3).label,false)
  assert.equal(nodeLabelStyle(node,1).label,true)
  assert.equal(nodeLabelStyle(node,2).labelText,'万众吉利')
})
test('dense overview reveals names progressively',()=>{
  const node={data:{name:'月销量',diameter:24,overview:true}}
  assert.equal(nodeLabelStyle(node,1).label,false)
  assert.equal(nodeLabelStyle(node,2).label,true)
})
test('incremental expansion retains prior IDs, direction and parallel relations without duplicates',()=>{
  const a={id:'a'},b={id:'b'},c={id:'c'}
  const ab={id:'ab',source:'a',target:'b'},ba={id:'ba',source:'b',target:'a'},bc={id:'bc',source:'b',target:'c'}
  const result=mergeNeighborhoods([{nodes:[a,b],edges:[ab,ba]},{nodes:[b,c,a],edges:[bc,ab]}])
  assert.deepEqual(result.nodes,[a,b,c])
  assert.deepEqual(result.edges,[ab,ba,bc])
})
