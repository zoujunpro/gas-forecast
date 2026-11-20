// Keep source files independent. Overlay references may point at existing nodes,
// but overlay records must never replace original nodes, edges or sales readings.
export function mergeGraphDatasets(original,overlay){
  const ids=new Set(original.nodes.map(node=>node.id)),edgeIds=new Set(original.edges.map(edge=>edge.id))
  for(const node of overlay.nodes){if(ids.has(node.id))throw new Error('情景节点 ID 与现有数据冲突');ids.add(node.id)}
  for(const edge of overlay.edges){if(edgeIds.has(edge.id))throw new Error('情景关系 ID 与现有数据冲突');edgeIds.add(edge.id);if(!ids.has(edge.source)||!ids.has(edge.target))throw new Error('情景关系存在无效端点')}
  return {...original,source:'客户销量数据 · 天然气传导情景',sourceSha256:`${original.sourceSha256}:${overlay.sourceSha256}`,nodes:[...original.nodes,...overlay.nodes],edges:[...original.edges,...overlay.edges],scenarios:overlay.scenarios||[],scenarioRules:overlay.rules||[],layoutFile:'/exploration-graph-layout.json',sourceBatches:[{source:original.source,nodeCount:original.nodes.length,edgeCount:original.edges.length,simulation:false},{source:overlay.source,nodeCount:overlay.nodes.length,edgeCount:overlay.edges.length,simulation:true}],references:overlay.references}
}
