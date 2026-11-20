import test from 'node:test'
import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import {forecastTrendOption} from '../src/forecastTrendChart.js'
import {periodLabels} from '../src/forecastTimeline.js'
const records=JSON.parse(readFileSync(new URL('../public/forecast-details.json',import.meta.url),'utf8'))
test('presentation keeps every model, the solid/dashed join and missing future actuals intact',()=>{
 for(const record of records){
  const config=forecastTrendOption(record),history=record.backtest.length
  assert.equal(config.legend.data.length,record.models.length+1)
  assert.deepEqual(config.xAxis.data,[...periodLabels(record,record.backtest,true),...periodLabels(record,record.rows)])
  for(const model of record.models){
   const solid=config.series.find(series=>series.id===model.name+'-backtest'),dashed=config.series.find(series=>series.id===model.name+'-forecast')
   assert.deepEqual(solid.data.slice(0,history),record.backtest.map(row=>row[model.name]??null))
   assert.equal(dashed.data[history-1],solid.data[history-1])
   assert.deepEqual(dashed.data.slice(history),model.forecast)
   assert.equal(solid.lineStyle.type,'solid');assert.equal(dashed.lineStyle.type,'dashed')
  }
  assert.deepEqual(config.series.find(series=>series.name==='实际销量').data.slice(history),record.rows.map(row=>row.actual??null))
 }
})
test('model focus changes appearance without dropping models or changing predictions',()=>{
 const record=records[0],focused=forecastTrendOption(record),comparison=forecastTrendOption(record,{focusSelected:false})
 assert.deepEqual(focused.series.map(series=>series.data),comparison.series.map(series=>series.data))
 assert.deepEqual(new Set(focused.series.map(series=>series.name)),new Set(['实际销量',...record.models.map(model=>model.name)]))
 const adopted=focused.series.find(series=>series.name===record.selectedModel)
 assert.equal(adopted.lineStyle.opacity,1)
 assert.ok(focused.series.filter(series=>series.name!==record.selectedModel&&series.name!=='实际销量').every(series=>series.lineStyle.opacity<1))
})
