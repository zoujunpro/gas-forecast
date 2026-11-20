<template>
  <div class="kl">
    <div class="kl-library-summary"><p>查找业务资料，带入会话继续分析</p><span><b>{{number(documents.length)}}</b> 篇资料</span></div>
    <div class="kl-toolbar">
      <input ref="fileInput" class="kl-hidden-file" type="file" multiple accept=".txt,.md,.markdown,.csv,.json,.pdf,.doc,.docx,.xlsx,.pptx" @change="uploadFiles">
      <label class="kl-search-field"><GraphIcon name="search"/><input v-model="search" placeholder="搜索资料名称、关键词或正文" aria-label="搜索知识库"></label>
      <select v-model="readability" aria-label="筛选资料状态"><option value="">全部状态</option><option value="readable">可阅读</option><option value="pending">待解析</option></select>
      <button class="kl-upload-button" @click="fileInput.click()" :disabled="uploading"><svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><path d="M12 16V3m-4 4 4-4 4 4M4 15v6h16v-6"/></svg>{{uploading?'正在保存…':'上传新知识'}}</button>
    </div>
    <details class="kl-upload-help"><summary>上传格式与存储说明</summary><p>支持 TXT、Markdown、CSV、JSON、PDF、Word、Excel、PPT，单文件不超过 10MB。文本可直接阅读，其他格式待解析；目前文件保存在当前浏览器，尚未上传服务器。内置场景资料按业务模板组织，具体来源可在详情查看。</p></details>
    <p v-if="uploadMessage" role="status" class="kl-upload-message">{{uploadMessage}}</p>
    <div class="kl-layout">
      <aside class="kl-categories" aria-label="资料分类"><p class="kl-category-caption">资料分类</p><button v-for="c in categories" :key="c" :class="{on:category===c}" :aria-pressed="category===c" @click="category=c;active=''"><GraphIcon :name="c==='全部资料'?'library':'folder'"/><b>{{c}}</b><span>{{number(c==='全部资料'?catalog.total:catalog.counts.get(c)||0)}}</span></button></aside>
      <section class="kl-list">
        <template v-if="!selected">
          <div ref="listHeading" class="kl-list-heading"><div><span class="kl-heading-icon"><GraphIcon name="folder"/></span><h3>{{category}}</h3><span>{{loading?'正在加载…':number(result.total)+' 篇资料'}}</span></div><select v-model="order" aria-label="资料排序"><option value="updated">最近更新</option><option value="title">名称排序</option></select></div>
          <div class="kl-table-wrap">
            <table class="kl-table"><colgroup><col><col class="kl-col-category"><col class="kl-col-status"><col class="kl-col-date"><col class="kl-col-actions"></colgroup>
              <thead><tr><th scope="col">资料名称</th><th scope="col">分类</th><th scope="col">状态</th><th scope="col">更新时间</th><th scope="col" class="kl-actions-heading">操作</th></tr></thead>
              <tbody><tr v-for="d in result.documents" :key="d.id" class="kl-entry">
                <td class="kl-entry-main"><div class="kl-entry-content"><span :class="['kl-file-icon','kl-file-'+fileType(d).tone]"><svg viewBox="0 0 28 34" fill="none" aria-hidden="true"><path d="M4 1h13l7 7v24H4V1Z" fill="white" stroke="#d4dae1"/><path d="M17 1v7h7" stroke="#d4dae1"/><path d="M8 13h11M8 17h9" stroke="#cbd3dd"/></svg><b>{{fileType(d).label}}</b></span><div><button class="kl-entry-title" @click="openDocument(d)" :title="d.title">{{d.title}}</button><p :title="d.summary">{{fileType(d).label}}<i>·</i>{{workspaceCopy(d.region||d.source)}}<template v-if="d.period"><i>·</i>{{d.period}}</template></p><small class="kl-mobile-meta">{{d.category}} · {{d.updatedAt}} · {{workspaceCopy(d.status)||'待解析'}}</small></div></div></td>
                <td class="kl-entry-category"><span class="kl-tag">{{d.category}}</span></td>
                <td class="kl-entry-status"><span :class="['kl-status',{'kl-status-pending':!canRead(d)}]"><i></i>{{workspaceCopy(d.status)||(canRead(d)?'可阅读':'待解析')}}</span></td>
                <td class="kl-entry-date">{{d.updatedAt||'—'}}</td>
                <td class="kl-entry-actions"><button @click="openDocument(d)" :aria-label="'查看资料：'+d.title">查看</button><button :disabled="!canRead(d)" @click="ask(d)" :aria-label="'基于此资料提问：'+d.title" :title="canRead(d)?'引用资料到会话':'解析完成后可引用提问'">提问</button></td>
              </tr></tbody>
            </table>
            <p v-if="loading||!result.total" class="kl-empty" role="status">{{loading?'正在加载资料…':error||'暂无匹配资料，试试其他关键词或分类。'}}</p>
          </div>
          <nav class="kl-pagination" aria-label="资料分页">
            <span class="kl-page-range" aria-live="polite">{{number(result.start)}}–{{number(result.end)}} 条，共 {{number(result.total)}} 条</span>
            <label class="kl-page-size">每页<select v-model.number="pageSize" aria-label="每页资料数量"><option :value="20">20 条</option><option :value="50">50 条</option><option :value="100">100 条</option></select></label>
            <div class="kl-page-controls"><button :disabled="result.page===1" aria-label="第一页" @click="goTo(1)">«</button><button :disabled="result.page===1" aria-label="上一页资料" @click="goTo(result.page-1)">‹</button><label><input type="number" :value="result.page" :min="1" :max="result.pages" aria-label="跳转到资料页码" @change="goTo($event.target.value);$event.target.value=result.page" @keydown.enter.prevent="$event.target.blur()"><span>/ {{number(result.pages)}}</span></label><button :disabled="result.page===result.pages" aria-label="下一页资料" @click="goTo(result.page+1)">›</button><button :disabled="result.page===result.pages" aria-label="最后一页" @click="goTo(result.pages)">»</button></div>
          </nav>
        </template>
        <article v-else ref="documentPanel" class="kl-document">
          <button class="kl-back" @click="active=''">‹ 返回资料列表</button><div class="kl-document-heading"><span class="kl-tag">{{selected.category}}</span><h2>{{selected.title}}</h2><p>V{{selected.version}} · 更新于 {{selected.updatedAt}} · {{workspaceCopy(selected.source)}}</p></div>
          <p class="kl-source-note">{{workspaceCopy(selected.sourceNote)}}</p><p v-if="!selected.sections?.length" class="kl-source-note">此文件尚未解析，不能作为问答依据；可下载原始文件。</p>
          <nav v-if="selected.sections?.length" class="kl-toc"><strong>目录</strong><button v-for="(section,i) in selected.sections" :key="section.heading" @click="scrollSection(i)">{{i+1}}. {{section.heading}}</button></nav>
          <section v-for="(section,i) in selected.sections||[]" :key="section.heading" :id="'knowledge-section-'+i"><h3>{{section.heading}}</h3><p>{{selected.source==='用户上传'?section.text:workspaceCopy(section.text)}}</p></section>
          <div class="kl-document-actions"><button :disabled="!selected.sections?.length" @click="ask(selected)">基于此资料提问</button><button @click="download">{{selected.file?'下载原文件':'下载资料 TXT'}}</button></div>
        </article>
      </section>
    </div>
  </div>
</template>
<script setup>
import {ref,shallowRef,computed,onMounted,onBeforeUnmount,nextTick,watch} from 'vue'
import GraphIcon from './GraphIcon.vue'
import {workspaceCopy} from './workspacePresentation.js'
import {knowledgeFiles,readKnowledgeFile} from './knowledgeFiles.js'
import {createKnowledgeCatalog,knowledgePage} from './knowledgeCatalog.js'
import {hydrateKnowledgeDemo,resolveKnowledgeDocument,canReadKnowledge as canRead} from './knowledgeDemo.js'
const emit=defineEmits(['ask'])
const fileInput=ref(),listHeading=ref(),documentPanel=ref(),uploading=ref(false),uploadMessage=ref(''),loading=ref(true)
const documents=shallowRef([]),category=ref('全部资料'),search=ref(''),appliedSearch=ref(''),active=ref(''),error=ref(''),page=ref(1),pageSize=ref(20),order=ref('updated'),readability=ref('')
const categories=['全部资料','政策标准','行业报告','业务规则','模型说明','上传资料']
const catalog=computed(()=>createKnowledgeCatalog(documents.value))
const result=computed(()=>knowledgePage(catalog.value,{query:appliedSearch.value,category:category.value,readability:readability.value,page:page.value,pageSize:pageSize.value,order:order.value}))
const selected=computed(()=>resolveKnowledgeDocument(catalog.value.byId.get(active.value)))
const number=value=>Number(value).toLocaleString('zh-CN')
function fileType(document){const format=(document.format||document.title?.split('.').at(-1)||'').toLowerCase();return ({pdf:{label:'PDF',tone:'red'},doc:{label:'DOC',tone:'blue'},docx:{label:'DOC',tone:'blue'},xlsx:{label:'XLS',tone:'green'},csv:{label:'CSV',tone:'green'},pptx:{label:'PPT',tone:'orange'},json:{label:'JSON',tone:'blue'},txt:{label:'TXT',tone:'gray'},md:{label:'MD',tone:'purple'},markdown:{label:'MD',tone:'purple'}}[format]||{label:'MD',tone:'purple'})}

let searchTimer
watch(search,value=>{clearTimeout(searchTimer);searchTimer=setTimeout(()=>{appliedSearch.value=value;page.value=1;active.value=''},180)})
watch([category,readability,order,pageSize],()=>{page.value=1;active.value=''})
onBeforeUnmount(()=>clearTimeout(searchTimer))
onMounted(async()=>{
 const responses=await Promise.allSettled([
  fetch('/knowledge.json').then(r=>{if(!r.ok)throw Error();return r.json()}),
  fetch('/knowledge-demo.json').then(r=>{if(!r.ok)throw Error();return r.json()}).then(hydrateKnowledgeDemo),
  knowledgeFiles('list').then(rows=>rows.filter(d=>d.scope!=='conversation'))
 ])
 documents.value=responses.flatMap(result=>result.status==='fulfilled'?result.value:[])
 if(responses[0].status==='rejected'||responses[1].status==='rejected')uploadMessage.value='部分资料未能加载，请刷新重试。'
 if(responses[2].status==='rejected')uploadMessage.value='浏览器存储不可用，无法读取上传文件。'
 if(!documents.value.length)error.value='资料加载失败，请刷新重试。'
 loading.value=false
})
async function goTo(value){page.value=Math.max(1,Math.min(result.value.pages,Math.floor(Number(value))||1));await nextTick();listHeading.value?.scrollIntoView({block:'nearest'})}
async function openDocument(document){active.value=document.id;await nextTick();documentPanel.value?.scrollIntoView({block:'start'})}
async function uploadFiles(event){
 uploading.value=true;const results=[]
 for(const file of event.target.files){try{const doc=await readKnowledgeFile(file);await knowledgeFiles('put',doc);documents.value=[...documents.value,doc];results.push(file.name+'：已保存'+(doc.readable?'':'（待解析）'))}catch(error){results.push(error.message||'保存失败')}}
 uploadMessage.value=results.join('；');uploading.value=false;event.target.value='';clearTimeout(searchTimer);search.value=appliedSearch.value='';category.value='上传资料';readability.value='';page.value=1;active.value=''
}
function scrollSection(i){document.getElementById('knowledge-section-'+i)?.scrollIntoView({behavior:'smooth',block:'start'})}
function ask(d=selected.value){d=resolveKnowledgeDocument(d);if(!d?.sections?.length)return;emit('ask',{id:d.id,title:d.title,source:d.source,version:d.version,content:d.sections.map(s=>s.heading+'：'+s.text).join('\n')})}
function download(){const d=selected.value;const text=d.title+'\nV'+d.version+' · '+d.updatedAt+'\n'+d.sourceNote+'\n\n'+(d.sections||[]).map(s=>s.heading+'\n'+s.text).join('\n\n');const url=URL.createObjectURL(d.file||new Blob([text],{type:'text/plain;charset=utf-8'}));const a=document.createElement('a');a.href=url;a.download=d.file?d.title:d.title+'.txt';a.click();URL.revokeObjectURL(url)}
</script>
<style scoped>
.kl { color:var(--wb-ui-text); font-size:var(--wb-font-control); }
.kl button,.kl input,.kl select { font:inherit; }
.kl-hidden-file { display:none!important; }
.kl-library-summary { display:flex; justify-content:space-between; align-items:center; margin:0 0 18px; color:var(--wb-ui-secondary); font-size:var(--wb-font-control); }
.kl-library-summary>span { padding:4px 9px; background:var(--wb-ui-surface); border-radius:6px; font-size:var(--wb-font-meta); }
.kl-library-summary b { color:var(--wb-ui-text); font-weight:500; font-variant-numeric:tabular-nums; }
.kl-toolbar { display:flex; align-items:center; gap:12px; margin-bottom:12px; }
.kl-search-field { display:flex; align-items:center; gap:9px; flex:1; min-width:0; padding:0 13px; border:1px solid var(--wb-ui-border); border-radius:9px; background:var(--wb-ui-surface); color:var(--wb-ui-muted); }
.kl-search-field:focus-within { border-color:var(--wb-ui-border); box-shadow:0 0 0 3px #008e7006; background:var(--wb-ui-surface-raised); }
.kl-search-field svg { width:18px; height:18px; flex-shrink:0; }
.kl-search-field input { width:100%; min-width:0; padding:11px 0; border:0; outline:0; background:transparent; color:var(--wb-ui-text); }
.kl-toolbar>select,.kl-list-heading select { padding:10px 12px; border:1px solid var(--wb-ui-border); border-radius:8px; background:var(--wb-ui-surface-raised); color:var(--wb-ui-text); font-size:var(--wb-font-meta); }
.kl-upload-button { display:inline-flex; justify-content:center; align-items:center; gap:7px; white-space:nowrap; }
.kl-upload-help { margin:0 0 18px; color:var(--wb-ui-muted); font-size:var(--wb-font-meta); }
.kl-upload-help summary { cursor:pointer; width:fit-content; }
.kl-upload-help p { margin:8px 0; padding:10px 13px; border-radius:7px; background:var(--wb-ui-surface); font-size:var(--wb-font-meta); line-height:1.8; }
.kl-upload-message { margin:0 0 16px; padding:10px 14px; border:1px solid var(--wb-ui-border); border-radius:8px; background:var(--wb-ui-surface); color:var(--wb-ui-secondary); font-size:var(--wb-font-meta); }
.kl-layout { display:grid; grid-template-columns:184px minmax(0,1fr); align-items:stretch; gap:0; overflow:hidden; border:1px solid var(--wb-ui-border); border-radius:13px; background:var(--wb-ui-surface-raised); }
.kl-categories { padding:15px 9px; border:0; border-right:1px solid var(--wb-ui-border); background:var(--wb-ui-surface); }
.kl-category-caption { margin:3px 12px 12px; color:var(--wb-ui-muted); font-size:var(--wb-font-meta); }
.kl-categories button { display:flex; align-items:center; gap:9px; width:100%; padding:10px 11px; margin:4px 0; border:0; border-radius:7px; color:var(--wb-ui-secondary); text-align:left; font-size:var(--wb-font-meta); }
.kl-categories button.on { background:var(--wb-ui-selection); color:var(--wb-ui-secondary); }
.kl-categories svg { width:16px; height:16px; flex-shrink:0; color:var(--wb-ui-muted); }
.kl-categories .on svg { color:var(--wb-ui-muted); }
.kl-categories b { font-weight:400; white-space:nowrap; }
.kl-categories button.on b { font-weight:500; }
.kl-categories span { margin-left:auto; font-size:var(--wb-font-meta); color:var(--wb-ui-muted); font-variant-numeric:tabular-nums; }
.kl-categories .on span { color:var(--wb-ui-muted); }
.kl-list { min-width:0; }
.kl-list-heading { display:flex; align-items:center; justify-content:space-between; gap:12px; min-height:66px; padding:14px 20px; border-bottom:1px solid var(--wb-ui-border); }
.kl-list-heading>div { display:flex; align-items:center; gap:9px; }
.kl-heading-icon { display:grid; place-items:center; width:25px; height:25px; color:var(--wb-ui-text); }
.kl-heading-icon svg { width:20px; height:20px; }
.kl-list-heading h3 { margin:0; color:var(--wb-ui-text); font-size:15px; font-weight:600; }
.kl-list-heading span:not(.kl-heading-icon) { color:var(--wb-ui-muted); font-size:var(--wb-font-meta); }
.kl-list-heading select { padding:6px 10px; border-color:transparent; color:var(--wb-ui-text); font-size:var(--wb-font-meta); }
.kl-table-wrap { overflow:auto; max-height:clamp(350px,calc(100dvh - 390px),740px); scrollbar-width:thin; scrollbar-color:var(--wb-ui-border-strong) transparent; }
.kl-table { width:100%; margin:0; border-collapse:separate; border-spacing:0; table-layout:fixed; font-size:var(--wb-font-meta); }
.kl-col-category { width:105px; }.kl-col-status { width:100px; }.kl-col-date { width:120px; }.kl-col-actions { width:125px; }
.kl-table th { position:sticky; top:0; z-index:2; padding:10px 18px; border-bottom:1px solid var(--wb-ui-border); background:var(--wb-ui-surface-raised); color:var(--wb-ui-muted); text-align:left; font-size:var(--wb-font-meta); font-weight:400; }
.kl-table td { padding:10px 18px; border-bottom:1px solid var(--wb-ui-border); color:var(--wb-ui-muted); text-align:left; vertical-align:middle; }
.kl-entry:last-child td { border-bottom:0; }
.kl-entry:hover td { background:var(--wb-ui-selection); }
.kl-entry-content { display:flex; align-items:center; gap:12px; min-width:0; }
.kl-entry-content>div { min-width:0; }
.kl-file-icon { position:relative; display:block; width:28px; height:34px; flex-shrink:0; color:var(--wb-ui-muted); }
.kl-file-icon svg { width:28px; height:34px; }
.kl-file-icon>b { position:absolute; left:0; bottom:4px; padding:0 3px; border-radius:2px; background:var(--wb-ui-primary); color:var(--wb-ui-on-primary); line-height:13px; font-size:7px; font-weight:600; letter-spacing:.2px; }
.kl-file-red>b { background:var(--wb-ui-primary); }.kl-file-blue>b { background:var(--wb-ui-primary); }.kl-file-green>b { background:var(--wb-ui-primary); }.kl-file-orange>b { background:var(--wb-ui-primary); }.kl-file-gray>b { background:var(--wb-ui-primary); }
.kl-entry-title { display:block; max-width:100%; padding:0; margin:0; overflow:hidden; border:0; background:transparent; color:var(--wb-ui-text); text-align:left; font-size:var(--wb-font-body); font-weight:400; text-overflow:ellipsis; white-space:nowrap; }
.kl-entry-title:hover { color:var(--wb-ui-text); }
.kl-entry-content p { margin:4px 0 0; overflow:hidden; color:var(--wb-ui-muted); font-size:var(--wb-font-meta); line-height:1.5; text-overflow:ellipsis; white-space:nowrap; }
.kl-entry-content p i { padding:0 6px; color:var(--wb-ui-muted); font-style:normal; }
.kl-tag { display:inline-flex; padding:2px 6px; border:1px solid var(--wb-ui-border); border-radius:4px; background:var(--wb-ui-surface); color:var(--wb-ui-secondary); font-size:var(--wb-font-meta); white-space:nowrap; }
.kl-status { display:inline-flex; align-items:center; gap:5px; padding:2px 6px; border-radius:4px; background:var(--wb-ui-success-soft); color:var(--wb-ui-success); font-size:var(--wb-font-meta); white-space:nowrap; }
.kl-status i { width:4px; height:4px; border-radius:50%; background:var(--wb-ui-success); }
.kl-status-pending { background:var(--wb-ui-warning-soft); color:var(--wb-ui-warning); }.kl-status-pending i { background:var(--wb-ui-warning-soft); }
.kl-entry-date { color:var(--wb-ui-muted); font-size:var(--wb-font-meta); white-space:nowrap; font-variant-numeric:tabular-nums; }
.kl-table .kl-actions-heading { text-align:right; }
.kl-table .kl-entry-actions { text-align:right; white-space:nowrap; }
.kl-entry-actions button { padding:4px 6px; border:0; border-radius:4px; background:transparent; color:var(--wb-ui-muted); font-size:var(--wb-font-meta); }
.kl-entry-actions button:last-child { margin-left:9px; color:var(--wb-ui-muted); }
.kl-entry-actions button:hover { background:var(--wb-ui-selection); color:var(--wb-ui-secondary); }
.kl-entry-actions button:disabled { opacity:.35; cursor:default; }
.kl-mobile-meta { display:none; }
.kl-pagination { display:flex; flex:none; align-items:center; justify-content:flex-end; gap:16px; margin:0; padding:14px 20px; border-top:1px solid var(--wb-ui-border); color:var(--wb-ui-muted); font-size:var(--wb-font-meta); }
.kl-page-range { margin-right:auto; font-size:var(--wb-font-meta); }
.kl-page-size { display:flex; align-items:center; gap:7px; white-space:nowrap; }
.kl-page-size select { padding:5px 7px; border:1px solid var(--wb-ui-border); border-radius:6px; background:var(--wb-ui-surface-raised); color:var(--wb-ui-secondary); font-size:var(--wb-font-meta); }
.kl-page-controls { display:flex; align-items:center; gap:5px; }
.kl-page-controls button { display:grid; place-items:center; width:25px; height:26px; padding:0; border:1px solid var(--wb-ui-border); border-radius:6px; background:var(--wb-ui-surface-raised); color:var(--wb-ui-muted); font-size:15px; }
.kl-page-controls button:hover { background:var(--wb-ui-selection); }
.kl-page-controls button:disabled { opacity:.35; cursor:default; }
.kl-page-controls label { display:flex; align-items:center; gap:7px; margin:0 4px; font-variant-numeric:tabular-nums; white-space:nowrap; }
.kl-page-controls input { width:42px; padding:4px 2px; border:1px solid var(--wb-ui-border); border-radius:5px; background:var(--wb-ui-surface-raised); color:var(--wb-ui-secondary); text-align:center; font-size:var(--wb-font-meta); appearance:textfield; -moz-appearance:textfield; }
.kl-page-controls input::-webkit-inner-spin-button { appearance:none; }
.kl-empty { padding:50px 15px; margin:0; color:var(--wb-ui-muted); text-align:center; font-size:var(--wb-font-control); }
.kl-document { padding:20px 24px; }
.kl-back { margin:0 0 20px; padding:0; color:var(--wb-ui-secondary); font-size:var(--wb-font-meta); }
.kl-document-heading h2 { margin:14px 0; font-size:23px; color:var(--wb-ui-text); overflow-wrap:anywhere; }
.kl-document-heading p { color:var(--wb-ui-muted); font-size:var(--wb-font-meta); }
.kl-source-note { margin:20px 0; padding:11px 14px; background:var(--wb-ui-warning-soft); border:1px solid var(--wb-ui-border); border-radius:7px; color:var(--wb-ui-warning); font-size:var(--wb-font-meta); line-height:1.8; }
.kl-toc { display:flex; flex:none; flex-direction:column; align-items:flex-start; gap:10px; padding:17px; background:var(--wb-ui-surface); border-radius:8px; }
.kl-toc strong { color:var(--wb-ui-text); font-size:var(--wb-font-control); }.kl-toc button { padding:0; color:var(--wb-ui-secondary); font-size:var(--wb-font-meta); }
.kl-document>section { padding:24px 0 6px; scroll-margin-top:25px; }
.kl-document>section h3 { color:var(--wb-ui-text); font-size:16px; }.kl-document>section p { margin-top:12px; color:var(--wb-ui-secondary); font-size:var(--wb-font-body); line-height:1.9; white-space:pre-wrap; }
.kl-document-actions { display:flex; flex-wrap:wrap; gap:12px; margin-top:28px; padding:23px 0; border-top:1px solid var(--wb-ui-border); }
.kl-document-actions button { padding:9px 16px; border:1px solid var(--wb-ui-border); border-radius:8px; color:var(--wb-ui-secondary); font-size:var(--wb-font-meta); }
@media(max-width:1250px) and (min-width:761px) { .kl-table { min-width:650px; }.kl-col-category{width:90px}.kl-col-status{width:85px}.kl-col-date{width:100px}.kl-col-actions{width:110px}.kl-table th,.kl-table td{padding-left:10px;padding-right:10px} }
@media(max-width:760px) {
 .kl-layout { grid-template-columns:1fr; gap:0; }.kl-categories { display:flex; overflow:auto; gap:3px; padding:7px; border-right:0; border-bottom:1px solid var(--wb-ui-border); }.kl-category-caption { display:none; }.kl-categories button { width:auto; flex-shrink:0; white-space:nowrap; padding:8px 10px; }.kl-categories span { margin-left:0; }.kl-categories svg { display:none; }.kl-list-heading { padding:12px 10px; }.kl-heading-icon { display:none; }.kl-table-wrap { max-height:none; }.kl-document { padding:16px 12px; }
 .kl-toolbar { flex-wrap:wrap; gap:9px; }.kl-search-field { flex-basis:100%; }.kl-toolbar>select { flex:1; }.kl-upload-help { margin-bottom:15px; }.kl-library-summary { margin-bottom:12px; }
 .kl-table,.kl-table tbody { display:block; }.kl-table colgroup,.kl-table thead { display:none; }.kl-table .kl-entry { display:grid; grid-template-columns:minmax(0,1fr) 92px; width:100%; border-bottom:1px solid var(--wb-ui-border); }.kl-table .kl-entry:last-child{border:0}
 .kl-table td { display:block; border:0; padding:12px 10px; }.kl-table .kl-entry-category,.kl-table .kl-entry-status,.kl-table .kl-entry-date { display:none; }.kl-entry-content { gap:9px; }.kl-file-icon { width:27px; height:32px; }.kl-entry-content p { margin-top:4px; }.kl-mobile-meta { display:block; color:var(--wb-ui-muted); font-size:var(--wb-font-meta); margin-top:4px; }
 .kl-table .kl-entry-actions { display:flex; align-items:center; justify-content:flex-end; padding-left:0; }.kl-entry-actions button { padding:4px; }.kl-entry-actions button:last-child { margin-left:4px; }
 .kl-pagination { flex-wrap:wrap; justify-content:space-between; gap:12px; padding:12px 10px; }.kl-page-range { flex-basis:100%; margin:0; }.kl-page-controls { gap:4px; }.kl-page-controls label{gap:4px;margin:0 2px}.kl-page-size { gap:4px; font-size:var(--wb-font-meta); }
}
</style>
