import test from 'node:test'
import assert from 'node:assert/strict'
import {explorationTrail} from '../src/graphNavigation.js'

const edges=[{source:'enterprise',target:'gas'},{source:'gas',target:'factory'}]
test('independent search or catalog selection resets the trail even for connected objects',()=>{
  assert.deepEqual(explorationTrail(['enterprise'],'unrelated',{edges}),['unrelated'])
  assert.deepEqual(explorationTrail(['enterprise'],'gas',{edges}),['gas'])
})
test('graph exploration only appends real hops and preserves intermediary nodes',()=>{
  assert.deepEqual(explorationTrail(['enterprise'],'gas',{extend:true,edges}),['enterprise','gas'])
  assert.deepEqual(explorationTrail(['enterprise'],'factory',{extend:true,edges}),['enterprise','gas','factory'])
  assert.deepEqual(explorationTrail(['enterprise'],'unrelated',{extend:true,edges}),['unrelated'])
})
test('going back truncates the trail instead of reordering history or creating a cycle',()=>{
  assert.deepEqual(explorationTrail(['enterprise','gas','factory'],'gas',{extend:true,edges}),['enterprise','gas'])
  assert.deepEqual(explorationTrail(['enterprise'],'enterprise',{extend:true,edges}),['enterprise'])
})
