import {gasEventBriefAsOf,gasEventBriefSources} from './knowledgeEventBrief.js'

// A new version adds the event-analysis conversation without overwriting earlier chats.
export const knowledgeConversationSessionId='wb-knowledge-event-impact-v2'
export const knowledgeConversationSeedKey='wb-knowledge-event-impact-seeded-v2'

export function knowledgeRetrievalConversation(documents,scene,now=Date.now()) {
 if(!Array.isArray(documents)||scene?.id!=='知识问答')return null
 const knowledge=documents.filter(document=>['gas-industry','graph-guide'].includes(document.id))
 const sources=JSON.parse(JSON.stringify(gasEventBriefSources))
 const [winter,production,international]=sources
 const messages=[
  {role:'user',text:'最近有什么大事情，对燃气业务有什么影响？别只给我新闻，帮我看一下需求、气源和价格。'},
  {role:'assistant',agent:'WorkBuddy · 资讯与知识检索',sources,text:`我重点看了国内采暖需求、国内供给和国际 LNG 市场三条线。以下依据截至 ${gasEventBriefAsOf} 已核对的公开资料整理。\n\n1. 近期寒潮与入冬保供\n新华网 11 月 17 日报道，寒潮带动用气增加，相关企业正推进资源筹措与调峰准备。对业务来说，首先要关注采暖相关客户的日峰值，以及可用气源能否匹配高峰时段。\n\n2. 国际 LNG 供应与欧洲库存压力\nIEA 11 月 3 日报告提示，国际 LNG 流向受扰叠加欧洲储气缓冲偏低，可能加剧灵活货源竞争。需要关注现货补库成本和到货安排，不宜直接推定所有国内客户的气价都会上涨。\n\n3. 国内天然气生产的新数据\n国家统计局 11 月 16 日公布，10 月规上工业天然气产量为 222 亿立方米，同比增长 0.8%。这提供了国内生产端的观察依据，但不能据此认定某个地区或客户没有保供压力。\n\n我的初步判断：先核对采暖高峰的气量保障，再关注 LNG 现货敞口和工业客户的用气变化。下面的业务影响属于基于这些事实的条件分析。`},
  {role:'user',text:'所以接下来气价是不是就要涨？我们需要提前多买一点吗？'},
  {role:'assistant',agent:'WorkBuddy · 供需与价格研判',sources:[international,winter],text:'目前还不能直接下“气价一定涨、需要多买”的结论，要先区分我们说的是哪一类气源和哪一种价格。\n\n如果补充气源主要依赖 LNG 现货，国际货源竞争和运输变化可能影响到岸成本与临时补库报价；如果主要是已落实的合同气，实际成本还要看合同约定的定价方式和执行气量。\n\n对销售端，也要分别看采购成本、合同销售价和实际结算周期，不能把上游价格变化直接套到每个客户。\n\n更适合先做的是：列清已落实气量、预计高峰需求、未覆盖气量和可调用调峰资源，再核对到货日期与价格敞口。只有确认存在缺口或集中到货风险，才有依据讨论是否调整采购安排。'},
  {role:'user',text:'那对我们江苏的城燃和工业客户，谁的影响会更大？'},
  {role:'assistant',agent:'WorkBuddy · 客户影响分析',text:'两类客户的传导方式不同，不能简单按同一个比例上调需求。\n\n城燃客户：先识别居民、商业和采暖负荷占比，核对服务区域是否确有采暖需求，再看最低气温、连续降温天数和同类天气下的历史峰值。江苏不同地区的采暖方式和客户结构存在差异，不能直接套用北方集中供暖规律。\n\n工业客户：更应关注企业开工、班次、排产、检修和燃料替代条件。即使气温下降，客户处于停产或减产阶段，用气也未必增加；采购成本波动是否改变用气，还取决于合同和工艺约束。\n\n可以优先筛查三类对象：采暖负荷较高的城燃客户、依赖现货补充气源的客户、近期排产或停复产变化明显的工业客户。具体名单需结合客户档案、供气结构和近期负荷确认，不能仅从新闻推定。'},
  {role:'user',text:'帮我把影响链讲清楚。能直接给一个下周销量增长多少的判断吗？'},
  {role:'assistant',agent:'WorkBuddy · 事件传导分析',text:'可以先梳理传导方向，再用业务数据估算幅度。目前这些公开资料不足以支持一个可信的“下周增长百分比”。\n\n天气链：持续降温 → 实际采暖负荷变化 → 客户日度需求与峰值变化。\n验证重点是采暖客户范围、当地天气、历史敏感性，以及变化发生的日期是否一致。\n\n气源链：国际 LNG 供应扰动 → 灵活货源竞争 → 到岸成本或到货安排变化 → 现货敞口客户的采购压力。\n验证重点是气源来源、合同覆盖、交付窗口和可用替代资源。\n\n工业链：气源成本或供应约束变化 → 企业根据工艺、订单和合同调整生产安排 → 实际用气变化。\n中间环节是否发生，需要客户排产和实测销量验证。\n\n进入预测时，应先统一客户与时间范围，使用可获得的天气、历史销量和已确认的排产信息进行模型计算；没有核实的事件影响可单独保留为情景条件，不宜当成已发生的销量增幅。'},
  {role:'user',text:'有没有可能新闻看起来很严重，我们的销量反而没涨，甚至下降？'},
  {role:'assistant',agent:'WorkBuddy · 交叉验证',text:'有可能，事件的影响方向和最终销量并不总是一致。\n\n例如，降温确实带来部分采暖需求，但工业客户同时检修或减产，合计销量可能基本不变；某类气源采购成本上升，也可能促使具备替代条件的企业调整燃料或排产，用气反而回落。\n\n还有两种常见偏差：一是把全国性新闻当成江苏每个客户都已受影响，二是把采购或库存变化直接当成终端消费变化。实际复核时，要分别看地区、客户类型和日期，并把进销存口径分开。\n\n因此，我会把“新闻事实”“待验证的传导条件”和“模型预测结果”分别保留。看到与预期不一致的销量变化时，优先检查客户范围、排产与数据截止时间，再解释原因。'},
  {role:'user',text:'整理一段今天例会能用的结论，再告诉我接下来先查哪些数据。'},
  {role:'assistant',agent:'WorkBuddy · 业务摘要',sources:[winter,production,international],text:`例会可以这样汇报：\n\n“近期天然气业务需要同时关注国内采暖季高峰与国际 LNG 市场风险。需求端应区分城燃采暖负荷和工业排产变化，供应端应核对合同覆盖、现货敞口与交付窗口。国内生产数据提供了供给观察依据，但不能代替本地区可用气量。对江苏客户，先按客户结构核对天气与近期负荷，再形成分客户预测和情景分析，暂不依据单条新闻统一上调用气或价格预期。”\n\n接下来先查四组数据：\n1. 客户结构：客户编码、服务地区、采暖占比、供气来源和合同覆盖。\n2. 需求变化：近 30—90 天日销量、峰值记录，以及已确认的排产、停复产和检修安排。\n3. 天气输入：客户所在地未来 7—10 天预报，保留数据发布时间。\n4. 气量保障：同期已落实气量、到货计划、可用调峰能力和暂未覆盖气量。\n\n拿到这些数据后，可以先做分客户日度预测，再汇总区域峰值并核对供需匹配。需求增长、价格变化和供应缺口分别给出依据，避免混为一个结论。\n\n本轮公共资讯口径截至 ${gasEventBriefAsOf}，后续天气与市场变化需使用更新资料核对。`}
 ]
 return {
  id:knowledgeConversationSessionId,title:'近期大事 · 天然气业务影响研判',scene:scene.id,updatedAt:now,
  autoTitle:false,customTitle:true,seeded:true,simulated:true,openAtStart:true,asOf:gasEventBriefAsOf,sourceFile:'knowledgeEventBrief.js',sources,knowledgeSources:JSON.parse(JSON.stringify(knowledge)),
  parameters:{[scene.id]:{object:scene.objects.includes('专业知识')?'专业知识':scene.objects[0],period:scene.periods[0],industry:'全部行业',supply:''}},
  input:'',knowledge:null,graphContext:'',attachments:[],result:null,
  messages
 }
}
