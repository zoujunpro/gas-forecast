// A saved result can populate a completed conversation without executing a model.
export const completedForecastSessionId='wb-completed-forecast-v1'
export const completedForecastSeedKey='wb-completed-forecast-seeded-v1'

export function completedForecastConversation(record,scene,now=Date.now()) {
 if(!record?.id||!record.rows?.length||record.rows.some(row=>!Number.isFinite(row.value)))throw new Error('预测记录缺少有效结果')
 const predictionRecord=JSON.parse(JSON.stringify(record))
 const totalCents=predictionRecord.rows.reduce((sum,row)=>sum+Math.round(row.value*100),0)
 const total=totalCents/100,average=(Math.round(totalCents/predictionRecord.rows.length)/100).toFixed(2)
 const peak=predictionRecord.rows.reduce((best,row)=>row.value>best.value?row:best)
 const selected=predictionRecord.models?.find(model=>model.name===predictionRecord.selectedModel)
 const metric=selected?.wape===undefined?'':`，记录中的回测 WAPE 为 ${selected.wape.toFixed(2)}%`
 const customerIndex=scene.objects.indexOf(record.object)
 return {
  id:completedForecastSessionId,title:`${record.object} · ${record.horizon}预测结果`,scene:record.scene,updatedAt:now,autoTitle:false,customTitle:true,seeded:true,simulated:true,
  parameters:{[record.scene]:{object:record.object,period:record.horizon,industry:'全部行业',supply:'',...(record.scene==='短期客户'&&customerIndex>=0?{customerIds:['configured-'+customerIndex]}:{})}},
  input:'',knowledge:null,graphContext:'',attachments:[],
  messages:[
   {role:'user',text:`请预测${record.object}${record.horizon}的天然气用气需求，给出每日预测、需求合计和峰值，并整理预测报告。`},
   {role:'assistant',agent:'WorkBuddy 协同助理',text:`已确认预测需求。\n预测对象：${record.object}\n预测范围：${record.rows[0].label} 至 ${record.rows.at(-1).label}\n结果粒度：${record.unit}，单位：${record.volumeUnit}。`},
   {role:'assistant',agent:'短期客户预测',text:`预测结果已返回。\n\n${record.horizon}预计用气 ${total.toFixed(2)} ${record.volumeUnit}，日均 ${average} ${record.volumeUnit}。峰值出现在 ${peak.label}，预计需求 ${peak.value.toFixed(2)} ${record.volumeUnit}。\n\n记录采用模型为 ${record.selectedModel}${metric}。完整预测报告已在下方展开，包含全部模型预测、天气输入、特征分析与回测结果，并支持直接导出。`}
  ],
  result:{object:record.object,period:record.horizon,unit:record.unit,industry:'全部行业',rows:predictionRecord.rows,peak:peak.value,peakLabel:peak.label,total:total.toFixed(2),average,status:'success',predictionRecord,sourceFile:'forecast-details.json',simulated:true,modelExecuted:false,calculationMethod:'saved-forecast-record'}
 }
}

// Update the early saved example's rounded daily average without replacing its conversation.
export function reconcileCompletedForecastAverage(session) {
 const rows=session?.result?.predictionRecord?.rows
 if(session?.id!==completedForecastSessionId||!rows?.length||rows.some(row=>!Number.isFinite(row.value)))return false
 const average=(Math.round(rows.reduce((sum,row)=>sum+Math.round(row.value*100),0)/rows.length)/100).toFixed(2)
 let changed=session.result.average!==average
 session.result.average=average
 for(const message of session.messages||[]){
  if(message.role!=='assistant'||message.agent!=='短期客户预测'||!message.text.startsWith('预测结果已返回。'))continue
  const text=message.text.replace(/日均\s+\d+(?:\.\d+)?\s+万方/,`日均 ${average} 万方`)
  if(text!==message.text){message.text=text;changed=true}
 }
 return changed
}
