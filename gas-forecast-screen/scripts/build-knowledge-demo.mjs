import {readFile,writeFile} from 'node:fs/promises'
import {createKnowledgeDemoManifest} from '../src/knowledgeDemo.js'
const target=new URL('../public/knowledge-demo.json',import.meta.url)
const manifest=createKnowledgeDemoManifest(),content=JSON.stringify(manifest)
let previous;try{previous=await readFile(target,'utf8')}catch{}
if(previous!==content)await writeFile(target,content)
console.log(`知识库演示资料 ${manifest.count.toLocaleString()} 条，索引 ${(Buffer.byteLength(content)/1024).toFixed(0)} KB`)
