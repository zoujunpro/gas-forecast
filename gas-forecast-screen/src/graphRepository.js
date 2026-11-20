import {mergeGraphDatasets} from './graphDatasets.js'
// The page depends on this contract, not the source of the graph.
// A Java API adapter can implement the same methods when Neo4j is connected.
export function createGraphRepository(data) {
  const nodes = data.nodes.map(node => ({ ...node, properties: { ...node.properties } }))
  const edges = data.edges.map((edge, index) => ({ ...edge, id: edge.id || `relation-${index}`, properties: { ...edge.properties } }))
  const byId = new Map(nodes.map(node => [node.id, node]))
  if (byId.size !== nodes.length) throw new Error('图谱数据存在重复节点 ID')
  const adjacency = new Map(nodes.map(node => [node.id, []]))
  const edgeMap = new Map()
  for (const edge of edges) {
    if (!byId.has(edge.source) || !byId.has(edge.target)) throw new Error('图谱关系引用了不存在的节点')
    if (edgeMap.has(edge.id)) throw new Error('图谱数据存在重复关系 ID')
    edgeMap.set(edge.id, edge)
    adjacency.get(edge.source).push(edge)
    if (edge.target !== edge.source) adjacency.get(edge.target).push(edge)
  }
  const scenarioById=new Map((data.scenarios||[]).map(scenario=>[scenario.id,scenario])),scenariosByObject=new Map()
  for(const scenario of data.scenarios||[])for(const id of new Set([...scenario.nodeIds,...scenario.targetIds])){
    if(!byId.has(id))throw new Error('情景引用了不存在的节点')
    scenariosByObject.set(id,[...(scenariosByObject.get(id)||[]),scenario])
  }
  function scenariosFor(id){return [...(scenariosByObject.get(id)||[])].sort((a,b)=>(a.variant==='base'?0:1)-(b.variant==='base'?0:1)||a.id.localeCompare(b.id))}
  const searchText = new Map(nodes.map(n => [n.id, [n.name, n.type, ...Object.values(n.properties)].join(' ').toLowerCase()]))
  const counts = [...nodes.reduce((map, n) => map.set(n.type, (map.get(n.type) || 0) + 1), new Map())].map(([type, count]) => ({ type, count }))
  function search({ query = '', type = '', page = 1, pageSize = 12 } = {}) {
    const terms = query.trim().toLowerCase().split(/\s+/).filter(Boolean)
    const matches = nodes.filter(n => (!type || n.type === type) && terms.every(term => searchText.get(n.id).includes(term)))
    const pages = Math.max(1, Math.ceil(matches.length / pageSize))
    page = Math.max(1, Math.min(pages, page))
    return { nodes: matches.slice((page - 1) * pageSize, page * pageSize), total: matches.length, page, pages }
  }
  function neighborhood(id, { includeValues = false, limit = 18, offset = 0 } = {}) {
    const all = adjacency.get(id) || []
    const eligible = all.filter(e => includeValues || byId.get(e.source).type !== '指标值' && byId.get(e.target).type !== '指标值')
    const neighbors = [...new Set(eligible.map(e => e.source === id ? e.target : e.source))]
    const ids = new Set([id, ...neighbors.slice(offset, offset + limit)])
    return {
      rootId: id, nodes: [...ids].map(key => byId.get(key)).filter(Boolean),
      edges: eligible.filter(e => ids.has(e.source) && ids.has(e.target)),
      totalNeighbors: neighbors.length, offset, limit, hasMore: offset + limit < neighbors.length,
      hiddenValues: new Set(all.filter(e => byId.get(e.source).type === '指标值' || byId.get(e.target).type === '指标值').map(e => e.source === id ? e.target : e.source)).size,
    }
  }
  function paths(id, { depth = 6, limit = 40, scenarioId = '' } = {}) {
    const candidates=scenariosFor(id),scenario=scenarioId?candidates.find(item=>item.id===scenarioId):candidates[0]
    if(scenario){
      const allEdges=scenario.edgeIds.map(key=>edgeMap.get(key)).filter(Boolean)
      let allowed=new Set(scenario.nodeIds)
      // Existing terminals and customers should see their own industry branch,
      // not unrelated industries that happen to share the same event scenario.
      if(allowed.has(id)&&!byId.get(id)?.simulated){
        const ancestors=new Set([id]),up=[id]
        while(up.length){const current=up.shift();for(const edge of allEdges)if(edge.target===current&&!ancestors.has(edge.source)){ancestors.add(edge.source);up.push(edge.source)}}
        const outcomes=[...ancestors].filter(key=>byId.get(key)?.type==='需求变化'),down=[...outcomes]
        allowed=ancestors
        while(down.length){const current=down.shift();for(const edge of allEdges)if(edge.source===current&&!allowed.has(edge.target)){allowed.add(edge.target);down.push(edge.target)}}
      }
      const scopedEdges=allEdges.filter(edge=>allowed.has(edge.source)&&allowed.has(edge.target))
      const layers=new Map([[scenario.eventId,0]]),queue=[scenario.eventId]
      while(queue.length){const current=queue.shift();for(const edge of scopedEdges)if(edge.source===current&&!layers.has(edge.target)){layers.set(edge.target,layers.get(current)+1);queue.push(edge.target)}}
      const eligible=scenario.nodeIds.filter(key=>allowed.has(key)&&(layers.get(key)??Infinity)<=depth),selected=eligible.slice(0,limit),selectedIds=new Set(selected)
      return {nodes:selected.map(key=>byId.get(key)),edges:scopedEdges.filter(edge=>selectedIds.has(edge.source)&&selectedIds.has(edge.target)),layers,scenarioId:scenario.id,hasMore:eligible.length>limit}
    }
    if(scenarioId&&scenarioById.has(scenarioId))return {nodes:[byId.get(id)].filter(Boolean),edges:[],layers:new Map([[id,0]]),hasMore:false}

    const ids = new Set([id]), result = new Map(), layers = new Map([[id, 0]]), queue = [id]
    while (queue.length && ids.size < limit) {
      const current = queue.shift(), level = layers.get(current)
      if (level >= depth) continue
      for (const edge of adjacency.get(current) || []) {
        if (edge.source !== current || !['触发', '传导', '影响', '导致'].includes(edge.type)) continue
        if (!ids.has(edge.target)) {
          if (ids.size >= limit) break
          ids.add(edge.target); layers.set(edge.target, level + 1); queue.push(edge.target)
        }
        result.set(edge.id, edge)
      }
    }
    return { nodes: [...ids].map(key => byId.get(key)).filter(Boolean), edges: [...result.values()], layers, hasMore: ids.size >= limit }
  }
  function sales(id) {
    const readingIds = new Set((adjacency.get(id) || []).filter(e => e.type === '关联').map(e => e.source === id ? e.target : e.source))
    return [...readingIds].map(key => byId.get(key)).filter(n => n.type === '指标值' && n.properties.企业 === byId.get(id)?.name && n.properties.月份 && n.properties.数值 !== '' && Number.isFinite(Number(n.properties.数值))).sort((a, b) => a.properties.月份.localeCompare(b.properties.月份))
  }
  function schema() {
    const relations = new Map()
    for (const edge of edges) {
      const source = byId.get(edge.source).type, target = byId.get(edge.target).type, key = JSON.stringify([source, edge.type, target])
      const row = relations.get(key) || { source, target, type: edge.type, count: 0 }
      row.count++; relations.set(key, row)
    }
    return { nodes: counts, edges: [...relations.values()] }
  }
  return { source: data.source, sourceSha256: data.sourceSha256, period: data.period, notice: data.notice, layoutFile:data.layoutFile||'/customer-graph-layout.json', sourceBatches:data.sourceBatches, nodes, edges, counts, scenariosFor, search, neighborhood, paths, sales, schema, overview: () => ({nodes, edges}), node: id => byId.get(id), edge: id => edgeMap.get(id) }
}

export async function loadGraphRepository() {
  const responses=await Promise.all([fetch('/customer-graph.json'),fetch('/transmission-scenarios.json')])
  if(responses.some(response=>!response.ok))throw new Error('图谱数据加载失败，请刷新重试。')
  const [original,overlay]=await Promise.all(responses.map(response=>response.json()))
  return createGraphRepository(mergeGraphDatasets(original,overlay))
}
