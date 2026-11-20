<template>
  <div v-if="record" ref="host" class="fd" :class="{'fd-document':presentation==='conversation'}">
    <div class="fd-header">
      <div class="fd-heading"><div class="fd-title-line"><h2>{{record.object||record.title}}</h2><span class="fd-scene">{{record.scene}}预测</span><span class="fd-status"><i></i>{{record.status}}</span></div><div class="fd-meta"><span>生成于 {{createdTime}}</span><i></i><span>数据截止 {{record.dataThrough||'—'}}</span><i></i><span>{{record.horizon}} · {{record.unit}}</span></div></div>
      <div class="fd-header-actions"><button class="fd-text-button" :aria-expanded="showRecordInfo" @click="showRecordInfo=!showRecordInfo">记录信息<GraphIcon name="chevron"/></button><button class="fd-export" @click="exportCsv"><svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><path d="M12 3v12m-4-4 4 4 4-4M4 15v5h16v-5"/></svg>导出数据</button></div>
    </div>
    <div v-if="showRecordInfo" class="fd-record-info"><dl><dt>数据类型</dt><dd>{{record.simulated?'预置参数样本':'预测记录'}}</dd><dt>生成时间</dt><dd>{{createdTime}}</dd><dt>天气来源</dt><dd>{{workspaceCopy(record.weatherSource)||'未记录'}}</dd><dt>回测说明</dt><dd>{{workspaceCopy(record.method)||'未记录'}}</dd></dl><p v-for="note in record.notes||[]" :key="note">{{workspaceCopy(note)}}</p></div>
    <div class="fd-summary">
      <article class="fd-metric-primary"><span class="fd-metric-label">预测总量<small>{{record.volumeUnit}}</small></span><b>{{total}}</b><p class="fd-metric-range"><template v-if="futureLabels.length"><span>{{futureLabels[0]}}</span> <i>至</i> <span>{{futureLabels.at(-1)}}</span></template><template v-else>暂无预测周期</template></p></article>
      <article><span class="fd-metric-label">峰值需求<small>{{record.volumeUnit}}</small></span><b>{{peak}}</b><p>峰值周期 {{peakLabel}}</p></article>
      <article><span class="fd-metric-label">{{averageLabel}}<small>{{record.volumeUnit}}</small></span><b>{{average}}</b><p>覆盖 {{record.rows.length}} 个预测周期</p></article>
      <article class="fd-model-card"><span class="fd-metric-label">采用模型<span v-if="record.models?.length" class="fd-model-count">{{record.models.length}} 个候选</span></span><b>{{record.selectedModel||'未记录'}}</b><p v-if="selectedModel?.wape!==undefined">回测 WAPE <strong>{{format(selectedModel.wape)}}%</strong></p><p v-else>暂无回测评估记录</p></article>
    </div>
    <CustomerBatchSummary v-if="record.customers?.length" :customers="record.customers"/><section :id="idPrefix+'-panel-result'" class="fd-trend-panel">
      <div class="fd-section-heading"><div class="fd-section-title"><h3><GraphIcon name="需求变化"/>需求趋势</h3><span class="fd-section-meta">{{record.volumeUnit}} · {{record.unit}}</span></div><div class="fd-trend-controls"><div v-if="record.models?.length>1" class="fd-chart-mode"><button :class="{chosen:focusSelected}" :aria-pressed="focusSelected" @click="focusSelected=true">突出采用模型</button><button :class="{chosen:!focusSelected}" :aria-pressed="!focusSelected" @click="focusSelected=false">全部对比</button></div><div class="fd-chart-tools"><button v-for="tool in chartTools" :key="tool.label" :title="tool.label" :aria-label="tool.label" @click="operate(tool.action)"><GraphIcon :name="tool.icon"/></button></div></div></div>
      <div class="fd-chart-caption"><span><i class="fd-solid"></i>回测</span><span><i class="fd-dashed"></i>预测</span><small>{{record.models?.length?record.models.length+' 个模型 · 点击图例筛选':'按周期展示计算需求'}}</small></div>
      <div ref="forecastChart" class="fd-chart" role="img" aria-label="实际销量及所有候选模型的回测与预测趋势"></div>
    </section>
    <section :id="idPrefix+'-panel-features'" class="fd-feature-panels">
      <div class="fd-panel fd-weather-panel"><div class="fd-section-heading"><h3><GraphIcon name="sun"/>天气输入</h3><span class="fd-section-meta">{{record.weather?.length||0}} 天</span></div><div v-if="record.weather?.length" class="fd-weather-list"><div v-for="w in record.weather" :key="w.date"><span class="fd-weather-date">{{shortDate(w.date)}}</span><GraphIcon :name="weatherIcon(w.condition)"/><span class="fd-weather-condition">{{w.condition}}</span><span class="fd-weather-temp"><b>{{w.min}}°</b><span>—</span><strong>{{w.max}}°</strong></span></div></div><p v-else class="fd-empty">暂无天气输入</p><div v-if="record.weather?.length" class="fd-card-footer">输入范围 {{record.weather[0].date}} 至 {{record.weather.at(-1).date}}</div></div>
      <div class="fd-panel fd-feature-panel"><div class="fd-section-heading"><h3><GraphIcon name="因素指标"/>特征重要性</h3><span class="fd-info-label" title="各类特征在本次记录中的相对重要程度，不代表销量增幅、准确率或因果贡献。">相对占比 ⓘ</span></div><div v-if="record.features?.length" class="fd-feature-analysis"><div ref="featureChart" class="fd-feature-chart" role="img" aria-label="模型使用特征的相对重要性占比"></div><div class="fd-feature-list"><div v-for="(feature,index) in record.features" :key="feature.name"><i :style="{background:featureColors[index%featureColors.length]}"></i><div><b>{{feature.name}}</b><p>{{feature.description}}</p></div><strong>{{featurePercent(feature)}}%</strong></div></div></div><p v-else class="fd-empty">暂无特征明细</p></div>
    </section>
    <section :id="idPrefix+'-panel-models'" class="fd-panel fd-model-panel"><div class="fd-section-heading"><div class="fd-section-title"><h3><GraphIcon name="驱动因素"/>回测评估</h3><span class="fd-section-meta">{{record.backtest?.length||0}} 期样本 · {{record.models?.length||0}} 个模型</span></div><span class="fd-lower-better">误差越低越好</span></div><div v-if="selectedModel" class="fd-model-metrics"><span>{{record.selectedModel}}</span><div v-for="metric in metricDefinitions" :key="metric.key"><small>{{metric.name}}</small><b>{{format(selectedModel[metric.key],metric.key==='wape'?2:3)}}</b><em>{{metric.key==='wape'?'%':record.volumeUnit}}</em></div></div><div v-if="record.models?.length" ref="metricsChart" class="fd-metrics-chart" role="img" aria-label="全部候选模型的MAE、RMSE及WAPE对比"></div><p v-else class="fd-empty">暂无候选模型回测记录</p><p v-if="record.models?.length" class="fd-subtle-note">MAE、RMSE 使用左轴，WAPE 使用右轴。图例可筛选指标。</p></section>
    <section :id="idPrefix+'-panel-data'" class="fd-panel fd-data-panel"><div class="fd-section-heading"><h3><GraphIcon name="传导规则"/>预测明细</h3><span class="fd-section-meta">{{record.rows.length}} 期 · {{record.volumeUnit}}</span></div><div class="fd-table-wrap"><table><thead><tr><th>预测周期</th><th class="fd-adopted">采用结果</th><th>实际销量</th><th>下界</th><th>上界</th><th v-for="model in record.models||[]" :key="model.name" :class="{'fd-adopted':model.name===record.selectedModel}">{{model.name}}</th></tr></thead><tbody><tr v-for="(row,index) in record.rows" :key="index"><td>{{futureLabels[index]}}</td><td class="fd-adopted">{{format(row.value)}}</td><td>{{format(row.actual)}}</td><td>{{format(row.lower)}}</td><td>{{format(row.upper)}}</td><td v-for="model in record.models||[]" :key="model.name" :class="{'fd-adopted':model.name===record.selectedModel}">{{format(model.forecast?.[index])}}</td></tr></tbody></table></div><details v-if="record.backtest?.length" class="fd-backtest-details" open><summary>回测明细 <span>{{record.backtest.length}} 期</span></summary><div class="fd-table-wrap"><table><thead><tr><th>回测周期</th><th class="fd-adopted">实际销量</th><th v-for="model in record.models||[]" :key="model.name">{{model.name}}</th></tr></thead><tbody><tr v-for="(row,index) in record.backtest" :key="index"><td>{{historyLabels[index]}}</td><td class="fd-adopted">{{format(row.actual)}}</td><td v-for="model in record.models||[]" :key="model.name">{{format(row[model.name])}}</td></tr></tbody></table></div></details><details v-if="record.rows.some(row=>row.lower!==undefined)" class="fd-data-notes"><summary>区间说明</summary><p>{{record.simulated?'上下界为预置参数区间，覆盖率尚未校准。':'上下界定义以模型记录为准。'}}</p></details></section>
  </div>
</template>
<script setup>
import {ref,computed,onMounted,onBeforeUnmount,nextTick,watch} from 'vue'
import * as echarts from 'echarts'
import GraphIcon from './GraphIcon.vue'
import CustomerBatchSummary from './CustomerBatchSummary.vue'
import {customerForecastCsv} from './customerForecast.js'
import {workspaceCopy} from './workspacePresentation.js'
import {modelMetricsOption} from './modelMetricsChart.js'
import {forecastTrendOption} from './forecastTrendChart.js'
import {periodLabels} from './forecastTimeline.js'
const props=defineProps({record:Object,idPrefix:{type:String,default:'fd'},presentation:{type:String,default:'page'}})
const record=computed(()=>props.record),host=ref(),forecastChart=ref(),metricsChart=ref(),featureChart=ref(),focusSelected=ref(true),showRecordInfo=ref(false)
const featureColors=['#009273','#5a8fd6','#a68ac7','#d6ab67','#90a5b6'],metricDefinitions=[{key:'mae',name:'MAE'},{key:'rmse',name:'RMSE'},{key:'wape',name:'WAPE'}]
const chartTools=[{label:'放大',icon:'zoomIn',action:'in'},{label:'缩小',icon:'zoomOut',action:'out'},{label:'复位',icon:'reset',action:'reset'}]
const historyLabels=computed(()=>periodLabels(record.value,record.value.backtest||[],true)),futureLabels=computed(()=>periodLabels(record.value,record.value.rows||[]))
const selectedModel=computed(()=>record.value.models?.find(model=>model.name===record.value.selectedModel))
const totalValue=computed(()=>record.value.rows.reduce((sum,row)=>sum+Number(row.value),0)),total=computed(()=>format(totalValue.value)),average=computed(()=>record.value.rows.length?format(totalValue.value/record.value.rows.length):'—')
const peakIndex=computed(()=>record.value.rows.reduce((best,row,index,rows)=>Number(row.value)>Number(rows[best]?.value??-Infinity)?index:best,0)),peak=computed(()=>format(record.value.rows[peakIndex.value]?.value)),peakLabel=computed(()=>futureLabels.value[peakIndex.value]||'—')
const rangeLabel=computed(()=>futureLabels.value.length?`${futureLabels.value[0]} 至 ${futureLabels.value.at(-1)}`:'暂无预测周期'),averageLabel=computed(()=>({日度:'日均需求',旬度:'旬均需求',月度:'月均需求'}[record.value.unit]||'期均需求'))
const createdTime=computed(()=>{const date=new Date(record.value.createdAt);if(!Number.isFinite(date.getTime()))return record.value.createdAt||'—';return new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit'}).format(date)})
const shortDate=value=>/^\d{4}-\d{2}-\d{2}$/.test(value)?value.slice(5).replace('-','/'):value
const weatherIcon=condition=>/雨|雪/.test(condition)?'rain':/云|阴/.test(condition)?'cloud':'sun'
function format(value,digits=2){return value===null||value===undefined||value===''||!Number.isFinite(Number(value))?'—':Number(value).toLocaleString('zh-CN',{minimumFractionDigits:digits,maximumFractionDigits:digits})}
function featurePercent(feature){const sum=record.value.features.reduce((total,item)=>total+Number(item.importance||0),0);return sum?(Number(feature.importance||0)/sum*100).toFixed(0):'0'}
let charts={},observer,disposed=false,revision=0,resizeFrame
function disposeCharts(){Object.values(charts).forEach(chart=>chart.dispose());charts={}}
function updateForecast(){const chart=charts.forecast;if(!chart)return;const config=forecastTrendOption(record.value,{focusSelected:focusSelected.value,width:chart.getWidth()}),current=chart.getOption()||{};if(current.dataZoom?.length){config.dataZoom[0].start=current.dataZoom[0].start;config.dataZoom[0].end=current.dataZoom[0].end}if(current.legend?.[0]?.selected)config.legend.selected=current.legend[0].selected;chart.setOption(config,{notMerge:true})}
async function ensureCharts(){
 const ticket=++revision;await nextTick();if(disposed||ticket!==revision||!record.value)return
 for(const [key,element] of Object.entries({forecast:forecastChart.value,features:featureChart.value,metrics:metricsChart.value})){
 if(!element||!element.clientWidth)continue
 if(charts[key]){charts[key].resize();continue}
 if(key==='metrics'&&!record.value.models?.length||key==='features'&&!record.value.features?.length)continue
 const chart=echarts.init(element);charts[key]=chart
 if(key==='forecast')updateForecast()
 if(key==='metrics')chart.setOption(modelMetricsOption(record.value,element.clientWidth))
 if(key==='features')chart.setOption({animationDuration:300,color:featureColors,tooltip:{trigger:'item',confine:true,renderMode:'richText',textStyle:{fontSize:12,color:'#333333'},formatter:p=>`${p.name}：${p.percent}%\n${record.value.features.find(feature=>feature.name===p.name)?.description||''}`},title:{text:'特征构成',subtext:`${record.value.features.length} 类特征`,left:'center',top:'40%',textStyle:{fontSize:15,fontWeight:600,color:'#292929'},subtextStyle:{fontSize:12,color:'#686868'}},series:[{type:'pie',radius:['60%','80%'],center:['50%','50%'],padAngle:3,itemStyle:{borderRadius:4,borderColor:'#fff',borderWidth:2},label:{show:false},emphasis:{scaleSize:5},data:record.value.features.map(feature=>({name:feature.name,value:feature.importance}))}]})
 }
}
function operate(action){const chart=charts.forecast;if(!chart)return;if(action==='reset'){chart.dispatchAction({type:'dataZoom',start:0,end:100});return}const zoom=chart.getOption().dataZoom?.[0]||{start:0,end:100},center=(zoom.start+zoom.end)/2,width=Math.min(100,Math.max(5,(zoom.end-zoom.start)*(action==='in'?.7:1.4))),start=Math.max(0,Math.min(100-width,center-width/2));chart.dispatchAction({type:'dataZoom',start,end:start+width})}
function exportCsv(){const r=record.value,escape=value=>'"'+String(value??'').replaceAll('"','""')+'"',lines=[['预测周期',`采用结果（${r.volumeUnit}）`,'实际销量','下界','上界',...(r.models||[]).map(model=>model.name)],...r.rows.map((row,index)=>[futureLabels.value[index],row.value,row.actual,row.lower,row.upper,...(r.models||[]).map(model=>model.forecast?.[index])])],url=URL.createObjectURL(new Blob([r.customers?customerForecastCsv(r):'\ufeff'+lines.map(line=>line.map(escape).join(',')).join('\r\n')],{type:'text/csv;charset=utf-8'})),anchor=document.createElement('a');anchor.href=url;anchor.download=(r.object||'预测结果')+'-预测.csv';anchor.click();URL.revokeObjectURL(url)}
watch(focusSelected,updateForecast)
watch(()=>props.record,async()=>{revision++;disposeCharts();showRecordInfo.value=false;await ensureCharts()})
onMounted(async()=>{await ensureCharts();if(disposed)return;observer=new ResizeObserver(()=>{cancelAnimationFrame(resizeFrame);resizeFrame=requestAnimationFrame(()=>{if(disposed)return;Object.values(charts).forEach(chart=>chart.resize());const chart=charts.forecast;if(chart&&chart.getOption()?.legend?.[0]?.type!==(chart.getWidth()<600?'scroll':'plain'))updateForecast()})});if(host.value)observer.observe(host.value)})
onBeforeUnmount(()=>{disposed=true;revision++;observer?.disconnect();cancelAnimationFrame(resizeFrame);disposeCharts()})
</script>
<style scoped>
.fd {
  --fd-text: var(--wb-ui-text);
  --fd-secondary: var(--wb-ui-secondary);
  --fd-muted: var(--wb-ui-muted);
  --fd-border: var(--wb-ui-border);
  --fd-surface: var(--wb-ui-surface);
  --fd-accent: var(--wb-ui-primary);
  color: var(--fd-text);
  font-family:var(--wb-font-family);
  font-size:var(--wb-font-control);
  line-height: 1.6;
  padding-bottom: 24px;
  -webkit-font-smoothing: antialiased;
}
.fd button,.fd input { font: inherit; }
.fd button { cursor: pointer; box-shadow: none; transition: background-color .16s, border-color .16s, color .16s; }
.fd button::after { display: none; }
.fd button:focus-visible { outline: 2px solid var(--fd-accent); outline-offset: 3px; }
.fd-header { display: flex; align-items: center; justify-content: space-between; gap: 24px; margin: 0 0 24px; }
.fd-heading { min-width: 0; }
.fd-title-line { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.fd-header h2 { margin: 0; color: var(--wb-ui-text); font-size: 26px; line-height: 1.45; font-weight: 650; letter-spacing: -.5px; }
.fd-scene { padding: 3px 9px; border: 1px solid var(--wb-ui-border); border-radius: 6px; color: var(--wb-ui-secondary); background: var(--wb-ui-surface); font-size:var(--wb-font-meta); white-space: nowrap; }
.fd-status { display: inline-flex; align-items: center; gap: 6px; color: var(--wb-ui-success); font-size:var(--wb-font-meta); white-space: nowrap; }
.fd-status i { width: 6px; height: 6px; border-radius: 50%; background: var(--wb-ui-success); }
.fd-meta { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin-top: 10px; color: var(--fd-muted); font-size:var(--wb-font-meta); }
.fd-meta>i { width: 3px; height: 3px; border-radius: 50%; background: var(--wb-ui-surface); }
.fd-header-actions { display: flex; align-items: center; gap: 12px; flex-shrink: 0; }
.fd-text-button { display: inline-flex; align-items: center; gap: 5px; padding: 9px 12px; border: 0; border-radius: 8px; background: transparent; color: var(--wb-ui-secondary); }
.fd-text-button:hover { background: var(--wb-ui-selection); color: var(--wb-ui-text); }
.fd-text-button svg { width: 13px; height: 13px; transform: rotate(90deg); transition: transform .16s; }
.fd-text-button[aria-expanded=true] svg { transform: rotate(-90deg); }
.fd-export { display: inline-flex; align-items: center; gap: 8px; padding: 9px 18px; min-height: 40px; border: 1px solid var(--wb-ui-primary); border-radius: 999px; background: var(--wb-ui-primary); color: var(--wb-ui-on-primary); font-weight: 500; }
.fd-export:hover { background: var(--wb-ui-primary); border-color: var(--wb-ui-primary); }
.fd-export:active { background: var(--wb-ui-primary); }
.fd-record-info { margin: -6px 0 20px; padding: 16px 20px; border: 1px solid var(--fd-border); border-radius: 12px; background: var(--wb-ui-surface); color: var(--fd-secondary); font-size:var(--wb-font-meta); line-height: 1.8; }
.fd-record-info dl { display: grid; grid-template-columns: 72px minmax(0,1fr); gap: 7px 16px; margin: 0 0 8px; }
.fd-record-info dt { color: var(--fd-muted); }
.fd-record-info dd { margin: 0; color: var(--wb-ui-text); }
.fd-record-info p { margin: 4px 0; }
.fd-summary { display: grid; grid-template-columns: repeat(4,minmax(0,1fr)); gap: 16px; margin-bottom: 24px; }
.fd-summary article { min-width: 0; padding: 20px 22px; border: 1px solid var(--fd-border); border-radius: 14px; background: var(--wb-ui-surface); }
.fd-summary article.fd-metric-primary { border-color: var(--wb-ui-border); background: var(--wb-ui-surface); }
.fd-metric-label { display: flex; align-items: center; justify-content: space-between; gap: 7px; color: var(--wb-ui-secondary); font-size:var(--wb-font-control); }
.fd-metric-label>small { color: var(--wb-ui-muted); font-size:var(--wb-font-meta); }
.fd-summary article>b { display: block; margin: 12px 0 10px; color: var(--wb-ui-text); font-size: 36px; line-height: 1.2; font-weight: 600; letter-spacing: -.9px; font-variant-numeric: tabular-nums; }
.fd-summary article.fd-metric-primary>b { color: var(--wb-ui-text); }
.fd-summary article>p { margin: 0; color: var(--wb-ui-secondary); font-size:var(--wb-font-meta); line-height: 1.7; }
.fd-summary article.fd-model-card>b { margin: 12px 0 13px; color: var(--wb-ui-text); font-size: 22px; line-height: 1.5; font-weight: 600; letter-spacing: -.3px; overflow-wrap: anywhere; }
.fd-model-card p>strong { margin-left: 5px; color: var(--wb-ui-text); font-size:var(--wb-font-control); font-weight: 600; font-variant-numeric: tabular-nums; }
.fd-model-count { padding: 3px 7px; border-radius: 5px; background: var(--wb-ui-surface); color: var(--wb-ui-secondary); font-size:var(--wb-font-meta); white-space: nowrap; }
.fd>section+section { margin-top: 24px; }
.fd-panel,.fd-trend-panel { padding: 24px; border: 1px solid var(--fd-border); border-radius: 14px; background: var(--wb-ui-surface-raised); box-shadow: 0 2px 6px #20212403; }
.fd-section-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.fd-section-title { display: flex; align-items: center; gap: 13px; flex-wrap: wrap; }
.fd h3 { display: flex; align-items: center; gap: 9px; margin: 0; color: var(--wb-ui-text); font-size: 16px; line-height: 1.5; font-weight: 600; }
.fd h3>svg { width: 18px; height: 18px; color: var(--wb-ui-text); flex-shrink: 0; }
.fd-section-meta { color: var(--wb-ui-secondary); font-size:var(--wb-font-meta); white-space: nowrap; }
.fd-trend-controls { display: flex; align-items: center; gap: 14px; }
.fd-chart-mode { display: flex; align-items: center; gap: 3px; padding: 3px; border: 1px solid var(--wb-ui-border); border-radius: 9px; background: var(--wb-ui-surface); }
.fd-chart-mode button { padding: 6px 11px; border: 1px solid transparent; border-radius: 6px; background: transparent; color: var(--wb-ui-secondary); font-size:var(--wb-font-meta); white-space: nowrap; }
.fd-chart-mode button:hover { color: var(--wb-ui-text); background: var(--wb-ui-selection); }
.fd-chart-mode button.chosen { border-color: var(--wb-ui-border); background: var(--wb-ui-surface-raised); color: var(--wb-ui-text); box-shadow: 0 1px 3px #1a1a1a08; font-weight: 500; }
.fd-chart-tools { display: flex; align-items: center; gap: 5px; }
.fd-chart-tools button { display: grid; place-items: center; width: 34px; height: 34px; padding: 0; border: 1px solid var(--fd-border); border-radius: 8px; background: var(--wb-ui-surface-raised); color: var(--wb-ui-secondary); }
.fd-chart-tools button:hover { border-color: var(--wb-ui-border); background: var(--wb-ui-selection); color: var(--wb-ui-text); }
.fd-chart-tools svg { width: 17px; height: 17px; }
.fd-chart-caption { display: flex; align-items: center; gap: 16px; margin-top: 15px; color: var(--wb-ui-secondary); font-size:var(--wb-font-meta); }
.fd-chart-caption>span { display: flex; align-items: center; gap: 6px; }
.fd-chart-caption i { display: inline-block; width: 19px; border-top: 1.5px solid var(--wb-ui-border-strong); }
.fd-chart-caption i.fd-dashed { border-top-style: dashed; }
.fd-chart-caption>small { margin-left: auto; color: var(--wb-ui-secondary); font-size:var(--wb-font-meta); }
.fd-chart { width: 100%; height: clamp(390px,48vh,500px); margin-top: 8px; }
.fd-feature-panels { display: grid; grid-template-columns: minmax(270px,.85fr) minmax(0,1.5fr); gap: 24px; }
.fd-weather-list { margin-top: 15px; }
.fd-weather-list>div { display: grid; grid-template-columns: 65px 23px 1fr auto; align-items: center; gap: 10px; padding: 13px 0; border-bottom: 1px solid var(--wb-ui-border); color: var(--wb-ui-secondary); font-size:var(--wb-font-control); }
.fd-weather-list>div:last-child { border-bottom: 0; }
.fd-weather-list svg { width: 20px; height: 20px; color: var(--wb-ui-muted); }
.fd-weather-date { color: var(--wb-ui-text); font-size:var(--wb-font-control); font-variant-numeric: tabular-nums; }
.fd-weather-condition { color: var(--wb-ui-secondary); font-size:var(--wb-font-meta); }
.fd-weather-temp { display: flex; align-items: center; gap: 10px; font-variant-numeric: tabular-nums; white-space: nowrap; }
.fd-weather-temp b,.fd-weather-temp strong { color: var(--wb-ui-text); font-size:var(--wb-font-body); font-weight: 500; }
.fd-weather-temp strong { color: var(--wb-ui-text); }
.fd-weather-temp>span { color: var(--wb-ui-muted); font-size:var(--wb-font-meta); }
.fd-card-footer { margin-top: 12px; padding-top: 14px; border-top: 1px solid var(--wb-ui-border); color: var(--wb-ui-muted); font-size:var(--wb-font-meta); line-height: 1.7; }
.fd-info-label { color: var(--wb-ui-secondary); font-size:var(--wb-font-meta); cursor: help; white-space: nowrap; }
.fd-feature-analysis { display: grid; grid-template-columns: minmax(180px,.9fr) minmax(0,1.1fr); align-items: center; gap: 20px; min-height: 300px; }
.fd-feature-chart { width: 100%; height: 290px; }
.fd-feature-list>div { display: flex; align-items: center; gap: 10px; padding: 15px 0; border-bottom: 1px solid var(--wb-ui-border); }
.fd-feature-list>div:last-child { border: 0; }
.fd-feature-list i { width: 7px; height: 7px; border-radius: 50%; flex-shrink: 0; }
.fd-feature-list>div>div { flex: 1; min-width: 0; }
.fd-feature-list b { display: block; color: var(--wb-ui-text); font-size:var(--wb-font-control); font-weight: 500; }
.fd-feature-list p { margin: 5px 0 0; color: var(--wb-ui-muted); font-size:var(--wb-font-meta); line-height: 1.7; }
.fd-feature-list strong { color: var(--wb-ui-text); font-size: 18px; font-weight: 600; font-variant-numeric: tabular-nums; white-space: nowrap; }
.fd-lower-better { padding: 4px 9px; border: 1px solid var(--wb-ui-border); border-radius: 6px; background: var(--wb-ui-surface); color: var(--wb-ui-secondary); font-size:var(--wb-font-meta); white-space: nowrap; }
.fd-model-metrics { display: flex; align-items: center; gap: 26px; flex-wrap: wrap; margin: 20px 0 10px; padding: 16px 20px; border: 1px solid var(--wb-ui-border); border-radius: 10px; background: var(--wb-ui-surface); }
.fd-model-metrics>span { padding-right: 16px; border-right: 1px solid var(--wb-ui-border); color: var(--wb-ui-text); font-size:var(--wb-font-control); font-weight: 500; }
.fd-model-metrics>div { display: flex; align-items: baseline; gap: 8px; }
.fd-model-metrics small { color: var(--wb-ui-secondary); font-size:var(--wb-font-meta); }
.fd-model-metrics b { color: var(--wb-ui-text); font-size: 20px; font-weight: 600; font-variant-numeric: tabular-nums; }
.fd-model-metrics em { color: var(--wb-ui-muted); font-size:var(--wb-font-meta); font-style: normal; }
.fd-metrics-chart { width: 100%; height: 400px; }
.fd-subtle-note { margin: 8px 0 0; color: var(--wb-ui-secondary); font-size:var(--wb-font-meta); line-height: 1.7; }
.fd-table-wrap { max-width: 100%; overflow: auto; scrollbar-width: thin; scrollbar-color: var(--wb-ui-border-strong) transparent; margin-top: 20px; }
.fd table { width: 100%; margin: 0; border-collapse: collapse; font-size:var(--wb-font-meta); white-space: nowrap; font-variant-numeric: tabular-nums; }
.fd th { padding: 13px 16px; border-bottom: 1px solid var(--wb-ui-border); background: var(--wb-ui-surface); color: var(--wb-ui-secondary); text-align: right; font-weight: 500; }
.fd td { padding: 14px 16px; border-bottom: 1px solid var(--wb-ui-border); color: var(--wb-ui-secondary); text-align: right; }
.fd th:first-child,.fd td:first-child { position: sticky; left: 0; z-index: 1; background: var(--wb-ui-surface-raised); color: var(--wb-ui-text); text-align: left; box-shadow: 1px 0 #e6e9ed; }
.fd th:first-child { background: var(--wb-ui-surface); }
.fd td.fd-adopted,.fd th.fd-adopted { background: var(--wb-ui-surface); color: var(--wb-ui-secondary); font-weight: 550; }
.fd tbody tr:hover td { background: var(--wb-ui-selection); }
.fd tbody tr:hover td.fd-adopted { background: var(--wb-ui-selection); }
.fd-backtest-details { margin-top: 26px; color: var(--wb-ui-secondary); font-size:var(--wb-font-control); }
.fd-backtest-details summary { cursor: pointer; font-weight: 500; }
.fd-backtest-details summary>span { margin-left: 7px; color: var(--wb-ui-muted); font-size:var(--wb-font-meta); font-weight: 400; }
.fd-data-notes { margin-top: 20px; color: var(--wb-ui-secondary); font-size:var(--wb-font-meta); }
.fd-data-notes summary { cursor: pointer; }
.fd-data-notes p { margin: 8px 0 0; color: var(--wb-ui-secondary); font-size:var(--wb-font-meta); line-height: 1.8; }
.fd-empty { padding: 45px 15px; color: var(--wb-ui-muted); font-size:var(--wb-font-control); text-align: center; }
@media (max-width: 1250px) {
  .fd-header { align-items: flex-start; }
  .fd-header h2 { font-size: 24px; }
  .fd-summary { gap: 12px; }
  .fd-summary article { padding: 18px; }
  .fd-summary article>b { font-size: 31px; }
  .fd-summary article.fd-model-card>b { font-size: 20px; }
  .fd-model-count { display: none; }
  .fd-feature-panels { grid-template-columns: minmax(245px,.85fr) minmax(0,1.2fr); }
  .fd-feature-analysis { grid-template-columns: 1fr; }
  .fd-feature-chart { height: 230px; }
  .fd-feature-list { padding: 0 10px; }
  .fd-feature-list>div { padding: 10px 0; }
  .fd-trend-controls { gap: 8px; }
}
@media (max-width: 760px) {
  .fd { font-size:var(--wb-font-meta); }
  .fd-header { flex-direction: column; gap: 16px; margin-bottom: 20px; }
  .fd-header-actions { width: 100%; justify-content: space-between; }
  .fd-header h2 { font-size: 22px; }
  .fd-title-line { gap: 8px; }
  .fd-scene,.fd-status { font-size:var(--wb-font-meta); }
  .fd-meta { gap: 8px; font-size:var(--wb-font-meta); }
  .fd-summary { grid-template-columns: repeat(2,minmax(0,1fr)); gap: 10px; margin-bottom: 18px; }
  .fd-summary article { padding: 15px; border-radius: 11px; }
  .fd-summary article>b { font-size: 29px; }
  .fd-summary article.fd-model-card>b { font-size: 18px; }
  .fd-metric-label { font-size:var(--wb-font-meta); }
  .fd-metric-label>small,.fd-summary article>p { font-size:var(--wb-font-meta); overflow-wrap: anywhere; }
  .fd>section+section { margin-top: 16px; }
  .fd-panel,.fd-trend-panel { padding: 18px 14px; border-radius: 12px; }
  .fd h3 { font-size: 15px; }
  .fd-section-heading { gap: 12px; flex-wrap: wrap; }
  .fd-section-title { gap: 10px; }
  .fd-trend-controls { width: 100%; justify-content: space-between; gap: 6px; }
  .fd-chart-mode button { padding: 6px 8px; font-size:var(--wb-font-meta); }
  .fd-chart-tools { gap: 3px; }
  .fd-chart-tools button { width: 30px; height: 30px; }
  .fd-chart-caption>small { display: none; }
  .fd-chart { height: 390px; }
  .fd-section-meta { font-size:var(--wb-font-meta); }
  .fd-feature-panels { grid-template-columns: 1fr; gap: 16px; }
  .fd-feature-analysis { grid-template-columns: 1fr; gap: 0; }
  .fd-feature-chart { height: 230px; }
  .fd-feature-list { padding: 0; }
  .fd-feature-list>div { padding: 12px 0; }
  .fd-model-metrics { gap: 15px; padding: 14px; }
  .fd-model-metrics>span { width: 100%; border: 0; padding: 0; }
  .fd-metrics-chart { height: 380px; }
}
@media (prefers-reduced-motion: reduce) { .fd button,.fd-text-button svg { transition: none; } }

/* The conversation presentation keeps all report sections in a continuous reading flow. */
.fd.fd-document .fd-header{margin-bottom:20px;gap:18px}
.fd.fd-document .fd-header h2{font-size:23px;font-weight:600;letter-spacing:-.4px}
.fd.fd-document .fd-title-line{gap:10px}
.fd.fd-document .fd-meta{gap:10px;margin-top:8px;font-size:12px}
.fd.fd-document .fd-header-actions{gap:6px}
.fd.fd-document .fd-export{min-height:34px;padding:7px 12px;font-size:12px}
.fd.fd-document .fd-text-button{padding:7px 9px;font-size:12px}
.fd.fd-document .fd-summary{gap:0;margin-bottom:24px;padding:18px 0;border-radius:10px;background:var(--wb-ui-surface)}
.fd.fd-document .fd-summary article,.fd.fd-document .fd-summary article.fd-metric-primary{padding:0 20px;border:0;border-right:1px solid var(--wb-ui-border);border-radius:0;background:transparent}
.fd.fd-document .fd-summary article:last-child{border-right:0}
.fd.fd-document .fd-metric-label{justify-content:flex-start;gap:7px;font-size:12px;color:var(--wb-ui-secondary)}
.fd.fd-document .fd-metric-label>small{font-size:12px;color:var(--wb-ui-muted)}
.fd.fd-document .fd-summary article>b{margin:8px 0 6px;font-size:29px;font-weight:600;letter-spacing:-.5px}
.fd.fd-document .fd-summary article.fd-model-card>b{margin:9px 0 7px;font-size:18px;line-height:1.45;letter-spacing:-.3px}
.fd.fd-document .fd-summary article>p{font-size:12px;color:var(--wb-ui-muted)}
.fd.fd-document .fd-metric-range{display:flex;flex-wrap:wrap;align-items:center;gap:3px 4px}
.fd.fd-document .fd-metric-range span{white-space:nowrap}
.fd .fd-metric-range i{font-style:normal}
.fd.fd-document .fd-model-count{display:inline;padding:0;background:transparent;font-size:12px;color:var(--wb-ui-muted)}
.fd.fd-document .fd-panel,.fd.fd-document .fd-trend-panel{padding:24px 0 0;border:0;border-top:1px solid var(--wb-ui-border);border-radius:0;background:transparent;box-shadow:none}
.fd.fd-document>section+section{margin-top:26px}
.fd.fd-document .fd-section-heading{gap:12px}
.fd.fd-document h3{font-size:15px;font-weight:600;gap:7px}
.fd.fd-document h3>svg{width:17px;height:17px}
.fd.fd-document .fd-section-meta{font-size:12px}
.fd.fd-document .fd-chart-mode{border:0;border-radius:6px;padding:2px;background:var(--wb-ui-surface)}
.fd.fd-document .fd-chart-mode button{padding:5px 8px;font-size:12px}
.fd.fd-document .fd-chart-tools{gap:4px}
.fd.fd-document .fd-chart-tools button{width:29px;height:29px;border-color:transparent;border-radius:6px;background:transparent}
.fd.fd-document .fd-chart-tools button:hover{background:var(--wb-ui-surface)}
.fd.fd-document .fd-chart-tools svg{width:16px;height:16px}
.fd.fd-document .fd-chart-caption{margin-top:12px;font-size:12px}
.fd.fd-document .fd-chart-caption>small{font-size:12px}
.fd.fd-document .fd-chart{height:330px;margin-top:7px}
.fd.fd-document .fd-feature-panels{grid-template-columns:minmax(260px,.85fr) minmax(0,1.7fr);gap:30px;padding-top:24px;border-top:1px solid var(--wb-ui-border)}
.fd.fd-document .fd-feature-panels>.fd-panel{padding:0;border-top:0}
.fd.fd-document .fd-feature-panels>.fd-weather-panel{padding-right:26px;border-right:1px solid var(--wb-ui-border)}
.fd.fd-document .fd-weather-list{margin-top:12px}
.fd.fd-document .fd-weather-list>div{grid-template-columns:49px 18px 1fr auto;gap:8px;padding:8px 0;font-size:12px}
.fd.fd-document .fd-weather-list svg{width:17px;height:17px}
.fd.fd-document .fd-weather-date{font-size:12px}
.fd.fd-document .fd-weather-temp{gap:5px}
.fd.fd-document .fd-weather-temp b,.fd.fd-document .fd-weather-temp strong{font-size:13px}
.fd.fd-document .fd-card-footer{margin-top:10px;padding-top:10px;font-size:12px}
.fd.fd-document .fd-feature-analysis{grid-template-columns:minmax(150px,.85fr) minmax(0,1.2fr);gap:16px;min-height:260px}
.fd.fd-document .fd-feature-chart{height:230px}
.fd.fd-document .fd-feature-list{padding:0}
.fd.fd-document .fd-feature-list>div{padding:10px 0;gap:8px}
.fd.fd-document .fd-feature-list b{font-size:12px}
.fd.fd-document .fd-feature-list p{font-size:12px;margin-top:3px}
.fd.fd-document .fd-feature-list strong{font-size:16px}
.fd.fd-document .fd-lower-better{padding:0;border:0;background:transparent;font-size:12px;color:var(--wb-ui-muted)}
.fd.fd-document .fd-model-metrics{gap:20px;margin:14px 0 6px;padding:12px 14px;border:0;border-radius:8px;background:var(--wb-ui-surface)}
.fd.fd-document .fd-model-metrics b{font-size:18px}
.fd.fd-document .fd-metrics-chart{height:300px}
.fd.fd-document .fd-subtle-note{margin-top:4px;font-size:12px}
.fd.fd-document .fd-table-wrap{margin-top:14px;border:1px solid var(--wb-ui-border);border-radius:9px}
.fd.fd-document table{font-size:12px}
.fd.fd-document th{padding:10px 12px;background:var(--wb-ui-surface);font-size:12px;font-weight:500}
.fd.fd-document td{padding:10px 12px;font-size:12px}
.fd.fd-document tbody tr:last-child td{border-bottom:0}
.fd.fd-document td.fd-adopted,.fd.fd-document th.fd-adopted{font-weight:500}
.fd.fd-document .fd-backtest-details{margin-top:22px}
.fd.fd-document .fd-data-notes{margin-top:14px}
@media(max-width:1200px){
 .fd.fd-document .fd-summary article{padding-left:15px;padding-right:15px}
 .fd.fd-document .fd-summary article>b{font-size:27px}
 .fd.fd-document .fd-feature-panels{grid-template-columns:1fr 1.35fr;gap:22px}
 .fd.fd-document .fd-feature-analysis{grid-template-columns:1fr;gap:0}
 .fd.fd-document .fd-feature-chart{height:190px}
 .fd.fd-document .fd-feature-panels>.fd-weather-panel{padding-right:20px}
}
@media(max-width:760px){
 .fd.fd-document .fd-header{gap:13px;margin-bottom:16px}
 .fd.fd-document .fd-header h2{font-size:21px}
 .fd.fd-document .fd-header-actions{justify-content:flex-start}
 .fd.fd-document .fd-summary{grid-template-columns:repeat(2,minmax(0,1fr));padding:0;margin-bottom:22px}
 .fd.fd-document .fd-summary article,.fd.fd-document .fd-summary article.fd-metric-primary{padding:14px;border:0}
 .fd.fd-document .fd-summary article:nth-child(odd){border-right:1px solid var(--wb-ui-border)}
 .fd.fd-document .fd-summary article:nth-child(n+3){border-top:1px solid var(--wb-ui-border)}
 .fd.fd-document .fd-summary article>b{font-size:26px}
 .fd.fd-document .fd-summary article.fd-model-card>b{font-size:17px}
 .fd.fd-document .fd-summary article>p{font-size:12px}
 .fd.fd-document .fd-panel,.fd.fd-document .fd-trend-panel{padding-top:20px}
 .fd.fd-document .fd-section-heading{align-items:flex-start;gap:10px}
 .fd.fd-document .fd-section-title{gap:6px}
 .fd.fd-document .fd-trend-controls{gap:3px}
 .fd.fd-document .fd-chart-mode button{padding:4px 5px;font-size:12px}
 .fd.fd-document .fd-chart-tools button{width:25px;height:25px}
 .fd.fd-document .fd-chart-tools svg{width:14px;height:14px}
 .fd.fd-document .fd-chart{height:310px}
 .fd.fd-document .fd-feature-panels{grid-template-columns:1fr;gap:22px;padding-top:22px}
 .fd.fd-document .fd-feature-panels>.fd-weather-panel{padding-right:0;border-right:0}
 .fd.fd-document .fd-feature-analysis{grid-template-columns:1fr;gap:0}
 .fd.fd-document .fd-feature-chart{height:210px}
 .fd.fd-document .fd-feature-list>div{padding:11px 0}
 .fd.fd-document .fd-model-metrics{gap:12px}
 .fd.fd-document .fd-model-metrics>span{width:100%;padding:0;border:0}
 .fd.fd-document .fd-metrics-chart{height:300px}
}
</style>
