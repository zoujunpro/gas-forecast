import test from 'node:test'
import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import {completedForecastConversation,completedForecastSessionId,reconcileCompletedForecastAverage} from '../src/forecastConversation.js'
const records=JSON.parse(readFileSync(new URL('../public/forecast-details.json',import.meta.url)))
const config=JSON.parse(readFileSync(new URL('../public/workbuddy.json',import.meta.url)))
const record=records.find(r=>r.scene==='短期客户'),scene=config.scenes.find(s=>s.id===record.scene)
test('completed conversation reports the same total, peak, dates and model metrics as its saved forecast',()=>{
 const session=completedForecastConversation(record,scene,1795140000000)
 assert.equal(session.id,completedForecastSessionId)
 assert.deepEqual(session.messages.map(m=>m.role),['user','assistant','assistant'])
 assert.equal(session.result.total,'139.75');assert.equal(session.result.peak,15.37);assert.equal(session.result.peakLabel,'2026-11-29')
 assert.equal(session.result.predictionRecord.models.length,12)
 assert.match(session.messages.at(-1).text,/139.75.*万方/);assert.match(session.messages.at(-1).text,/13.98/);assert.match(session.messages.at(-1).text,/15.37/);assert.match(session.messages.at(-1).text,/1.01%/)
 assert.deepEqual(session.result.rows,record.rows)
 assert.deepEqual(session.parameters['短期客户'].customerIds,['configured-0'])
})
test('saved conversations preserve source limitations and isolate changes from the original forecast',()=>{
 const original=JSON.stringify(record),session=completedForecastConversation(record,scene)
 assert.equal(session.simulated,true);assert.equal(session.result.simulated,true);assert.equal(session.result.modelExecuted,false)
 assert.equal(session.result.calculationMethod,'saved-forecast-record')
 assert.equal(session.result.predictionRecord.createdAt,record.createdAt)
 assert.equal(session.result.predictionRecord.method,record.method)
 assert.deepEqual(session.result.predictionRecord.notes,record.notes)
 session.result.rows[0].value=999;session.result.predictionRecord.models[0].name='changed'
 assert.equal(JSON.stringify(record),original)
 assert.throws(()=>completedForecastConversation({...record,rows:[]},scene),/有效结果/)
})

test('early cached example average is reconciled without replacing user messages, drafts, titles or record data',()=>{
 const session=completedForecastConversation(record,scene)
 session.title='我的预测';session.input='继续分析峰值';session.result.average='13.97'
 session.messages.at(-1).text=session.messages.at(-1).text.replace('日均 13.98','日均 13.97')
 const originalRecord=JSON.stringify(session.result.predictionRecord),userText=session.messages[0].text
 assert.equal(reconcileCompletedForecastAverage(session),true)
 assert.equal(session.result.average,'13.98');assert.match(session.messages.at(-1).text,/日均 13.98/)
 assert.equal(session.title,'我的预测');assert.equal(session.input,'继续分析峰值');assert.equal(session.messages[0].text,userText)
 assert.equal(JSON.stringify(session.result.predictionRecord),originalRecord)
 assert.equal(reconcileCompletedForecastAverage(session),false)
 const custom={...session,id:'other-conversation'};assert.equal(reconcileCompletedForecastAverage(custom),false)
})
