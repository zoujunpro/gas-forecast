import {readFile,writeFile} from 'node:fs/promises'
import {createHash} from 'node:crypto'
const base=new URL('../',import.meta.url)
const documents=JSON.parse(await readFile(new URL('data/transmission-workbook-sources.json',base),'utf8'))
const raw=JSON.parse(await readFile(new URL('public/customer-graph.json',base),'utf8'))
const source=documents.find(book=>book.file==='天然气事件终端传导情景.xlsx'),sheet=source.sheets[0]
const profiles=new Map((await readFile(new URL('data/transmission-profiles.txt',base),'utf8')).split('\n').filter(line=>line.trim()&&!line.startsWith('#')).map(line=>{const [id,industries,group,direction,lag,condition,override]=line.split('|');return [+id,{industries:industries.split(','),group,direction,lag,condition,override}]}))
const regions={pipeline:['河北','河南','湖南'],industrial:['山东','湖北','广东'],coastal:['广东','上海','广西'],heating:['河北','辽宁','宁夏'],rural:['河南','甘肃','贵州'],domestic:['新疆','川渝','青海'],northwest:['新疆','甘肃','宁夏'],hot:['广东','湖北','湖南'],shipping:['上海','广东','广西'],traffic:['山东','河南','广东']}
const variants=[{key:'mild',name:'温和',strength:'轻',assumption:'驱动因素小幅变化，库存与可替代资源能够吸收大部分冲击'},{key:'base',name:'典型',strength:'中',assumption:'驱动因素持续变化，库存与替代资源部分缓冲，适用条件成立'},{key:'stress',name:'强化',strength:'强',assumption:'驱动因素较大变化且持续，边际缓冲能力偏弱，仍须满足适用条件'}]
const terminalNames={城市燃气:'居民与商业用气',工业燃料:'工业燃料用气',发电:'燃气发电用气',化工:'化工原料用气',化肥:'合成氨与化肥用气',CNG:'CNG加气终端需求',LNG装车:'LNG配送与加注需求',LNG液化厂:'液化装置原料气需求'}
const actions={城市燃气:'核实终端负荷与日供气计划',工业燃料:'核实排产与燃料替代方案',发电:'核实调度电量与气源保障',化工:'核实装置开工与原料采购',化肥:'核实化肥订单与原料气计划',CNG:'核实站点供气与加气计划',LNG装车:'核实装车配送与终端加注需求',LNG液化厂:'核实液化负荷与原料气合同'}
const perturbations={
  1:['新增输气能力',[10,20,35],'%'],2:['管输费变动',[-3,-8,-15],'%'],16:['可售LNG到港量变化',[5,12,25],'%'],18:['对应气源可交付量变化',[-5,-15,-30],'%'],25:['到货延迟',[2,5,10],'天'],26:['可用储气缺口',[5,15,25],'%'],28:['油价挂钩结算基准变化',[-5,-12,-25],'%'],29:['油价挂钩结算基准变化',[5,12,25],'%'],30:['LNG现货采购成本变化',[5,15,30],'%'],32:['人民币对美元贬值幅度',[2,5,10],'%'],33:['采购门站价调整',[3,8,15],'%'],34:['采购门站价调整',[-3,-8,-15],'%'],39:['碳价增量',[15,30,60],'元/吨二氧化碳'],45:['计划停输时长',[1,3,7],'天'],53:['工业开工率变化',[3,7,12],'个百分点'],54:['工业开工率变化',[-3,-7,-12],'个百分点'],55:['气电可调度发电量变化',[5,12,25],'%'],56:['气电利用小时变化',[-5,-12,-25],'%'],57:['相对季节基准降温幅度',[3,6,10],'℃'],58:['相对季节基准升温幅度',[2,4,6],'℃'],59:['相对季节基准升温幅度',[2,4,6],'℃'],68:['天然气制氢原料替代比例',[5,15,30],'%'],71:['可采购煤炭价格变化',[-5,-15,-25],'%'],72:['可采购煤炭价格变化',[5,15,30],'%'],74:['柴油单位价格变化',[-5,-12,-20],'%'],75:['柴油单位价格变化',[5,12,20],'%'],78:['既有燃气采暖负荷替代比例',[5,15,30],'%'],86:['可替代线路LNG里程占比变化',[-5,-15,-30],'个百分点'],88:['受影响资源可交付量变化',[-5,-15,-30],'%'],94:['航线到货延迟',[3,7,14],'天'],95:['可调储气补充量占缺口比例',[20,50,80],'%'],96:['设施停运时长',[1,3,7],'天']
}
const nodes=[],edges=[],scenarios=[],rules=[],corrections=[]
let category=''
const find=(type,name)=>raw.nodes.find(node=>node.type===type&&node.name===name)
const addNode=(id,name,type,properties,context={})=>{nodes.push({id,name,type,properties,simulated:true,...context});return id}
const addEdge=(id,from,to,type,properties,context={})=>{edges.push({id,source:from,target:to,type,properties,simulated:true,...context});return id}
const split=text=>String(text).split(/\s*[→⇒]\s*/).map(value=>value.trim()).filter(Boolean)
const references=[{id:'eia-demand',title:'EIA — Factors affecting natural gas prices',url:'https://www.eia.gov/energyexplained/natural-gas/factors-affecting-natural-gas-prices.php',scope:'温度、电力负荷、产量、库存与可替代燃料等一般机制，不证明任何具体情景已发生'},{id:'eia-carbon',title:'EIA — Carbon dioxide emissions coefficients',url:'https://www.eia.gov/environment/emissions/co2_vol_mass.php',scope:'相同热量下燃烧排放系数比较；不等同于全生命周期排放或具体企业碳成本'}]
for(const sourceRow of sheet.rows){
 const [sequence,name,path,impact]=sourceRow.values
 if(typeof sequence==='string'&&sourceRow.values.slice(1).every(value=>value===null)){category=sequence.replace(/^[一二三四五六七八九十]+、/,'');continue}
 if(typeof sequence!=='number')continue
 const profile=profiles.get(sequence);if(!profile)throw new Error(`缺少情景 ${sequence} 的业务适用配置`)
 const ruleId=`SIM_RULE_${String(sequence).padStart(3,'0')}`,mechanisms=split(profile.override||path)
 const rule={id:ruleId,sequence,name,category,industries:profile.industries,regions:regions[profile.group],direction:profile.direction,lag:profile.lag,condition:profile.condition,sourceFile:source.file,sourceSheet:sheet.name,sourceRow:sourceRow.row,sourceRange:`A${sourceRow.row}:D${sourceRow.row}`,originalPath:path,originalImpact:impact,mechanisms,reviewed:Boolean(profile.override)}
 rules.push(rule)
 if(profile.override)corrections.push({ruleId,sequence,originalPath:path,normalizedPath:profile.override,reason:'补充必要条件或修正不成立的必然方向，原表原文保留供核对',referenceIds:sequence===39?['eia-carbon','eia-demand']:['eia-demand']})
 addNode(ruleId,name+'传导规则','传导规则',{'数据类型':'情景规则','情景分类':category,'生效条件':profile.condition,'终端方向':profile.direction,'时滞假设':profile.lag,'原表传导路径':path,'原表终端影响':impact,'采用机制':mechanisms.join(' → '),'规则来源':`${source.file} · ${sheet.name}!${rule.sourceRange}`,'验证状态':'待业务验证'},{sourceFile:source.file,sourceRow:sourceRow.row})
 for(const [regionIndex,region] of regions[profile.group].entries())for(const variant of variants){
  const scenarioId=`SCN_${String(sequence).padStart(3,'0')}_${regionIndex+1}_${variant.key}`,eventId=`${scenarioId}_EVENT`,nodeIds=[],edgeIds=[],targetIds=new Set()
  const perturbation=perturbations[sequence],variantIndex=variants.indexOf(variant)
  const assumption=perturbation?{variable:perturbation[0],value:perturbation[1][variantIndex],unit:perturbation[2],basis:'人为设定的情景输入，不是监测值，不用于估计真实销量'}:{variable:'驱动条件强度',value:variant.strength,unit:'情景等级',basis:'定性业务假设，无定量效应系数'}
  const context={scenarioId,sourceFile:source.file,sourceRow:sourceRow.row,ruleId}
  const common={'数据类型':'情景推演','适用地区':region,'情景强度':variant.strength,'终端方向':profile.direction}
  const add=(suffix,label,type,properties={})=>{const id=addNode(`${scenarioId}_${suffix}`,label,type,{...common,...properties},context);nodeIds.push(id);return id}
  const link=(from,to,type,explanation,extra={})=>{const id=addEdge(`${scenarioId}_REL_${edgeIds.length+1}`,from,to,type,{'数据类型':'情景推演','作用机制':explanation,'生效条件':profile.condition,'时滞假设':profile.lag,'验证状态':'待业务验证',...extra},context);edgeIds.push(id);return id}
  add('EVENT',`${region} · ${name}（${variant.name}）`,'事件',{'情景分类':category,'适用行业':profile.industries.join('、'),'情景假设':variant.assumption,'扰动参数':`${assumption.variable}：${assumption.value}${assumption.unit}`,'参数口径':assumption.basis,'生效条件':profile.condition,'时滞假设':profile.lag,'规则来源':`${source.file} · ${sheet.name}!${rule.sourceRange}`,'验证状态':'待业务验证'})
  let last=eventId
  for(const [index,label] of mechanisms.entries()){
   const node=add(`FACTOR_${index+1}`,label,'驱动因素',{'机制说明':label,'生效条件':profile.condition})
   link(last,node,index===0?'触发':'传导',`${rawLabel(last)}使${label}发生条件性变化`);last=node
  }
  for(const [industryIndex,industry] of profile.industries.entries()){
   const suffix=profile.direction==='条件性'?'随条件调整':profile.direction==='稳供'?'保障能力改善':`需求${profile.direction}`
   const outcomeName=(terminalNames[industry]||industry)+(profile.direction==='条件性'?'随条件调整':profile.direction==='稳供'?'保障能力改善':profile.direction)
   const outcome=add(`OUTCOME_${industryIndex+1}`,outcomeName,'需求变化',{'适用行业':industry,'终端方向':profile.direction,'判定前提':profile.condition,'影响口径':industry==='LNG装车'?'LNG配送对应终端加注或用气需求；不直接视为CNG销量':'情景条件下的终端需求或可用供气变化，不是企业实绩'})
   link(last,outcome,'影响',`${mechanisms.at(-1)}经价格、负荷或供给约束传递至${industry}，${suffix}`)
   const action=add(`ACTION_${industryIndex+1}`,actions[industry]||'复核供需计划','业务行动',{'适用行业':industry,'业务建议':actions[industry]||'复核供需计划'})
   link(outcome,action,'触发','终端需求或供气变化触发计划复核；本数据不执行调度或采购指令')
   const anchor=find('行业',industry),factory=raw.nodes.find(node=>node.type==='行业工厂'&&node.properties.类型===industry)
   for(const target of [anchor,factory].filter(Boolean)){
    targetIds.add(target.id);nodeIds.push(target.id)
    link(outcome,target.id,'影响',`按行业类型匹配的情景适用关系，范围限定为${region}；不代表此对象已发生实绩变化`,{'匹配依据':`行业类型=${industry}；地区情景=${region}`})
   }
   // Company selection requires BOTH industry and province. It never changes monthly readings.
   const customers=raw.nodes.filter(node=>node.type==='企业'&&node.properties.层级==='三级'&&node.properties.行业类别===industry&&node.properties.所在地===region).slice(0,2)
   for(const customer of customers){targetIds.add(customer.id);nodeIds.push(customer.id);link(outcome,customer.id,'影响','按行业与所在地双重匹配的适用情景，不认定企业实际受影响',{'匹配依据':`行业=${industry}；所在地=${region}`})}
  }
  const regionNode=find('地点',region),commodity=find('品类','管道天然气')
  for(const [target,type] of [[regionNode,'适用地区'],[commodity,'关联品类']])if(target){targetIds.add(target.id);addEdge(`${scenarioId}_CONTEXT_${type}`,eventId,target.id,type,{'数据类型':'情景推演'},context)}
  targetIds.add(ruleId);addEdge(`${scenarioId}_ADOPT_RULE`,eventId,ruleId,'采用规则',{'数据类型':'情景推演'},context)
  scenarios.push({id:scenarioId,eventId,ruleId,name:`${name} · ${region} · ${variant.name}`,category,region,variant:variant.key,strength:variant.strength,assumption,direction:profile.direction,industries:profile.industries,nodeIds:[...new Set(nodeIds)],edgeIds,targetIds:[...targetIds],sourceFile:source.file,sourceRow:sourceRow.row})
 }
}
function rawLabel(id){return nodes.findLast(node=>node.id===id)?.name||id}
const generated={version:1,datasetId:'gas-transmission-scenarios-v1',source:'天然气事件终端传导情景',simulation:true,notice:'依据情景规则与业务假设生成，不代表已发生事件、已验证因果或真实销量预测。',sources:documents.map(book=>({file:book.file,sha256:book.sha256,role:book===source?'情景原文及来源行号':'项目范围参考，不生成已发生事件'})),references,sourceAudit:{detailRows:rules.length,summaryDeclaredTotal:102,adoptedBasis:'逐条明细',corrections},rules,scenarios,nodes,edges}
generated.sourceSha256=createHash('sha256').update(JSON.stringify({rules,scenarios,nodes,edges})).digest('hex')
await writeFile(new URL('public/transmission-scenarios.json',base),JSON.stringify(generated)+'\n')
console.log(JSON.stringify({rules:rules.length,scenarios:scenarios.length,nodes:nodes.length,edges:edges.length,corrections:corrections.length},null,2))
