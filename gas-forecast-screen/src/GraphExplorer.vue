<template>
  <div class="gx" ref="host">
    <div class="gx-topline"><p>图谱数据 · {{repository?.nodes.length.toLocaleString()||'—'}} 个节点 / {{repository?.edges.length.toLocaleString()||'—'}} 条关系</p><button class="gx-secondary" :aria-expanded="showColors" @click="showColors=!showColors">配色设置</button><button class="gx-secondary" ref="schemaTrigger" @click="showSchema=true"><GraphIcon name="graph"/>查看本体结构</button></div>
    <GraphColorSettings v-if="showColors&&repository" :counts="repository.counts" :settings="colorSettings" :message="colorMessage" @change="(type,color)=>colorSettings.typeColors[type]=color" @reset="resetColors"/>
    <form class="gx-search" @submit.prevent="search"><label><GraphIcon name="search"/><input v-model="draft" aria-label="搜索图谱" placeholder="搜索企业、品类、事件、指标或预测"></label><button class="gx-primary">搜索</button><button type="button" class="gx-secondary" @click="showAll">全部对象</button><button type="button" class="gx-secondary" :class="{'gx-overview-active':mode==='overview'}" @click="showOverview"><GraphIcon name="graph"/>全图总览</button></form>
    <nav class="gx-breadcrumb" aria-label="探索路径" title="沿真实图谱关系的探索记录，不表示因果链"><span>{{mode==='overview'?'全部图谱':'探索路径'}}</span><template v-for="(id,index) in trail" :key="index"><span v-if="index">›</span><button @click="backTo(index)">{{repository?.node(id)?.name}}</button></template></nav>
    <p v-if="loadError" role="alert" class="gx-error">{{loadError}}<button @click="load">重试</button></p>
    <div v-else-if="!repository" class="gx-loading">正在加载图谱…</div>
    <div v-else class="gx-layout" :class="{'gx-details-hidden':!detailsOpen}">
      <aside class="gx-directory"><h3>对象目录</h3><div class="gx-types"><button :class="{'gx-type-selected':!type}" @click="setType('')"><GraphIcon name="graph"/><span>全部对象</span><small>{{repository.nodes.length}}</small></button><button v-for="row in categories" :key="row.type" :class="{'gx-type-selected':type===row.type,unavailable:!row.count}" @click="setType(row.type)"><GraphIcon :name="row.type"/><span>{{row.type==='预测'?'预测结果':row.type}}</span><small>{{row.count}}</small></button></div><div class="gx-list-heading"><span>{{query?'搜索结果':'对象列表'}}</span><small>{{results.total}} 项</small></div><div class="gx-object-list"><button v-for="node in results.nodes" :key="node.id" :class="{chosen:rootId===node.id}" @click="navigate(node.id)" :title="node.name"><b>{{node.name}}</b><small>{{node.type}}<template v-if="node.properties.层级==='三级'"> · 客户</template></small></button><div class="gx-no-results" v-if="!results.total"><p>当前原始数据中暂无{{type||'匹配'}}对象</p><button @click="showAll">查看全部对象</button></div></div><footer class="gx-pages"><button @click="page--" :disabled="page<=1" aria-label="上一页">‹</button><span>{{results.page}} / {{results.pages}} 页</span><button @click="page++" :disabled="page>=results.pages" aria-label="下一页">›</button></footer></aside>
      <section class="gx-graph-panel"><div class="gx-graph-heading"><div><h3>{{mode==='overview'?'全部节点图谱':rootNode?.name||'请选择对象'}}</h3><p>{{mode==='overview'?'全部节点与关系':mode==='path'?'事件与终端影响':(Object.keys(expanded).length?'已展开关联网络':'一层关系')}}<span v-if="rootNode"> · {{view.nodes.length}} 节点 / {{view.edges.length}} 关系</span></p></div><div class="gx-view-switch"><button :class="{on:mode==='relations'}" @click="setMode('relations')"><GraphIcon name="graph"/>关系图</button><button v-if="pathScenarios.length" :class="{on:mode==='path'}" @click="setMode('path')"><GraphIcon name="运输"/>传导路径</button></div></div>
        <div class="gx-graph-options"><label v-if="mode==='relations'"><input type="checkbox" v-model="includeValues">显示指标值</label><span v-else-if="mode==='overview'">放大查看名称，悬停查看属性</span><label v-if="mode==='path'&&pathScenarios.length>1" class="gx-scenario-picker">传导情景<select v-model="activeScenarioId" aria-label="选择传导情景"><option v-for="scenario in pathScenarios" :key="scenario.id" :value="scenario.id">{{scenario.name}}</option></select></label><span v-if="mode==='relations'&&view.hiddenValues&&!includeValues">{{view.hiddenValues}} 个指标值已收起</span><button class="gx-detail-toggle" @click="detailsOpen=!detailsOpen">{{detailsOpen?'收起详情':'展开详情'}}</button></div>
        <div class="gx-canvas" @mouseleave="hideTooltip"><div class="gx-render" ref="canvas" role="img" :aria-label="mode==='overview'?'全部原始节点和关系图':rootNode?.name+'的'+(mode==='path'?'传导路径':'一层关系图')"></div><p v-if="graphError" role="alert" class="gx-canvas-message">{{graphError}}<button @click="draw">重试渲染</button></p><div v-else-if="mode==='path'&&!view.edges.length" class="gx-canvas-message"><GraphIcon name="graph"/><b>暂无已记录的传导路径</b><p>原始文件未提供此对象的触发、传导或影响关系。</p><button @click="setMode('relations')">返回关系图</button></div><span v-if="rendering" class="gx-render-status" role="status">正在绘制 {{view.nodes.length.toLocaleString()}} 个节点…</span><div class="gx-canvas-tools"><button v-for="tool in tools" :key="tool.label" :title="tool.label" :aria-label="tool.label" @click="operate(tool.action)"><GraphIcon :name="tool.icon"/></button></div></div>
        <div class="gx-graph-footer"><div class="gx-legend"><span v-for="legend in legends" :key="legend.type"><i :style="{background:legend.colors[0],borderColor:legend.colors[1]}"></i>{{legend.type}}</span></div><small>{{mode==='overview'?'拖动画布 · 滚轮缩放 · 拖动节点调整位置 · 双击查看详情':'拖动画布 · 滚轮缩放 · 拖动节点联动布局 · 双击追加关联'}}</small></div>
        <div class="gx-neighbor-pages" v-if="mode==='relations'&&view.totalNeighbors>view.limit"><span>直接关联 {{view.totalNeighbors}} 个对象，每批 {{view.limit}} 个</span><button :disabled="neighborOffset===0" @click="neighborOffset=Math.max(0,neighborOffset-view.limit)">上一批</button><button :disabled="!view.hasMore" @click="neighborOffset+=view.limit">下一批</button></div>
      </section>
      <aside v-if="detailsOpen" class="gx-details" aria-live="polite"><h3>{{selectedEdge?'关系详情':'对象详情'}}</h3><template v-if="selectedEdge"><div class="gx-edge-title"><b>{{repository.node(selectedEdge.source)?.name}}</b><span>{{selectedEdge.type}} →</span><b>{{repository.node(selectedEdge.target)?.name}}</b></div><dl><template v-for="(value,key) in selectedEdge.properties" :key="key"><dt>{{key}}</dt><dd>{{value||'未提供'}}</dd></template><dt>关系来源</dt><dd>{{edgeSource(selectedEdge)}}</dd></dl><button class="gx-secondary" @click="selectedEdgeId=''">返回对象详情</button></template>
        <template v-else-if="detailNode"><div class="gx-related-list"><button v-for="node in detailObjects" :key="node.id" :class="{selected:node.id===detailId}" @click="selectNode(node.id)"><i :style="{background:displayColor(node)+'22',color:displayColor(node)}"><GraphIcon :name="node.type"/></i><div><b>{{node.name}}</b><small>对象类型：{{node.type}}</small></div><GraphIcon name="chevron"/></button></div><details class="gx-properties" :open="detailId!==rootId"><summary>基本信息 · {{detailNode.name}}</summary><dl><template v-for="(value,key) in detailNode.properties" :key="key"><dt>{{key}}</dt><dd>{{value||'未提供'}}</dd></template><dt>数据来源</dt><dd>{{repository.source}}</dd><dt>节点 ID</dt><dd>{{detailNode.id}}</dd></dl></details><button v-if="detailId!==rootId" class="gx-secondary gx-continue" @click="navigate(detailId,{extend:mode!=='overview'})">以此对象继续探索</button>
          <div v-if="salesOpen&&detailNode.type==='企业'" class="gx-sales"><h4>实际月销量</h4><template v-if="sales.length"><p>累计 {{format(totalSales)}} 立方米</p><div ref="salesCanvas" class="gx-sales-chart" role="img" :aria-label="detailNode.name+'的实际月销量趋势'"></div><details><summary>查看原始明细（{{sales.length}}期）</summary><table><thead><tr><th>月份</th><th>销量（立方米）</th></tr></thead><tbody><tr v-for="row in sales" :key="row.id"><td>{{row.properties.月份}}</td><td>{{format(row.properties.数值)}}</td></tr></tbody></table></details><small>{{repository.period}}；11 月为非完整月。</small></template><p v-else>此企业没有直接关联的月销量记录，未将下属企业销量自动汇总。</p></div>
        </template><p v-else class="gx-detail-empty">点击画布中的节点或连线查看详情。</p>
        <div class="gx-detail-actions"><button v-if="!selectedEdge&&detailNode?.type==='企业'" class="gx-primary" @click="salesOpen=!salesOpen"><GraphIcon name="指标值"/>{{salesOpen?'收起销量':'查看销量'}}</button><button class="gx-secondary" :disabled="!detailNode&&!selectedEdge" @click="analyze"><GraphIcon name="message"/>带入会话分析</button></div>
      </aside>
    </div>
    <p v-if="repository" class="gx-source">{{repository.source}}</p>
    <GraphSchema v-if="showSchema&&repository" :repository="repository" :color-settings="colorSettings" @close="showSchema=false" @browse="browseSchemaType"/>
  </div>
</template>
<script setup>
import {ref,shallowRef,computed,onMounted,onBeforeUnmount,watch,nextTick} from 'vue'
import GraphIcon from './GraphIcon.vue'
import GraphSchema from './GraphSchema.vue'
import GraphColorSettings from './GraphColorSettings.vue'
import {loadGraphRepository} from './graphRepository.js'
import {overviewPositions,cachedOverviewPositions} from './graphLayout.js'
import {nodeLabelStyle} from './graphAppearance.js'
import {mergeNeighborhoods} from './graphExpansion.js'
import {explorationTrail} from './graphNavigation.js'
import {normalizeColorSettings,nodeColor,typeColor,colorSettingsKey} from './graphColorSettings.js'
const emit=defineEmits(['analyze'])
let savedColors;try{savedColors=JSON.parse(localStorage.getItem(colorSettingsKey)||'null')}catch{}
const colorSettings=ref(normalizeColorSettings(savedColors)),showColors=ref(false),colorMessage=ref('')
const displayColor=node=>nodeColor(node,colorSettings.value)
function resetColors(){colorSettings.value=normalizeColorSettings(null)}
const schemaTrigger=ref(),host=ref(),canvas=ref(),salesCanvas=ref(),repository=shallowRef(),loadError=ref(''),graphError=ref(''),rendering=ref(false)
const expanded=ref({}),activeScenarioId=ref('')
const pathScenarios=computed(()=>repository.value?.scenariosFor(rootId.value)||[])
const draft=ref(''),query=ref(''),type=ref('企业'),page=ref(1),rootId=ref(''),detailId=ref(''),selectedEdgeId=ref(''),trail=ref([]),recentIds=ref([]),mode=ref('relations'),includeValues=ref(false),neighborOffset=ref(0),detailsOpen=ref(true),salesOpen=ref(false),showSchema=ref(false)
let graph,trend,observer,resizeTimer,disposed=false,drawQueue=Promise.resolve(),generation=0,chartGeneration=0,previousHighlights=[],drawnNodes=[],labelCache=new Map(),labelTimer,labelsUpdating=false,lastLabelZoom=-1,colorTimer,colorRevision=0,retainOnNextDraw=false,overviewCoordinates,overviewRequest,wheelFrame,wheelDelta=0
const categories=computed(()=>{const counts=repository.value?.counts||[];return [...counts,...['事件','预测','传导规则'].filter(t=>!counts.some(row=>row.type===t)).map(type=>({type,count:0}))]})
const results=computed(()=>repository.value?.search({query:query.value,type:type.value,page:page.value})||{nodes:[],total:0,page:1,pages:1})
const rootNode=computed(()=>repository.value?.node(rootId.value)),detailNode=computed(()=>repository.value?.node(detailId.value)),selectedEdge=computed(()=>repository.value?.edge(selectedEdgeId.value))
const view=computed(()=>{
  if(!repository.value||!rootId.value)return {nodes:[],edges:[]}
  if(mode.value==='overview')return repository.value.overview()
  if(mode.value==='path')return repository.value.paths(rootId.value,{scenarioId:activeScenarioId.value})
  const base=repository.value.neighborhood(rootId.value,{includeValues:includeValues.value,offset:neighborOffset.value})
  return {...base,...mergeNeighborhoods([base,...Object.entries(expanded.value).map(([id,limit])=>repository.value.neighborhood(id,{includeValues:includeValues.value,limit}))])}
})
const detailObjects=computed(()=>{const ids=mode.value==='overview'?[detailId.value,...repository.value.neighborhood(detailId.value).nodes.map(n=>n.id)]:[detailId.value,rootId.value,...view.value.nodes.map(n=>n.id)];return [...new Set(ids)].map(id=>repository.value?.node(id)).filter(Boolean).slice(0,8)})
const sales=computed(()=>repository.value?.sales(detailId.value)||[]),totalSales=computed(()=>sales.value.reduce((sum,n)=>sum+Number(n.properties.数值),0))
const legends=computed(()=>[...new Set(view.value.nodes.map(n=>n.type))].map(type=>({type,colors:[typeColor(type,colorSettings.value),typeColor(type,colorSettings.value)]})))
const tools=[{label:'放大',icon:'zoomIn',action:'in'},{label:'缩小',icon:'zoomOut',action:'out'},{label:'适应画布',icon:'fit',action:'fit'},{label:'复位',icon:'reset',action:'reset'}]
const format=value=>Number(value).toLocaleString('zh-CN',{maximumFractionDigits:2})
function browseSchemaType(value){showSchema.value=false;draft.value=query.value='';setType(value)}
function search(){query.value=draft.value.trim();type.value='';page.value=1;const first=repository.value?.search({query:query.value,type:type.value}).nodes[0];if(first)navigate(first.id)}
function setType(value){type.value=value;page.value=1}
function showAll(){draft.value=query.value=type.value='';page.value=1}
function showOverview(){showAll();trail.value=[];selectedEdgeId.value='';salesOpen.value=false;mode.value='overview';detailsOpen.value=false}
function navigate(id,{extend=false}={}){
  if(!repository.value?.node(id))return
  trail.value=explorationTrail(trail.value,id,{extend,edges:view.value.edges})
  expanded.value={};retainOnNextDraw=false
  rootId.value=detailId.value=id;activeScenarioId.value=repository.value.scenariosFor(id)[0]?.id||'';selectedEdgeId.value='';salesOpen.value=false;neighborOffset.value=0
  includeValues.value=repository.value.node(id).type==='指标值'
  mode.value=['事件','传导规则'].includes(repository.value.node(id).type)?'path':'relations';detailsOpen.value=true
  recentIds.value=[...recentIds.value.filter(value=>value!==id),id].slice(-8)
  try{localStorage.setItem('wb-graph-recent',JSON.stringify(recentIds.value))}catch{}
}
function expandNode(id){
  if(mode.value==='overview'){selectNode(id);return}
  if(mode.value==='path'){navigate(id,{extend:true});return}
  trail.value=explorationTrail(trail.value,id,{extend:true,edges:view.value.edges})
  selectNode(id);retainOnNextDraw=true
  expanded.value={...expanded.value,[id]:(expanded.value[id]||0)+18}
}
function clearSelection(){detailId.value='';selectedEdgeId.value='';salesOpen.value=false;hideTooltip();highlight()}
function backTo(index){navigate(trail.value[index],{extend:true})}

function selectNode(id){if(!repository.value?.node(id))return;detailId.value=id;selectedEdgeId.value='';detailsOpen.value=true;salesOpen.value=false;highlight()}
function setMode(value){mode.value=value;selectedEdgeId.value='';neighborOffset.value=0;if(!trail.value.length&&rootId.value)trail.value=[rootId.value]}
function hideTooltip(){
  // G6 initializes plugins on its first render, so no tooltip exists before then.
  try{graph?.getPluginInstance('details-tooltip')?.hide()}catch{}
}
function highlight(){
  if(!graph)return
  const available=new Set([...view.value.nodes,...view.value.edges].map(item=>item.id)),states={}
  for(const id of previousHighlights)if(available.has(id))states[id]=[]
  previousHighlights=[]
  const mark=(id,state)=>{if(available.has(id)){states[id]=[state];previousHighlights.push(id)}}
  if(selectedEdgeId.value){
    const edge=repository.value.edge(selectedEdgeId.value)
    mark(edge.id,'selected');mark(edge.source,'related');mark(edge.target,'related')
  }else if(detailId.value){
    mark(detailId.value,'selected')
    for(const edge of view.value.edges)if(edge.source===detailId.value||edge.target===detailId.value){
      mark(edge.id,'related');mark(edge.source===detailId.value?edge.target:edge.source,'related')
    }
  }
  if(Object.keys(states).length)graph.setElementState(states,false).catch(()=>{})
}
function edgeSource(edge){return edge.sourceFile?`${edge.sourceFile} · 第 ${edge.sourceRow} 行`:`relationships.csv · 第 ${Number(edge.id.split('-').at(-1))+2} 行`}
function analyze(){const n=detailNode.value,e=selectedEdge.value;const subject=e?`${repository.value.node(e.source).name} —${e.type}→ ${repository.value.node(e.target).name}`:`${n.name}（${n.type}）`;const properties=e?.properties||n?.properties||{};emit('analyze',`请基于原始图谱分析「${subject}」。\n来源：${repository.value.source}\n数据类型：${(e||n)?.simulated?'情景推演，非已发生事件或实绩':'原始记录'}\n属性：${Object.entries(properties).map(([k,v])=>`${k}：${v||'未提供'}`).join('；')}\n请区分原始关联和因果推断；原始数据未提供的验证状态、事件与预测依据不要补造。`)}
function tooltip(event,items){const id=items[0]?.id||event.target?.id,n=repository.value?.node(id),e=repository.value?.edge(id);const el=document.createElement('div');el.className='gx-node-tooltip';const title=document.createElement('b');title.textContent=n?`${n.name} · ${n.type}`:e?`${repository.value.node(e.source)?.name} —${e.type}→ ${repository.value.node(e.target)?.name}`:'';el.append(title);const text=document.createElement('p');text.style.cssText='white-space:pre-wrap;max-width:290px;line-height:1.7;margin:7px 0 0';text.textContent=Object.entries(n?.properties||e?.properties||{}).slice(0,7).map(([k,v])=>`${k}：${v||'未提供'}`).join('\n');el.append(text);return Promise.resolve(el)}
async function prepareOverview(){
  if(overviewCoordinates)return
  if(!overviewRequest)overviewRequest=fetch(repository.value.layoutFile).then(response=>response.ok?response.json():null).then(cache=>{overviewCoordinates=cachedOverviewPositions(cache,repository.value)||overviewPositions(repository.value.nodes)}).catch(()=>{overviewCoordinates=overviewPositions(repository.value.nodes)})
  await overviewRequest
}
function graphData(retained=new Map()){
  const data=view.value,overview=mode.value==='overview',width=Math.max(360,canvas.value.clientWidth),height=canvas.value.clientHeight||540
  const relationCounts=new Map(),labeledTypes=new Set(),degrees=new Map(data.nodes.map(node=>[node.id,0]))
  for(const edge of data.edges){relationCounts.set(edge.type,(relationCounts.get(edge.type)||0)+1);degrees.set(edge.source,degrees.get(edge.source)+1);degrees.set(edge.target,degrees.get(edge.target)+1)}
  const others=data.nodes.filter(n=>n.id!==rootId.value),count=others.length
  const positions=overview?(overviewCoordinates||overviewPositions(data.nodes)):new Map([[rootId.value,[width/2,height/2]]])
  if(mode.value==='path'){
    const groups=new Map()
    for(const node of data.nodes){const layer=data.layers?.get(node.id)||0;groups.set(layer,[...(groups.get(layer)||[]),node.id])}
    let extraRows=0
    for(const [layer,ids] of [...groups].sort((a,b)=>a[0]-b[0])){
      const columns=Math.min(5,ids.length),rowCount=Math.ceil(ids.length/columns)
      ids.forEach((id,i)=>positions.set(id,[(i%columns+1)*width/(columns+1),55+(layer+extraRows+Math.floor(i/columns))*110]))
      extraRows+=rowCount-1
    }
  }else if(!overview){
    const radius=Math.max(170,Math.min(width,height)*.34),ringSize=8
    others.forEach((n,i)=>{const ring=Math.floor(i/ringSize),ringCount=Math.min(ringSize,count-ring*ringSize),angle=(i%ringSize)/ringCount*Math.PI*2-Math.PI/2-.55,r=radius+ring*140;positions.set(n.id,[width/2+Math.cos(angle)*r,height/2+Math.sin(angle)*r])})
  }
  return {
    nodes:data.nodes.map(n=>{
      const center=!overview&&n.id===rootId.value,[x,y]=retained.get(n.id)||positions.get(n.id)
      const degree=degrees.get(n.id)||0
      const diameter=overview?Math.min(48,14+Math.log2(degree+1)*3):center?60:44
      const datum={id:n.id,data:{name:n.name,type:n.type,diameter,overview},style:{x,y,size:diameter,fill:displayColor(n),stroke:'#fff',lineWidth:1.5,icon:false,label:false,labelPlacement:'bottom',labelOffsetY:7,labelFill:'#292929',labelFontWeight:600,labelWordWrap:true,labelTextOverflow:'ellipsis',labelTextAlign:'center',labelTextBaseline:'top',shadowColor:'#20304818',shadowBlur:overview?0:4,shadowOffsetY:overview?0:2,cursor:'grab'}}
      Object.assign(datum.style,nodeLabelStyle(datum,0))
      return datum
    }),
    edges:data.edges.map(e=>{const label=!overview&&(relationCounts.get(e.type)<=4||!labeledTypes.has(e.type));labeledTypes.add(e.type);return {id:e.id,source:e.source,target:e.target,data:{type:e.type},style:{label,labelText:e.type,labelPlacement:mode.value==='path'?.72:.5,labelAutoRotate:false,labelFontSize:12,labelFill:'#686868',labelBackground:true,labelBackgroundFill:'#fff',labelPadding:[2,4],endArrow:!overview,endArrowSize:5,stroke:'#b4becb',lineWidth:overview?.5:1,opacity:overview?.2:.85}}})
  }
}

function scheduleColors(){
  const ticket=++colorRevision;clearTimeout(colorTimer)
  colorTimer=setTimeout(()=>{drawQueue=drawQueue.catch(()=>{}).then(async()=>{
    if(disposed||!graph||rendering.value||ticket!==colorRevision)return
    const changes=graph.getNodeData().flatMap(node=>{const fill=displayColor(repository.value.node(node.id));return node.style.fill===fill?[]:[{id:node.id,style:{fill}}]})
    if(changes.length){graph.updateNodeData(changes);await graph.draw()}
  }).catch(error=>{if(!disposed)colorMessage.value='配色更新失败：'+error.message})},120)
}
watch(colorSettings,value=>{
  try{localStorage.setItem(colorSettingsKey,JSON.stringify(normalizeColorSettings(value)));colorMessage.value=''}catch{colorMessage.value='当前浏览器无法保存设置，本次配色仍会生效。'}
  scheduleColors()
},{deep:true})
function scheduleLabels(){clearTimeout(labelTimer);labelTimer=setTimeout(updateLabels,mode.value==='overview'?220:65)}
async function updateLabels(){
  if(!graph||disposed||rendering.value||labelsUpdating)return
  const zoom=graph.getZoom();if(Math.abs(zoom-lastLabelZoom)<.001)return
  labelsUpdating=true;lastLabelZoom=zoom
  try{
    const changes=[]
    for(const node of drawnNodes){const style=nodeLabelStyle(node,zoom),key=JSON.stringify(style);if(labelCache.get(node.id)!==key){labelCache.set(node.id,key);changes.push({id:node.id,style})}}
    if(changes.length){graph.updateNodeData(changes);await graph.draw()}
  }finally{labelsUpdating=false;if(graph&&!disposed&&Math.abs(graph.getZoom()-lastLabelZoom)>.001)scheduleLabels()}
}
function draw(){
  const ticket=++generation;rendering.value=true
  drawQueue=drawQueue.catch(()=>{}).then(async()=>{
    await nextTick();if(disposed||ticket!==generation||!canvas.value||!repository.value)return
    try{
      if(!graph){
        const {Graph}=await import('@antv/g6');if(disposed||ticket!==generation)return
        graph=new Graph({container:canvas.value,width:canvas.value.clientWidth,height:canvas.value.clientHeight,padding:55,animation:false,zoomRange:[.03,12],behaviors:[{type:'drag-canvas',enable:event=>event.targetType==='canvas'},{type:'drag-element-force',enable:()=>mode.value==='relations',fixed:false,hideEdge:'none'},{type:'drag-element',enable:()=>mode.value!=='relations',animation:false,hideEdge:'none',dropEffect:'none',shadow:false},{type:'optimize-viewport-transform',debounce:160}],node:{type:'circle',state:{selected:{size:60,stroke:'#087f64',lineWidth:2,halo:true,haloStroke:'#cde8db',haloLineWidth:5,haloStrokeOpacity:.45},related:{stroke:'#86b6a5',lineWidth:2}}},edge:{type:'line',state:{selected:{stroke:'#178d71',lineWidth:2,label:true,endArrow:true},related:{stroke:'#74aa97',lineWidth:1.6,endArrow:true},hover:{stroke:'#178d71',label:true,endArrow:true}}},plugins:[{type:'tooltip',key:'details-tooltip',trigger:'hover',getContent:tooltip}]})
        graph.on('node:click',e=>selectNode(e.target.id))
        graph.on('node:dblclick',e=>expandNode(e.target.id))
        graph.on('canvas:click',clearSelection)
        graph.on('edge:pointerenter',e=>graph.setElementState(e.target.id,['hover'],false))
        graph.on('edge:pointerleave',e=>{graph.setElementState(e.target.id,[],false);highlight()})
        graph.on('edge:click',e=>{selectedEdgeId.value=e.target.id;detailsOpen.value=true;highlight()})
        graph.on('aftertransform',scheduleLabels)
      }
      if(disposed||ticket!==generation)return
      graphError.value='';hideTooltip();clearTimeout(labelTimer)
      const overview=mode.value==='overview'
      if(overview)await prepareOverview()
      if(disposed||ticket!==generation)return
      const retain=retainOnNextDraw;retainOnNextDraw=false
      const retained=retain?new Map(graph.getNodeData().map(n=>[n.id,graph.getElementPosition(n.id)])):new Map()
      const data=graphData(retained);drawnNodes=data.nodes;labelCache=new Map(data.nodes.map(node=>[node.id,JSON.stringify(nodeLabelStyle(node,0))]));lastLabelZoom=-1
      graph.setLayout(mode.value!=='relations'?[]:{type:'d3-force',animation:true,preLayout:false,iterations:overview?65:100,alphaDecay:overview?.13:.1,velocityDecay:.5,linkDistance:overview?65:155,nodeStrength:overview?-60:-450,preventOverlap:true,nodeSize:overview?42:80,nodeSpacing:15,collide:{radius:overview?22:52,strength:1},centerStrength:.3})
      if(mode.value==='path'&&!view.value.edges.length){data.nodes=[];data.edges=[]}
      graph.setData(data);await graph.render()
      if(disposed||ticket!==generation)return
      if(!retain&&data.nodes.length)await graph.fitView({when:'always'},false);if(data.nodes.length)highlight()
    }catch(error){if(!disposed)graphError.value='图谱渲染失败：'+error.message}
    finally{if(ticket===generation){rendering.value=false;await updateLabels();scheduleColors()}}
  })
  return drawQueue
}
async function operate(action){try{if(!graph)return;if(action==='reset')return draw();if(action==='fit')await graph.fitView({when:'always'},false);else await graph.zoomBy(action==='in'?1.2:1/1.2,false)}catch{}}
function wheel(event){if(!graph||event.target.closest('button'))return;event.preventDefault();wheelDelta+=event.deltaY;if(!wheelFrame)wheelFrame=requestAnimationFrame(()=>{wheelFrame=0;const delta=wheelDelta;wheelDelta=0;if(graph&&!disposed)graph.zoomBy(Math.exp(-Math.max(-160,Math.min(160,delta))*.002),false).catch(()=>{})})}
async function drawSales(){const ticket=++chartGeneration;await nextTick();trend?.dispose();trend=null;if(!salesOpen.value||!salesCanvas.value||!sales.value.length)return;const echarts=await import('echarts');if(disposed||ticket!==chartGeneration||!salesCanvas.value)return;trend=echarts.init(salesCanvas.value);trend.setOption({color:['#009273'],tooltip:{trigger:'axis',renderMode:'richText',confine:true,valueFormatter:v=>format(v)+' 立方米'},grid:{left:58,right:10,top:15,bottom:38},xAxis:{type:'category',data:sales.value.map(n=>n.properties.月份),axisLabel:{fontSize:12,color:'#686868',rotate:35}},yAxis:{type:'value',axisLabel:{fontSize:12,color:'#686868',formatter:v=>v>=10000?format(v/10000)+'万':format(v)},splitLine:{lineStyle:{color:'#e8edf5'}}},series:[{name:'实际月销量',type:'line',data:sales.value.map(n=>Number(n.properties.数值)),lineStyle:{width:2.5},symbolSize:5}]})}
async function load(){loadError.value='';try{repository.value=await loadGraphRepository();if(disposed)return;await nextTick();let recent=[];try{const saved=JSON.parse(localStorage.getItem('wb-graph-recent')||'[]');if(Array.isArray(saved))recent=saved.filter(id=>repository.value.node(id))}catch{};recentIds.value=recent.slice(-8);trail.value=[];const initial=repository.value.node(recent.at(-1))||repository.value.nodes.find(n=>n.type==='企业'&&n.name==='万众吉利')||repository.value.nodes[0];if(initial){type.value=initial.type;navigate(initial.id);}observer?.disconnect();observer=new ResizeObserver(()=>{clearTimeout(resizeTimer);resizeTimer=setTimeout(()=>{if(!disposed){graph?.resize();trend?.resize()}},100)});observer.observe(canvas.value);canvas.value.addEventListener('wheel',wheel,{passive:false,capture:true});await draw()}catch(error){loadError.value=error.message}}
watch(showSchema,async value=>{await nextTick();if(!value)schemaTrigger.value?.focus()});watch([rootId,mode,includeValues,neighborOffset,expanded,activeScenarioId],draw);watch([salesOpen,detailId],drawSales);watch(detailsOpen,async()=>{await nextTick();graph?.resize();drawSales()});onMounted(load)
onBeforeUnmount(()=>{disposed=true;generation++;chartGeneration++;clearTimeout(resizeTimer);clearTimeout(labelTimer);clearTimeout(colorTimer);cancelAnimationFrame(wheelFrame);observer?.disconnect();canvas.value?.removeEventListener('wheel',wheel,true);graph?.destroy();trend?.dispose()})
</script>
<style scoped>
.gx{color:var(--wb-ui-text)}.gx button,.gx input{font:inherit}.gx button{cursor:pointer}.gx button:disabled{cursor:default;opacity:.4}.gx svg{flex-shrink:0}.gx-topline{display:flex;justify-content:space-between;align-items:center;gap:12px;margin:4px 0 20px}.gx-topline p,.gx-source{font-size:var(--wb-font-meta);color:var(--wb-ui-secondary);line-height:1.8}.gx .gx-primary,.gx .gx-secondary{display:inline-flex;align-items:center;justify-content:center;gap:7px;padding:10px 16px;border:1px solid var(--wb-ui-border);border-radius:7px;color:var(--wb-ui-accent);background:var(--wb-ui-surface-raised);font-size:var(--wb-font-control)}.gx .gx-primary{color:var(--wb-ui-on-primary);background:var(--wb-ui-accent);border-color:var(--wb-ui-accent)}.gx .gx-secondary:hover{background:var(--wb-ui-surface)}.gx-search{display:flex;gap:10px}.gx-search>label{display:flex;align-items:center;gap:11px;flex:1;min-width:0;border:1px solid var(--wb-ui-border);border-radius:8px;padding:0 14px;background:white;color:var(--wb-ui-secondary)}.gx-search input{width:100%;min-width:0;border:0;outline:0;padding:12px 0;font-size:var(--wb-font-body);color:var(--wb-ui-text);background:transparent}.gx-search input::placeholder{color:var(--wb-ui-muted)}.gx-breadcrumb{display:flex;gap:9px;align-items:center;flex-wrap:wrap;min-height:40px;font-size:var(--wb-font-meta);color:var(--wb-ui-secondary)}.gx-breadcrumb button{font-size:var(--wb-font-meta);color:var(--wb-ui-secondary);background:transparent;border:0}.gx-layout{display:grid;grid-template-columns:210px minmax(320px,1fr) 290px;gap:12px;align-items:stretch}.gx-layout.gx-details-hidden{grid-template-columns:190px minmax(320px,1fr)}.gx-directory,.gx-graph-panel,.gx-details{border:1px solid var(--wb-ui-border);border-radius:10px;background:var(--wb-ui-surface-raised);min-width:0;overflow:hidden}.gx h3{font-size:16px;font-weight:650;color:var(--wb-ui-text);margin:0}.gx-directory{display:flex;flex-direction:column;max-height:830px}.gx-directory>h3,.gx-details>h3{padding:17px 16px;border-bottom:1px solid var(--wb-ui-border)}.gx-types{padding:10px 9px;max-height:310px;overflow-y:auto;border-bottom:1px solid var(--wb-ui-border);scrollbar-width:thin}.gx-types button{display:flex;align-items:center;gap:8px;width:100%;padding:9px 10px;border:0;background:transparent;color:var(--wb-ui-text);text-align:left;font-size:var(--wb-font-control);border-radius:6px}.gx-types small{margin-left:auto;font-size:var(--wb-font-meta);color:var(--wb-ui-muted)}.gx-types .gx-type-selected{background:var(--wb-ui-surface);color:var(--wb-ui-accent);font-weight:600}.gx-types .unavailable{color:var(--wb-ui-muted)}.gx-list-heading{display:flex;justify-content:space-between;padding:12px 16px 6px;color:var(--wb-ui-secondary);font-size:var(--wb-font-meta)}.gx-object-list{padding:3px 9px;overflow:auto;scrollbar-width:thin;flex:1;min-height:170px}.gx-object-list>button{display:block;width:100%;text-align:left;padding:10px 11px;border:0;background:transparent;border-radius:6px;color:var(--wb-ui-text);margin-bottom:3px}.gx-object-list>button.chosen{background:var(--wb-ui-surface);color:var(--wb-ui-accent)}.gx-object-list b{display:block;font-weight:500;font-size:var(--wb-font-control);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.gx-object-list small{display:block;color:var(--wb-ui-muted);font-size:var(--wb-font-meta);margin-top:3px}.gx-pages{padding:10px;display:flex;justify-content:space-between;align-items:center;border-top:1px solid var(--wb-ui-border);font-size:var(--wb-font-meta);color:var(--wb-ui-secondary)}.gx-pages button{width:28px;height:28px;border:1px solid var(--wb-ui-border);border-radius:5px;color:var(--wb-ui-secondary);background:var(--wb-ui-surface-raised);font-size:18px}.gx-no-results{padding:20px 5px;font-size:var(--wb-font-control);line-height:1.9;color:var(--wb-ui-secondary)}.gx-no-results button{background:none;border:0;color:var(--wb-ui-accent);font-size:var(--wb-font-meta);margin-top:10px}.gx-graph-panel{display:flex;flex-direction:column}.gx-graph-heading{display:flex;justify-content:space-between;align-items:flex-start;gap:12px;padding:19px 18px 8px;flex-wrap:wrap}.gx-graph-heading h3{font-size:21px;color:var(--wb-ui-text)}.gx-graph-heading p{font-size:var(--wb-font-control);color:var(--wb-ui-secondary);margin:7px 0}.gx-graph-heading p span{font-size:var(--wb-font-meta)}.gx-view-switch{display:flex;border:1px solid var(--wb-ui-border);border-radius:6px;overflow:hidden}.gx-view-switch button{display:flex;align-items:center;gap:5px;padding:7px 10px;font-size:var(--wb-font-meta);border:0;background:var(--wb-ui-surface-raised);color:var(--wb-ui-secondary)}.gx-view-switch .on{color:var(--wb-ui-accent);background:var(--wb-ui-surface)}.gx-view-switch svg{width:16px;height:16px}.gx-graph-options{display:flex;align-items:center;gap:9px;flex-wrap:wrap;padding:0 18px 10px;font-size:var(--wb-font-meta);color:var(--wb-ui-muted)}.gx-graph-options label{display:flex;align-items:center;gap:5px;color:var(--wb-ui-secondary)}.gx-graph-options input{accent-color:var(--wb-ui-accent)}.gx-graph-options button{border:0;background:none;color:var(--wb-ui-secondary);font-size:var(--wb-font-meta);margin-left:auto}.gx-canvas{position:relative;flex:1;min-height:510px;background:var(--wb-ui-surface-raised)}.gx-render{height:100%;width:100%;min-height:510px;touch-action:none;overscroll-behavior:contain;cursor:grab}.gx-render:active{cursor:grabbing}.gx-canvas-tools{position:absolute;bottom:14px;right:14px;display:flex;border:1px solid var(--wb-ui-border);background:var(--wb-ui-surface-raised);border-radius:7px;padding:3px;box-shadow:0 3px 10px #20212410}.gx-canvas-tools button{display:grid;place-items:center;width:30px;height:30px;color:var(--wb-ui-secondary);background:var(--wb-ui-surface-raised);border:0;border-radius:4px}.gx-canvas-tools button:hover{background:var(--wb-ui-surface)}.gx-canvas-tools svg{width:17px;height:17px}.gx-canvas-message{position:absolute;inset:0;display:flex;flex-direction:column;gap:12px;align-items:center;justify-content:center;background:#ffffffed;text-align:center;padding:30px;font-size:var(--wb-font-body);color:var(--wb-ui-secondary)}.gx-canvas-message p{max-width:280px;line-height:1.9;font-size:var(--wb-font-control)}.gx-canvas-message>svg{width:36px;height:36px;color:var(--wb-ui-muted)}.gx-canvas-message button{font-size:var(--wb-font-control);color:var(--wb-ui-accent);background:var(--wb-ui-surface);border:1px solid var(--wb-ui-border);border-radius:6px;padding:8px 14px}.gx-graph-footer{display:flex;flex-direction:column;gap:10px;padding:13px 17px;border-top:1px solid var(--wb-ui-border)}.gx-legend{display:flex;flex-wrap:wrap;gap:13px;font-size:var(--wb-font-meta);color:var(--wb-ui-secondary)}.gx-legend span{display:flex;align-items:center;gap:6px}.gx-legend i{width:10px;height:10px;border:1px solid;border-radius:50%}.gx-graph-footer>small{font-size:var(--wb-font-meta);color:var(--wb-ui-muted)}.gx-neighbor-pages{display:flex;flex-wrap:wrap;gap:12px;padding:12px 17px;font-size:var(--wb-font-meta);color:var(--wb-ui-secondary);border-top:1px solid var(--wb-ui-border)}.gx-neighbor-pages button{border:0;background:none;color:var(--wb-ui-accent);font-size:var(--wb-font-meta)}.gx-details{padding-bottom:16px;display:flex;flex-direction:column;max-height:830px;overflow:auto;scrollbar-width:thin}.gx-related-list{padding:0 15px;max-height:315px;overflow:auto;scrollbar-width:thin}.gx-related-list button{display:flex;align-items:center;width:100%;text-align:left;gap:10px;padding:16px 0;border:0;border-bottom:1px solid var(--wb-ui-surface);background:white;color:var(--wb-ui-text)}.gx-related-list button>i{display:grid;place-items:center;border-radius:50%;width:35px;height:35px;flex-shrink:0}.gx-related-list button>div{min-width:0;flex:1}.gx-related-list b{display:block;font-weight:600;font-size:var(--wb-font-body);overflow-wrap:anywhere}.gx-related-list small{display:block;font-size:var(--wb-font-meta);color:var(--wb-ui-muted);margin-top:3px}.gx-related-list button>svg{width:15px;color:var(--wb-ui-muted)}.gx-related-list .selected b{color:var(--wb-ui-accent)}.gx-properties{margin:15px;font-size:var(--wb-font-meta);color:var(--wb-ui-secondary)}.gx-properties summary{cursor:pointer}.gx dl{display:grid;grid-template-columns:75px minmax(0,1fr);gap:9px 10px;margin:15px;font-size:var(--wb-font-meta);line-height:1.8}.gx-properties dl{margin:13px 0 0}.gx dt{color:var(--wb-ui-muted)}.gx dd{margin:0;color:var(--wb-ui-secondary);overflow-wrap:anywhere}.gx .gx-continue{margin:0 15px 15px}.gx-detail-actions{display:flex;flex-direction:column;gap:9px;margin:20px 15px 0}.gx-details>.gx-detail-actions{margin-top:auto;padding-top:20px}.gx-edge-title{display:flex;flex-direction:column;gap:7px;padding:20px 16px;font-size:var(--wb-font-body)}.gx-edge-title span{font-size:var(--wb-font-control);color:var(--wb-ui-secondary)}.gx-details>.gx-secondary{margin:15px}.gx-detail-empty{padding:20px;font-size:var(--wb-font-control);color:var(--wb-ui-muted);line-height:1.9}.gx-sales{border-top:1px solid var(--wb-ui-border);padding:15px;margin-top:15px}.gx-sales h4{font-size:var(--wb-font-body);color:var(--wb-ui-text)}.gx-sales>p{font-size:var(--wb-font-meta);color:var(--wb-ui-secondary);line-height:1.8;margin:9px 0}.gx-sales>small{font-size:var(--wb-font-meta);color:var(--wb-ui-secondary);line-height:1.8}.gx-sales-chart{height:210px;width:100%;margin:10px 0}.gx-sales details{font-size:var(--wb-font-meta);color:var(--wb-ui-secondary)}.gx table{width:100%;border-collapse:collapse;font-size:var(--wb-font-meta)}.gx th{background:var(--wb-ui-surface);text-align:left;padding:10px;color:var(--wb-ui-secondary)}.gx td{padding:9px;border-bottom:1px solid var(--wb-ui-border);color:var(--wb-ui-secondary)}.gx td:last-child{text-align:right}.gx-source{margin:16px 0 0;font-size:var(--wb-font-meta)}.gx-error,.gx-loading{padding:60px 20px;text-align:center;font-size:var(--wb-font-body);color:var(--wb-ui-muted)}.gx-error{color:var(--wb-ui-secondary)}.gx-error button{display:block;margin:15px auto;color:var(--wb-ui-accent);border:0;background:none}.gx-schema-backdrop{position:fixed;inset:0;z-index:1200;background:#2021245e;display:flex;align-items:center;justify-content:center;padding:30px}.gx-schema{width:min(780px,100%);max-height:85vh;overflow:auto;background:white;border-radius:14px;padding:25px;color:var(--wb-ui-text);box-shadow:0 20px 80px #20212430}.gx-schema header{display:flex;justify-content:space-between;gap:20px;align-items:center}.gx-schema header button{background:transparent;border:0;color:var(--wb-ui-secondary)}.gx-schema p{font-size:var(--wb-font-meta);color:var(--wb-ui-muted);line-height:1.8;margin:10px 0}.gx-schema-types{display:flex;flex-wrap:wrap;gap:9px;margin:20px 0}.gx-schema-types>span{display:flex;align-items:center;gap:6px;font-size:var(--wb-font-control);padding:7px 11px;border-radius:6px}.gx-schema-types b{font-weight:500;margin-left:5px}.gx-schema-table{overflow:auto}.gx-schema-table td{white-space:nowrap}
@media(min-width:1650px){.gx-layout{grid-template-columns:230px minmax(400px,1fr) 300px}.gx-canvas,.gx-render{min-height:600px}.gx-directory,.gx-details{max-height:930px}}
@media(max-width:1200px){.gx-layout{grid-template-columns:160px minmax(250px,1fr)}.gx-details{grid-column:1/-1;max-height:none}.gx-related-list{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}.gx-details>.gx-detail-actions{flex-direction:row}.gx-layout.gx-details-hidden{grid-template-columns:160px minmax(250px,1fr)}.gx-details>.gx-properties{max-width:650px}.gx-sales-chart{max-width:750px}}
@media(max-width:760px){.gx-layout,.gx-layout.gx-details-hidden{grid-template-columns:1fr}.gx-directory{max-height:400px}.gx-types{display:flex;max-height:none;overflow:auto;gap:3px;flex-shrink:0}.gx-types button{width:auto;white-space:nowrap}.gx-types small{margin-left:5px}.gx-object-list{display:grid;grid-template-columns:1fr 1fr;min-height:100px}.gx-graph-heading{padding:15px 12px}.gx-graph-heading h3{font-size:18px}.gx-related-list{grid-template-columns:1fr 1fr}.gx-search{flex-wrap:wrap}.gx-search label{flex-basis:100%}.gx-topline{flex-wrap:wrap}.gx-canvas,.gx-render{min-height:460px}.gx-schema-backdrop{padding:15px}.gx-topline .gx-secondary{padding:8px 11px}.gx-schema{padding:18px}.gx-layout .gx-graph-panel{grid-row:auto}.gx-details{grid-column:1}.gx-search input{font-size:var(--wb-font-body)}}
.gx-breadcrumb{justify-content:flex-start;flex:none}.gx-pages,.gx-graph-footer{margin:0;position:static}.gx-schema header{height:auto;background:none;border:0;border-radius:0}.gx-types button{box-shadow:none;position:static}.gx-types button:after{display:none}
.gx .gx-overview-active{background:var(--wb-ui-surface);border-color:var(--wb-ui-muted);color:var(--wb-ui-accent)}.gx-render-status{position:absolute;top:15px;left:15px;color:var(--wb-ui-secondary);background:#fffffff0;padding:8px 12px;border:1px solid var(--wb-ui-border);border-radius:6px;font-size:var(--wb-font-meta);pointer-events:none}
.gx-topline>p{margin-right:auto}.gx-color-settings{padding:18px;background:var(--wb-ui-surface);border:1px solid var(--wb-ui-border);border-radius:9px;margin-bottom:18px}.gx-color-heading{display:flex;justify-content:space-between;align-items:center;margin-bottom:13px}.gx-color-note{font-size:var(--wb-font-meta);color:var(--wb-ui-secondary);line-height:1.8;margin:10px 0}.gx-type-color-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(155px,1fr));gap:15px;margin:15px 0}.gx-type-color-grid>label{display:flex;align-items:center;gap:10px;color:var(--wb-ui-secondary);font-size:var(--wb-font-control);cursor:pointer}.gx-type-color-grid small{font-size:var(--wb-font-meta);color:var(--wb-ui-muted)}.gx-type-color-sphere{position:relative;display:inline-block;width:32px;height:32px;flex-shrink:0;border-radius:50%;box-shadow:0 3px 7px #2021241c}.gx-type-color-sphere:focus-within{outline:2px solid var(--wb-ui-accent);outline-offset:3px}.gx .gx-type-color-sphere input[type=color]{position:absolute;inset:0;width:100%;height:100%;padding:0;border:0;border-radius:50%;opacity:0;cursor:pointer}@media(max-width:760px){.gx-topline{justify-content:flex-start}.gx-topline>p{flex-basis:100%}}
.gx-scenario-picker{max-width:100%;gap:7px}.gx-scenario-picker select{font:inherit;font-size:var(--wb-font-meta);color:var(--wb-ui-secondary);background:var(--wb-ui-surface-raised);border:1px solid var(--wb-ui-border);border-radius:5px;padding:5px 8px;max-width:400px}.gx-canvas-message{border-radius:0;box-shadow:none;background:var(--wb-ui-surface-raised)}.gx-canvas-message:before,.gx-canvas-message:after{display:none}
</style>
