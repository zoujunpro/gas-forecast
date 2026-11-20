const rounded=value=>Number(value.toFixed(2))
export function createCustomerOptions(graph,scene) {
 const configured=(scene?.objects||[]).map((name,index)=>({id:'configured-'+index,name,region:'',industry:'',base:Number(scene.base)*(1+index*.07),baselineSource:'预置业务参数',simulated:true}))
 if(!graph)return configured
 const readings=new Map(graph.nodes.filter(node=>node.type==='指标值').map(node=>[node.id,node])),sales=new Map()
 for(const edge of graph.edges){const value=readings.get(edge.source);if(edge.type==='关联'&&value){const rows=sales.get(edge.target)||[];rows.push(value);sales.set(edge.target,rows)}}
 const partial=graph.period?.match(/(\d{4})-(\d{2})（\d{1,2}月非完整月/),partialMonth=partial?partial[1]+'-'+partial[2]:null
 const imported=graph.nodes.filter(node=>node.type==='企业'&&node.properties.层级==='三级').map(node=>{
  const seen=new Set(),rows=(sales.get(node.id)||[]).filter(value=>{const p=value.properties,month=p.月份;if(!/^\d{4}-\d{2}$/.test(month)||(Number(month.slice(5))<1||Number(month.slice(5))>12)||month===partialMonth||p.单位!=='立方米'||String(p.数值??'').trim()===''||!Number.isFinite(Number(p.数值))||Number(p.数值)<0||seen.has(value.id))return false;seen.add(value.id);return true}).sort((a,b)=>b.properties.月份.localeCompare(a.properties.月份)).slice(0,3)
  const days=rows.reduce((sum,row)=>{const [year,month]=row.properties.月份.split('-').map(Number);return sum+new Date(Date.UTC(year,month,0)).getUTCDate()},0)
  const base=days?rows.reduce((sum,row)=>sum+Number(row.properties.数值),0)/days/10000:null
  const months=rows.map(row=>row.properties.月份).sort(),last=months.at(-1),[year,month]=(last||'').split('-').map(Number)
  return {id:node.id,name:node.name,region:node.properties.所在地||'',industry:node.properties.行业类别||'',base,baselineSource:months.length?`近${months.length}个完整月的日均销量（${months[0]} 至 ${last}）`:'缺少完整月销量',dataThrough:last?`${last}-${new Date(Date.UTC(year,month,0)).getUTCDate()}`:undefined,source:graph.source,sourceSha256:graph.sourceSha256,readingIds:rows.map(row=>row.id),simulated:true}
 })
 return [...imported,...configured]
}
export function createCustomerForecast({customers,factors,days,today}) {
 if(!customers.length)throw Error('请选择至少一个客户')
 if(!Number.isInteger(days)||days<1||days>factors.length)throw Error('预测范围无效')
 const labels=Array.from({length:days},(_,index)=>{const date=new Date(today+'T00:00:00Z');date.setUTCDate(date.getUTCDate()+index+1);return date.toISOString().slice(0,10)})
 const unique=[...new Map(customers.map(customer=>[customer.id,customer])).values()]
 const results=unique.map(customer=>{
  const available=customer.base!==null&&customer.base!==undefined&&Number.isFinite(customer.base)&&customer.base>=0
  const rows=available?labels.map((label,index)=>({label,value:rounded(customer.base*Number(factors[index]))})):[]
  return {customerId:customer.id,name:customer.name,region:customer.region,rows,status:available?'success':'unavailable',total:available?rounded(rows.reduce((sum,row)=>sum+row.value,0)):null,peak:available?Math.max(...rows.map(row=>row.value)):null,baselineSource:customer.baselineSource,dataThrough:customer.dataThrough,readingIds:customer.readingIds||[],source:customer.source||'预置业务参数',sourceSha256:customer.sourceSha256,modelExecuted:false,simulated:true}
 })
 const successful=results.filter(customer=>customer.status==='success')
 const rows=successful.length?labels.map((label,index)=>({label,value:rounded(successful.reduce((sum,customer)=>sum+customer.rows[index].value,0))})):[]
 return {customers:results,rows,successful:successful.length,unavailable:results.length-successful.length,total:rows.length?rows.reduce((sum,row)=>sum+row.value,0).toFixed(2):null,peak:rows.length?Math.max(...rows.map(row=>row.value)):null,status:rows.length?'success':'unavailable',simulated:true,modelExecuted:false,calculationMethod:'rules-customer-baseline'}
}
export function manualForecastPrompt({scene,customers=[],object,period,industry,supply}) {
 if(scene==='短期客户')return `请为以下 ${customers.length} 个客户分别预测${period}的日度天然气需求：${customers.map(customer=>customer.name).join('、')}。按客户保留明细，并汇总同一周期的需求。`
 return `请对${object}进行${period}的${scene}预测。${scene==='月度销量'?`行业范围：${industry}。`:''}${scene==='冬季保供'&&supply!==''?`每期可用供气量：${supply} 万方。`:''}`
}
export function customerForecastCsv(result) {
 const escape=value=>'"'+String(value??'').replaceAll('"','""')+'"'
 const rows=[['客户编码','客户名称','地区','状态','预测周期','需求（万方）']]
 for(const customer of result.customers){if(customer.status==='success')for(const row of customer.rows)rows.push([customer.customerId,customer.name,customer.region,'已生成',row.label,row.value]);else rows.push([customer.customerId,customer.name,customer.region,'缺少完整月销量','',''])}
 if(result.customers.length>1)for(const row of result.rows)rows.push(['TOTAL','汇总','','已生成',row.label,row.value])
 return '\ufeff'+rows.map(row=>row.map(escape).join(',')).join('\r\n')
}
