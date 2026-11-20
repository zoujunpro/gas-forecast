import {readFile,writeFile} from 'node:fs/promises'
import {createHash} from 'node:crypto'
import {forceSimulation,forceLink,forceManyBody,forceCollide,forceCenter} from 'd3-force'
import {mergeGraphDatasets} from '../src/graphDatasets.js'
import {overviewPositions} from '../src/graphLayout.js'
const base=new URL('../public/',import.meta.url),bytes=await readFile(new URL('customer-graph.json',base))
const original=JSON.parse(bytes)
await buildLayout(original,'customer-graph-layout.json',createHash('sha256').update(bytes).digest('hex'))
try{
  const overlayBytes=await readFile(new URL('transmission-scenarios.json',base)),overlay=JSON.parse(overlayBytes)
  await buildLayout(mergeGraphDatasets(original,overlay),'exploration-graph-layout.json',createHash('sha256').update(bytes).update(overlayBytes).digest('hex'))
}catch(error){if(error.code!=='ENOENT')throw error}
async function buildLayout(data,filename,graphHash){
  const output=new URL(filename,base)
  try{const cached=JSON.parse(await readFile(output,'utf8'));if(cached.version===1&&cached.graphHash===graphHash){console.log(filename+' 布局缓存已是最新');return}}catch{}
  const seed=overviewPositions(data.nodes)
  const nodes=data.nodes.map(node=>({id:node.id,x:seed.get(node.id)[0],y:seed.get(node.id)[1]}))
  const links=data.edges.map(edge=>({source:edge.source,target:edge.target}))
  const simulation=forceSimulation(nodes).stop().force('link',forceLink(links).id(node=>node.id).distance(65)).force('charge',forceManyBody().strength(-60)).force('collide',forceCollide(22)).force('center',forceCenter(0,0)).alphaDecay(.04).velocityDecay(.5)
  simulation.tick(180)
  const positions=Object.fromEntries(nodes.map(node=>[node.id,[+node.x.toFixed(2),+node.y.toFixed(2)]]))
  await writeFile(output,JSON.stringify({version:1,graphHash,sourceSha256:data.sourceSha256,nodeCount:nodes.length,edgeCount:links.length,positions})+'\n')
  console.log(`已预计算 ${nodes.length} 个节点、${links.length} 条关系的布局`)
}
