// Deterministic seeds for the force layout; every original node is retained.
export function overviewPositions(nodes) {
  const angle=Math.PI*(3-Math.sqrt(5))
  return new Map(nodes.map((node,index)=>{
    const radius=24*Math.sqrt(index+.5)
    return [node.id,[radius*Math.cos(index*angle),radius*Math.sin(index*angle)]]
  }))
}

// A cached layout stores coordinates only. It cannot add, remove or rewrite graph data.
export function cachedOverviewPositions(cache,repository){
  if(cache?.version!==1||cache.sourceSha256!==repository.sourceSha256||cache.nodeCount!==repository.nodes.length||cache.edgeCount!==repository.edges.length)return null
  const positions=new Map()
  for(const node of repository.nodes){
    const point=cache.positions?.[node.id]
    if(!Array.isArray(point)||point.length!==2||!point.every(Number.isFinite))return null
    positions.set(node.id,point)
  }
  return positions
}
