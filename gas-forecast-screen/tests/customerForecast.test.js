import test from 'node:test'
import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import {createCustomerOptions,createCustomerForecast,manualForecastPrompt,customerForecastCsv} from '../src/customerForecast.js'
const source=JSON.parse(readFileSync(new URL('../public/customer-graph.json',import.meta.url)))
const config=JSON.parse(readFileSync(new URL('../public/workbuddy.json',import.meta.url)))
const scene=config.scenes.find(s=>s.id==='短期客户')
test('customer choices use original terminal customer IDs and direct sales, with no parent double counting or partial November baseline',()=>{
 const customers=createCustomerOptions(source,scene)
 assert.equal(customers.length,197)
 assert.equal(new Set(customers.map(c=>c.id)).size,197)
 const original=customers.filter(c=>!c.id.startsWith('configured-'))
 assert.equal(original.length,194)
 for(const customer of original){
  assert.equal(source.nodes.find(n=>n.id===customer.id).properties.层级,'三级')
  const readings=customer.readingIds.map(id=>source.nodes.find(n=>n.id===id))
  assert.equal(readings.length,3)
  assert.deepEqual(readings.map(n=>n.properties.月份),['2025-10','2025-09','2025-08'])
  const expected=readings.reduce((sum,r)=>sum+Number(r.properties.数值),0)/92/10000
  assert.equal(customer.base,expected)
  assert.equal(customer.dataThrough,'2025-10-31')
  assert.equal(customer.sourceSha256,source.sourceSha256)
 }
})
test('missing and invalid readings remain unavailable, zero is valid and unrelated reading edges are excluded',()=>{
 const enterprise=(id)=>({id,name:id,type:'企业',properties:{层级:'三级'}})
 const reading=(id,value,month='2025-10',unit='立方米')=>({id,type:'指标值',properties:{数值:value,月份:month,单位:unit}})
 const graph={period:source.period,nodes:[enterprise('empty'),enterprise('zero'),enterprise('valid'),reading('blank',''),reading('bad','NaN'),reading('negative','-2'),reading('partial',999,'2025-11'),reading('wrong-unit',999,'2025-10','吨'),reading('wrong-month',999,'2025-99'),reading('unrelated',999),reading('r0',0),reading('r1',310000)],edges:['blank','bad','negative','partial','wrong-unit','wrong-month'].map(id=>({source:id,target:'empty',type:'关联'})).concat([{source:'unrelated',target:'empty',type:'销售'},{source:'r0',target:'zero',type:'关联'},{source:'r1',target:'valid',type:'关联'},{source:'r1',target:'valid',type:'关联'}])}
 const customers=createCustomerOptions(graph,{objects:[]})
 assert.equal(customers[0].base,null);assert.equal(customers[1].base,0);assert.equal(customers[2].base,1)
 const result=createCustomerForecast({customers,factors:[1,2],days:2,today:'2026-12-31'})
 assert.equal(result.successful,2);assert.equal(result.unavailable,1)
 assert.equal(result.customers[0].total,null);assert.deepEqual(result.customers[0].rows,[])
 assert.equal(result.customers[1].total,0)
 assert.deepEqual(result.rows,[{label:'2027-01-01',value:1},{label:'2027-01-02',value:2}])
 const failed=createCustomerForecast({customers:[customers[0]],factors:[1],days:1,today:'2026-11-20'})
 assert.equal(failed.total,null);assert.equal(failed.peak,null);assert.equal(failed.status,'unavailable')
})
test('batch demand sums each customer for the same date, retains separate provenance and exports all customer rows',()=>{
 const customers=createCustomerOptions(source,scene).filter(c=>['万众吉利','万华化学'].includes(c.name))
 const result=createCustomerForecast({customers:[...customers,customers[0]],factors:config.factors,days:10,today:'2026-11-20'})
 assert.equal(result.customers.length,2)
 for(const [i,row] of result.rows.entries())assert.equal(row.value,Number(result.customers.reduce((sum,c)=>sum+c.rows[i].value,0).toFixed(2)))
 assert.equal(result.total,result.rows.reduce((sum,row)=>sum+row.value,0).toFixed(2))
 assert.equal(result.peak,Math.max(...result.rows.map(row=>row.value)))
 assert.equal(result.modelExecuted,false);assert.equal(result.simulated,true)
 assert.ok(result.customers.every(c=>c.sourceSha256===source.sourceSha256&&c.readingIds.length===3))
 const csv=customerForecastCsv(result)
 assert.equal(csv.split('\r\n').length,31)
 assert.match(csv,/ENT_万众吉利/);assert.match(csv,/TOTAL/);assert.match(csv,/2026-11-30/)
 const escaped=customerForecastCsv({...result,customers:[{...result.customers[0],name:'客户,"A"'}]})
 assert.match(escaped,/"客户,""A"""/)
})
test('manual requests carry selected scope, horizon, industry and supply into the conversation',()=>{
 assert.match(manualForecastPrompt({scene:'短期客户',customers:[{name:'客户甲'},{name:'客户乙'}],period:'未来10天'}),/2 个客户.*未来10天.*客户甲、客户乙/)
 assert.match(manualForecastPrompt({scene:'月度销量',object:'江苏',period:'未来6个月',industry:'工业用气'}),/江苏.*未来6个月.*工业用气/)
 assert.match(manualForecastPrompt({scene:'冬季保供',object:'华北地区',period:'未来三旬',supply:0}),/0 万方/)
 assert.throws(()=>createCustomerForecast({customers:[],factors:[1],days:1,today:'2026-11-20'}),/请选择/)
})
