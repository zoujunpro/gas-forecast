// Deterministic, explicitly synthetic records for UI scale and workflow validation.
export const knowledgeDemoProfiles = [
 ['采暖需求分析','行业报告','md','识别气温、采暖方式与城燃需求的关联。','分析最低气温、采暖度日和采暖客户范围，并匹配相同日期的用气记录。','在确认采暖方式、客户类型和供暖安排后，再讨论天气敏感性；工业负荷变化需另行核查。'],
 ['工业开工与排产跟踪','行业报告','xlsx','整理开工、排产与生产负荷的分析要点。','核对开工率、班次、停复产和产能利用率，并明确统计对象与口径。','将排产变化与同一企业的历史销量匹配；区域景气变化不能直接替代客户实绩。'],
 ['燃气发电需求研判','行业报告','pdf','关注电力负荷、机组运行与替代能源出力。','检查电力负荷、机组检修、调度计划及其他能源出力。','天然气需求同时受到机组效率、燃料价格和调度约束影响，不能只依据用电量下结论。'],
 ['LNG终端需求观察','行业报告','pptx','整理交通、工业与终端库存的观察维度。','区分重卡、船用及工业终端，分别核对补库、出勤、运输和替代燃料条件。','LNG终端不直接映射到CNG加气站；需求变化需与行业及地区范围相匹配。'],
 ['能源替代条件分析','行业报告','md','比较电能替代、燃料价格与设备约束。','核查电价、气价、设备效率、改造进度和生产工艺。','只有替代设备投运且满足工艺约束时，才讨论替代方向，不从价格变化直接推定销量比例。'],
 ['销量计量口径','业务规则','pdf','统一气量单位、标准状态及统计边界。','汇总前核查立方米、万方、亿方和标准状态，明确起止时间及去重键。','保持客户、地区、行业和时间窗口一致，避免多层级重复汇总。'],
 ['客户档案核验','业务规则','xlsx','确认企业名称、编码、行业和所在地区。','先确认唯一客户编码，再核对组织层级、行业分类和供用气对象。','同名或名称近似的对象需人工确认，不由展示名称直接推断组织归属。'],
 ['合同与采购计划核对','业务规则','md','核对合同边界、采购安排与业务执行条件。','整理合同生效区间、计量条款、采购计划和计划调整记录。','计划与实际执行记录分开保存；未取得原始合同的条款不作确定性引用。'],
 ['库存与调峰协同','业务规则','xlsx','梳理库存、可用气源与调峰安排。','区分账面库存、可用库存、出入库限制和调峰计划。','供需缺口分析需要同一时段的可用供气量与需求，不能以库存总量代替每日供气能力。'],
 ['检修与限供协同','业务规则','pdf','核对检修窗口、输送能力及客户保障范围。','确认检修对象、持续时间、能力变化、备用路线和通知范围。','检修可能影响可用气量，实际影响还取决于备用气源与调配结果。'],
 ['短期客户预测方法','模型说明','md','核查客户历史销量、天气输入与预测周期。','确认客户编码、数据截止时间、日度频率和预测区间。','训练与回测严格按时间拆分，避免使用预测时点之后的信息。'],
 ['冬季保供预测方法','模型说明','pdf','按区域、时段评估需求与保供约束。','统一预测区域与旬度范围，核查天气输入和可用气源。','预测需求与已确认供气量分别展示；缺少供气量时不生成确定性缺口结论。'],
 ['月度销量预测方法','模型说明','pptx','梳理季节性、行业和月度汇总口径。','检查月度完整性、行业结构、历史趋势和节假日安排。','非完整月份需要标明截止日期，不与完整月份直接计算增长率。'],
 ['多模型回测评估','模型说明','xlsx','使用MAE、RMSE和WAPE比较候选模型。','MAE与RMSE和销量同单位，WAPE为百分比；三者越低越好。','在相同对象、样本窗口和单位下比较；不能把100%-WAPE当作通用准确率。'],
 ['特征与数据质量检查','模型说明','md','检查缺失、异常、时序对齐与特征可用性。','核查缺失值、异常值、滞后特征和天气数据在预测时点的可获得性。','特征重要性是相对程度，不能直接解释为销量增幅或因果贡献。'],
 ['保供资料核验清单','政策标准','pdf','整理保供文件、适用范围与有效期核验要点。','需核验正式发布机构、发布日期、有效期、适用地区和原文。','本条是资料核验清单，不替代政策原文，也不构成已发布政策。'],
 ['计量标准核验清单','政策标准','pdf','列出计量文件原文及适用口径的核验项。','核查标准原文、版本、适用设备和标准状态。','没有正式原文时不编造文号、标准编号或具体条款。'],
 ['安全运行资料清单','政策标准','md','核对安全运行文件与检修管理资料。','整理正式制度、检修安排、应急预案和责任范围。','现场执行以正式制度和专业人员核查为准，此条目不提供现场操作指令。'],
 ['价格与管输规则核验','政策标准','pdf','记录价格、管输费用的适用范围和生效时间。','区分公告价格、合同价格、管输费用及税费口径。','未取得正式文件时不填写真实费率，经济性变化需要结合合同与设备效率分析。'],
 ['引用与来源记录规范','政策标准','md','规范资料来源、版本与证据链记录。','引用时保存原文、来源、版本、发布日期、适用对象和有效范围。','将原始记录、规则推演和模型结果区分保存，无法核验的信息保留未决状态。']
].map(([name,category,format,summary,check,method],index)=>({id:'topic-'+index,name,category,format,summary,check,method}))
export const knowledgeDemoRegions=['江苏','浙江','安徽','上海','山东','河南','河北','北京','天津','山西','陕西','甘肃','宁夏','青海','新疆','内蒙古','辽宁','吉林','黑龙江','湖南','湖北','江西','四川','重庆','广东']
export function createKnowledgeDemoManifest() {
 const documents=[]
 for(let month=39;month>=0;month--)for(let region=0;region<25;region++)for(let topic=0;topic<20;topic++){
  const id=documents.length+1,date=new Date(Date.UTC(2023,7+month,1)),period=date.toISOString().slice(0,7)
  documents.push([id,topic,region,period,month===39?20:28,id%37!==0])
 }
 return {version:1,simulated:true,count:documents.length,description:'业务模板 × 25个地区 × 40个月；仅用于展示及交互验证',records:documents}
}
export function hydrateKnowledgeDemo(manifest) {
 if(manifest.version!==1||manifest.simulated!==true||manifest.count!==manifest.records?.length)throw Error('演示资料索引格式不正确')
 return manifest.records.map(([id,topic,region,period,day,readable])=>{
  const profile=knowledgeDemoProfiles[topic],place=knowledgeDemoRegions[region]
  if(!profile||!place)throw Error('演示资料模板不存在')
  return {id:'knowledge-demo-'+String(id).padStart(5,'0'),title:`${place} · ${profile.name} · ${period}`,category:profile.category,summary:profile.summary,format:profile.format,region:place,period,profileId:profile.id,version:'1.0',updatedAt:`${period}-${day}`,source:'业务演示资料集',status:readable?'可阅读':'待解析',readable,simulated:true,searchText:readable?profile.check+' '+profile.method:'',sourceNote:'该条目由业务模板、地区和月份组合生成，用于页面展示与交互验证，不是已发生业务记录或正式文件。列表文件类型用于展示，下载提供生成的文本内容。'}
 })
}
export function canReadKnowledge(document) {return Boolean(document?.sections?.length||document?.readable&&document?.simulated&&document?.profileId)}
export function resolveKnowledgeDocument(document) {
 if(!document?.simulated||!document.profileId||!document.readable||document.sections?.length)return document
 const profile=knowledgeDemoProfiles.find(p=>p.id===document.profileId)
 if(!profile)throw Error('资料模板不存在')
 return {...document,sections:[{heading:'资料范围',text:`适用对象：${document.region}天然气业务。分析时段：${document.period}。${profile.summary}`},{heading:'核对要点',text:profile.check},{heading:'分析方法与限制',text:profile.method},{heading:'来源说明',text:document.sourceNote}]}
}
