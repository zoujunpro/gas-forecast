import {buildForecastTimeline} from './forecastTimeline.js'
const candidateColors=['#6287ae','#63a697','#b59a6f','#9788ac','#ae7f98','#789cae','#8c9d7d','#ad917f','#729e9f','#9396b4','#a8a06e','#7e91aa']
export function forecastTrendOption(record,{focusSelected=true,width=1200}={}){
 const timeline=buildForecastTimeline(record),selected=record.selectedModel||timeline.modelNames[0],names=timeline.modelNames
 const series=timeline.series.map(item=>{
  const actual=item.name==='实际销量',adopted=item.name===selected,color=actual?'#30363e':adopted?'#009273':candidateColors[names.indexOf(item.name)%candidateColors.length]
  return {...item,showSymbol:false,symbol:'circle',symbolSize:actual?5:4,z:actual?8:adopted?7:2,
   itemStyle:{...item.itemStyle,color,opacity:1},lineStyle:{...item.lineStyle,color,width:adopted?3:actual?2.5:1.5,opacity:actual||adopted||!focusSelected?1:.4},
   emphasis:{focus:'series',lineStyle:{width:3,opacity:1}},blur:{lineStyle:{opacity:.12}},
   ...(item.markLine?{markLine:{...item.markLine,lineStyle:{type:'dashed',color:'#a2b5ac',width:1},label:{formatter:'预测起点',color:'#686868',fontSize:12}}}:{}),
   ...(item.markArea?{markArea:{...item.markArea,itemStyle:{color:'#f7f7f7'}}}:{})}
 })
 return {animationDuration:250,tooltip:{trigger:'axis',confine:true,renderMode:'richText',backgroundColor:'#fff',borderColor:'#e1e5e8',padding:[12,15],textStyle:{color:'#333333',fontSize:12,lineHeight:22},axisPointer:{type:'line',lineStyle:{color:'#a9b5b0',type:'dashed'}},formatter:params=>{
  const values=new Map(params.filter(p=>p.value!==null&&p.value!==undefined&&p.value!=='-').map(p=>[p.seriesName,p.value]))
  const order=['实际销量',selected,...names.filter(name=>name!==selected)],index=params[0]?.dataIndex
  return `${params[0]?.axisValueLabel||''}  ${index<timeline.historyCount?'回测':'预测'}\n`+order.filter(name=>values.has(name)).map(name=>`${name}${name===selected?'（采用）':''}：${values.get(name)} ${record.volumeUnit}`).join('\n')
 }},legend:{type:width<600?'scroll':'plain',pageIconSize:9,pageTextStyle:{color:'#686868',fontSize:12},bottom:3,left:'center',itemWidth:17,itemHeight:7,itemGap:18,icon:'roundRect',textStyle:{fontSize:12,color:'#4d4d4d'},data:[...(series.some(s=>s.name==='实际销量')?['实际销量']:[]),...names]},
 grid:{left:56,right:22,top:28,bottom:width<600?58:85},xAxis:{type:'category',boundaryGap:false,data:timeline.labels,axisLabel:{fontSize:12,color:'#686868',hideOverlap:true,margin:14},axisTick:{show:false},axisLine:{lineStyle:{color:'#e5e8eb'}}},
 yAxis:{type:'value',splitNumber:4,min:value=>Math.max(0,Math.floor((value.min-Math.max(value.max-value.min,1)*.16)*10)/10),max:value=>Math.ceil((value.max+Math.max(value.max-value.min,1)*.15)*10)/10,axisLabel:{fontSize:12,color:'#686868'},splitLine:{lineStyle:{color:'#eceff1',type:'dashed'}}},
 dataZoom:[{type:'inside',xAxisIndex:0,filterMode:'none',start:0,end:100,zoomOnMouseWheel:false,moveOnMouseWheel:true,moveOnMouseMove:true}],series}
}
