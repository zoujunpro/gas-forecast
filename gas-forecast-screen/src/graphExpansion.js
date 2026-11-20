// Neighborhood batches may share endpoints and relations. Keep each original ID once.
export function mergeNeighborhoods(batches){
  const nodes=new Map(),edges=new Map()
  for(const batch of batches){
    for(const node of batch.nodes)nodes.set(node.id,node)
    for(const edge of batch.edges)edges.set(edge.id,edge)
  }
  return {nodes:[...nodes.values()],edges:[...edges.values()]}
}
