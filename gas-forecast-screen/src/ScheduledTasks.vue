<template>
  <div class="st">
    <div class="st-toolbar"><div><p>管理预测任务的周期、时间与分析需求</p><span><GraphIcon name="时间"/>北京时间 · UTC+8</span></div><button ref="createButton" @click="create">创建定时任务</button></div>
    <section class="st-workspace" aria-label="定时任务配置">
      <div class="st-list-toolbar">
        <div class="st-filters" role="group" aria-label="任务配置状态"><button v-for="item in filters" :key="item.value" :class="{chosen:filter===item.value}" :aria-pressed="filter===item.value" @click="filter=item.value">{{item.label}}<span>{{item.count}}</span></button></div>
        <label class="st-search"><GraphIcon name="search"/><input v-model="query" aria-label="搜索定时任务" placeholder="搜索任务名称或对象"></label>
      </div>
      <div class="st-table-heading" aria-hidden="true"><span>任务名称 / 预测对象</span><span>计划时间</span><span>配置状态</span><span>操作</span></div>
      <div v-if="loading" class="st-empty">正在加载任务…</div>
      <div v-else-if="!filteredTasks.length" class="st-empty"><GraphIcon name="时间"/><h3>{{tasks.length?'暂无匹配任务':'还没有定时任务'}}</h3><p>{{tasks.length?'试试其他关键词或配置状态。':'创建任务，或从下方模板开始。'}}</p><button v-if="query||filter" @click="query='';filter=''">清除筛选</button></div>
      <article v-for="task in filteredTasks" :key="task.id" class="st-task-row">
        <div class="st-task-main"><span :class="['st-scene-icon',sceneStyle(task.scene)]"><GraphIcon :name="sceneIcon(task.scene)"/></span><div><h3>{{task.name}}</h3><p><span>{{task.scene}}</span><i>·</i><span :title="task.object">{{task.object}}</span><i>·</i><span>{{task.horizon}}</span></p><small class="st-task-origin">{{task.builtin?'预置任务':'自定义任务'}}</small></div></div>
        <div class="st-plan"><b>{{task.time}}</b><span>{{frequencyLabel(task)}}</span></div>
        <div class="st-state"><span :class="['st-state-label',{enabled:task.enabled}]"><i></i>{{task.enabled?'配置已启用':'未启用'}}</span><small>{{task.enabled?'待接入调度':'尚无执行记录'}}</small></div>
        <div class="st-task-actions"><button class="st-toggle" role="switch" :aria-checked="Boolean(task.enabled)" :aria-label="(task.enabled?'停用配置：':'启用配置：')+task.name" :title="task.enabled?'停用此配置':'启用此配置，实际执行待接入调度'" @click="toggle(task)"><span></span></button><span class="st-action-divider"></span><button class="st-icon-action" :aria-label="'编辑任务：'+task.name" title="编辑任务" @click="edit(task)"><GraphIcon name="edit"/></button><button class="st-run" @click="run(task)" :aria-label="'填入会话：'+task.name"><GraphIcon name="message"/>填入会话</button><button v-if="!task.builtin" class="st-icon-action st-remove" :aria-label="'删除任务：'+task.name" title="删除任务" @click="remove(task)"><GraphIcon name="trash"/></button></div>
      </article>
      <div class="st-list-footer"><span>共 {{tasks.length}} 个任务<span v-if="query||filter"> · 当前显示 {{filteredTasks.length}} 个</span></span><details class="st-scheduler-note"><summary><i></i>调度服务待接入</summary><p>当前仅保存任务配置，不会自动执行。启用配置后仍需后端调度服务；可使用“填入会话”继续分析。</p></details></div>
    </section>
    <section class="st-templates"><div class="st-templates-heading"><div><h3>从模板开始</h3><p>预设业务场景与时间，创建时可继续调整</p></div><span>{{templates.length}} 个模板</span></div><div class="st-template-grid"><button v-for="template in templates" :key="template.id||template.scene" @click="useTemplate(template)"><div class="st-template-top"><span :class="['st-scene-icon',sceneStyle(template.scene)]"><GraphIcon :name="sceneIcon(template.scene)"/></span><span>{{template.scene}}</span><GraphIcon name="chevron"/></div><strong>{{template.name}}</strong><p>{{template.instructions}}</p><small><GraphIcon name="时间"/>{{frequencyLabel(template)}} {{template.time}}<i>·</i>{{template.horizon}}</small></button></div></section>
    <p v-if="message" class="st-message" role="status">{{message}}</p>
    <div v-if="editing" class="st-modal" @click.self="closeEditor" @keydown="dialogKeys">
      <form ref="editor" role="dialog" aria-modal="true" aria-labelledby="st-form-title" @submit.prevent="save">
        <div class="st-dialog-heading"><div><h3 id="st-form-title">{{draft.id?'编辑定时任务':'新建定时任务'}}</h3><p>设置预测对象、分析要求与执行计划</p></div><button type="button" class="st-icon-action" @click="closeEditor" aria-label="关闭任务编辑"><GraphIcon name="close"/></button></div>
        <div class="st-form-body">
          <div class="st-form-section"><h4>任务内容</h4><label>任务名称<input ref="nameInput" v-model="draft.name" required maxlength="80" placeholder="例如：每日客户用气预测"></label><label>分析需求<textarea v-model="draft.instructions" required placeholder="描述预测任务、分析要求与交付内容…"></textarea></label><div class="st-grid st-business-grid"><label>预测场景<select aria-label="预测场景" v-model="draft.scene" @change="draft.object='';draft.horizon=horizons[draft.scene][0]"><option>短期客户</option><option>冬季保供</option><option>月度销量</option></select></label><label>{{draft.scene==='短期客户'?'客户':draft.scene==='冬季保供'?'区域':'省份'}}<input v-model="draft.object" required placeholder="填写具体业务对象"></label><label>预测范围<select aria-label="预测范围" v-model="draft.horizon"><option v-for="h in horizons[draft.scene]" :key="h">{{h}}</option></select></label></div></div>
          <div class="st-form-section"><h4>执行计划<span>北京时间 · UTC+8</span></h4><div class="st-grid"><label>执行频率<select aria-label="执行频率" v-model="draft.frequency"><option>每天</option><option>每周</option><option>每月</option></select></label><label>计划时间<input v-model="draft.time" type="time" required></label><label v-if="draft.frequency==='每周'">星期<select aria-label="星期" v-model="draft.weekday"><option v-for="day in weekdays" :key="day">{{day}}</option></select></label><label v-if="draft.frequency==='每月'">每月几号<input v-model.number="draft.day" type="number" min="1" max="28" required></label><label>有效期<select aria-label="有效期" v-model="draft.validity"><option>长期有效</option><option>指定截止日期</option></select></label><label v-if="draft.validity==='指定截止日期'">截止日期<input v-model="draft.endDate" type="date" required :min="today"></label><label>对话模型<select aria-label="对话模型" v-model="draft.model"><option>自动选择</option><option>DeepSeek-V3.2</option><option>Qwen3-Max</option><option>GLM-4.7</option></select></label></div></div>
          <p v-if="formError" class="st-form-error" role="alert">{{formError}}</p>
        </div>
        <div class="st-form-actions"><span>保存配置后，实际执行需接入调度服务</span><button type="button" @click="closeEditor">取消</button><button>保存配置</button></div>
      </form>
    </div>
    <div v-if="pendingDelete" class="st-modal st-delete-overlay" @click.self="cancelDelete" @keydown="dialogKeys"><section ref="deleteDialog" role="alertdialog" aria-modal="true" aria-labelledby="st-delete-title"><span class="st-delete-icon"><GraphIcon name="trash"/></span><h3 id="st-delete-title">删除这个任务？</h3><p>“{{pendingDelete.name}}”的配置将被移除。</p><div><button ref="cancelDeleteButton" @click="cancelDelete">取消</button><button @click="confirmDelete">删除任务</button></div></section></div>
  </div>
</template>
<script setup>
import {localId} from './localId.js'
import {ref,computed,onMounted,nextTick} from 'vue'
import GraphIcon from './GraphIcon.vue'
const emit=defineEmits(['run'])
const tasks=ref([]),templates=ref([]),loading=ref(true),editing=ref(false),message=ref(''),formError=ref(''),draft=ref({}),pendingDelete=ref(null),query=ref(''),filter=ref('')
const createButton=ref(),editor=ref(),nameInput=ref(),deleteDialog=ref(),cancelDeleteButton=ref()
let returnFocus
const today=new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Shanghai'}).format(new Date())
const weekdays=['周一','周二','周三','周四','周五','周六','周日'],horizons={'短期客户':['未来7天','未来10天','未来14天'],'冬季保供':['未来三旬','本供暖季'],'月度销量':['未来3个月','未来6个月']}
const filters=computed(()=>[{value:'',label:'全部任务',count:tasks.value.length},{value:'enabled',label:'已启用',count:tasks.value.filter(task=>task.enabled).length},{value:'disabled',label:'未启用',count:tasks.value.filter(task=>!task.enabled).length}])
const filteredTasks=computed(()=>{const term=query.value.trim().toLowerCase();return tasks.value.filter(task=>(!filter.value||(filter.value==='enabled'?task.enabled:!task.enabled))&&(!term||[task.name,task.object,task.scene].join(' ').toLowerCase().includes(term)))})
const sceneStyle=scene=>({'短期客户':'st-scene-teal','冬季保供':'st-scene-blue','月度销量':'st-scene-purple'}[scene]||'st-scene-teal')
const sceneIcon=scene=>({'短期客户':'需求变化','冬季保供':'sun','月度销量':'指标值'}[scene]||'时间')
const frequencyLabel=task=>task.frequency==='每周'?'每'+(task.weekday||'周一'):task.frequency==='每月'?`每月 ${task.day||1} 日`:task.frequency
onMounted(async()=>{try{const response=await fetch('/scheduled-tasks.json');if(!response.ok)throw Error();templates.value=await response.json();const saved=JSON.parse(localStorage.getItem('wb-scheduled-tasks')||'null');tasks.value=Array.isArray(saved)?saved:templates.value.filter(t=>!t.templateOnly).map(t=>({...t}));if(!saved)persist()}catch{message.value='任务配置加载失败，请刷新重试。'}finally{loading.value=false}})
function persist(){try{localStorage.setItem('wb-scheduled-tasks',JSON.stringify(tasks.value));return true}catch{message.value='浏览器存储不可用，配置无法持久保存。';return false}}
async function openEditor(value){returnFocus=document.activeElement;draft.value=value;formError.value='';editing.value=true;await nextTick();nameInput.value?.focus()}
function create(){openEditor({name:'',scene:'短期客户',object:'',frequency:'每天',time:'08:00',weekday:'周一',day:1,horizon:'未来7天',instructions:'',model:'自动选择',validity:'长期有效',endDate:'',enabled:false})}
function edit(task){openEditor({model:'自动选择',validity:'长期有效',endDate:'',...task})}
function useTemplate(template){openEditor({...template,id:undefined,builtin:false,enabled:false,model:'自动选择',validity:'长期有效',endDate:''})}
async function restoreFocus(){await nextTick();(returnFocus?.isConnected?returnFocus:createButton.value)?.focus()}
function closeEditor(){editing.value=false;restoreFocus()}
function save(){
 if(draft.value.validity==='指定截止日期'&&draft.value.endDate<today){formError.value='截止日期不能早于今天。';return}
 if(!horizons[draft.value.scene]?.includes(draft.value.horizon)){formError.value='请选择与预测场景对应的预测范围。';return}
 const task={...draft.value,name:draft.value.name.trim(),object:draft.value.object.trim()};if(!task.name||!task.object)return
 const previous=tasks.value
 tasks.value=task.id?tasks.value.map(row=>row.id===task.id?task:row):[{...task,id:localId()},...tasks.value]
 if(persist()){query.value='';filter.value='';closeEditor();message.value='配置已保存。实际执行需接入后端调度服务。'}else{tasks.value=previous;formError.value='配置保存失败，请检查浏览器存储后重试。'}
}
function toggle(task){task.enabled=!task.enabled;if(!persist())task.enabled=!task.enabled}
async function remove(task){returnFocus=document.activeElement;pendingDelete.value=task;await nextTick();cancelDeleteButton.value?.focus()}
function cancelDelete(){pendingDelete.value=null;restoreFocus()}
function confirmDelete(){const previous=tasks.value;tasks.value=tasks.value.filter(task=>task.id!==pendingDelete.value.id);if(!persist()){tasks.value=previous;return}pendingDelete.value=null;message.value='任务配置已删除。';restoreFocus()}
function run(task){emit('run',task)}
function dialogKeys(event){
 if(event.key==='Escape'){event.preventDefault();event.stopPropagation();pendingDelete.value?cancelDelete():closeEditor();return}
 if(event.key!=='Tab')return
 const dialog=pendingDelete.value?deleteDialog.value:editor.value,items=[...dialog.querySelectorAll('button:not(:disabled),input:not(:disabled),select:not(:disabled),textarea:not(:disabled)')].filter(element=>element.getClientRects().length)
 if(event.shiftKey&&document.activeElement===items[0]){event.preventDefault();items.at(-1)?.focus()}
 else if(!event.shiftKey&&document.activeElement===items.at(-1)){event.preventDefault();items[0]?.focus()}
}
</script>
<style scoped>
.st { color:var(--wb-ui-text); font-size:var(--wb-font-control); }
.st button,.st input,.st select,.st textarea { font:inherit; }
.st button { cursor:pointer; }
.st-toolbar { display:flex; align-items:center; justify-content:space-between; gap:16px; margin:0 0 22px; }
.st-toolbar>div { display:flex; flex-wrap:wrap; align-items:center; gap:14px; }
.st-toolbar p { margin:0; color:var(--wb-ui-muted); font-size:var(--wb-font-meta); }
.st-toolbar>div>span { display:flex; align-items:center; gap:5px; color:var(--wb-ui-muted); font-size:var(--wb-font-meta); }
.st-toolbar svg { width:13px; height:13px; }
.st-workspace { overflow:hidden; border:1px solid var(--wb-ui-border); border-radius:13px; background:var(--wb-ui-surface-raised); }
.st-list-toolbar { display:flex; align-items:center; justify-content:space-between; gap:18px; padding:15px 20px; border-bottom:1px solid var(--wb-ui-border); }
.st-filters { display:flex; align-items:center; gap:5px; }
.st-filters button { display:inline-flex; align-items:center; gap:7px; padding:7px 10px; border:0; border-radius:6px; background:transparent; color:var(--wb-ui-secondary); font-size:var(--wb-font-control); white-space:nowrap; }
.st-filters button:hover { background:var(--wb-ui-selection); }
.st-filters button.chosen { color:var(--wb-ui-text); background:var(--wb-ui-selection); font-weight:500; }
.st-filters button>span { color:var(--wb-ui-muted); font-size:var(--wb-font-meta); font-weight:400; font-variant-numeric:tabular-nums; }
.st-filters button.chosen>span { color:var(--wb-ui-muted); }
.st-search { display:flex; align-items:center; gap:7px; min-width:0; width:245px; padding:0 10px; border:1px solid var(--wb-ui-border); border-radius:7px; background:var(--wb-ui-surface); color:var(--wb-ui-muted); }
.st-search:focus-within { border-color:var(--wb-ui-border); background:var(--wb-ui-surface-raised); }
.st-search svg { width:15px; height:15px; flex-shrink:0; }
.st-search input { width:100%; min-width:0; padding:8px 0; border:0; outline:0; background:transparent; color:var(--wb-ui-secondary); font-size:var(--wb-font-meta); }
.st-table-heading,.st-task-row { display:grid; grid-template-columns:minmax(250px,1fr) 155px 155px 245px; align-items:center; gap:16px; }
.st-table-heading { padding:10px 22px; border-bottom:1px solid var(--wb-ui-border); background:var(--wb-ui-surface-raised); color:var(--wb-ui-muted); font-size:var(--wb-font-meta); }
.st-table-heading>span:last-child { text-align:right; }
.st-task-row { padding:20px 22px; border-bottom:1px solid var(--wb-ui-border); }
.st-task-row:hover { background:var(--wb-ui-surface-raised); }
.st-task-main { display:flex; align-items:center; gap:13px; min-width:0; }
.st-task-main>div { min-width:0; }
.st-task-main h3 { margin:0 0 6px; color:var(--wb-ui-text); font-size:15px; line-height:1.5; font-weight:500; overflow-wrap:anywhere; }
.st-task-main p { display:flex; flex-wrap:wrap; align-items:center; gap:6px; margin:0; color:var(--wb-ui-muted); font-size:var(--wb-font-meta); line-height:1.6; }
.st-task-main p>i { color:var(--wb-ui-muted); font-style:normal; }
.st-task-origin { display:block; margin-top:5px; color:var(--wb-ui-muted); font-size:var(--wb-font-meta); }
.st-scene-icon { display:grid; place-items:center; width:38px; height:38px; flex-shrink:0; border:1px solid var(--wb-ui-border); border-radius:10px; background:var(--wb-ui-surface); color:var(--wb-ui-secondary); }
.st-scene-icon svg { width:20px; height:20px; }
.st-scene-blue { border-color:var(--wb-ui-border); background:var(--wb-ui-surface); color:var(--wb-ui-secondary); }
.st-scene-purple { border-color:var(--wb-ui-border); background:var(--wb-ui-surface); color:var(--wb-ui-secondary); }
.st-plan { display:flex; flex-direction:column; align-items:flex-start; gap:5px; color:var(--wb-ui-muted); }
.st-plan>b { color:var(--wb-ui-text); font-size:15px; line-height:1.5; font-weight:500; font-variant-numeric:tabular-nums; letter-spacing:.2px; }
.st-plan>span { font-size:var(--wb-font-meta); }
.st-state { display:flex; flex-direction:column; align-items:flex-start; gap:7px; }
.st-state-label { display:inline-flex; align-items:center; gap:5px; padding:2px 7px; border:1px solid var(--wb-ui-border); border-radius:4px; background:var(--wb-ui-surface); color:var(--wb-ui-muted); font-size:var(--wb-font-meta); }
.st-state-label>i { width:4px; height:4px; border-radius:50%; background:var(--wb-ui-primary); }
.st-state-label.enabled { border-color:var(--wb-ui-border); background:var(--wb-ui-success-soft); color:var(--wb-ui-success); }
.st-state-label.enabled>i { background:var(--wb-ui-success); }
.st-state small { color:var(--wb-ui-muted); font-size:var(--wb-font-meta); }
.st-task-actions { display:flex; align-items:center; justify-content:flex-end; gap:12px; }
.st-toggle { display:block; width:28px; height:16px; padding:2px; border:0; border-radius:999px; background:var(--wb-ui-border-strong); flex-shrink:0; }
.st-toggle>span { display:block; width:12px; height:12px; border-radius:50%; background:var(--wb-ui-surface-raised); box-shadow:0 1px 3px #24312c20; transition:transform .16s; }
.st-toggle[aria-checked=true] { background:var(--wb-ui-primary); }
.st-toggle[aria-checked=true]>span { transform:translateX(12px); }
.st-action-divider { height:14px; width:1px; background:var(--wb-ui-surface); }
.st-icon-action { display:grid; place-items:center; width:26px; height:28px; padding:0; border:0; border-radius:5px; background:transparent; color:var(--wb-ui-muted); }
.st-icon-action svg { width:15px; height:15px; }
.st-icon-action:hover { background:var(--wb-ui-selection); color:var(--wb-ui-secondary); }
.st-run { display:inline-flex; align-items:center; gap:5px; padding:5px 8px; border:1px solid var(--wb-ui-border); border-radius:6px; background:var(--wb-ui-surface-raised); color:var(--wb-ui-secondary); font-size:var(--wb-font-meta); white-space:nowrap; }
.st-run svg { width:13px; height:13px; }
.st-run:hover { background:var(--wb-ui-selection); border-color:var(--wb-ui-border); }
.st-remove:hover { background:var(--wb-ui-danger-soft); color:var(--wb-ui-danger); }
.st-list-footer { display:flex; align-items:center; justify-content:space-between; gap:16px; padding:12px 20px; color:var(--wb-ui-muted); font-size:var(--wb-font-meta); }
.st-scheduler-note { max-width:450px; text-align:right; }
.st-scheduler-note summary { display:inline-flex; align-items:center; gap:5px; color:var(--wb-ui-muted); cursor:pointer; list-style:none; }
.st-scheduler-note summary::-webkit-details-marker { display:none; }
.st-scheduler-note summary>i { width:4px; height:4px; border-radius:50%; background:var(--wb-ui-primary); }
.st-scheduler-note p { margin:8px 0 0; padding:10px 12px; border:1px solid var(--wb-ui-border); border-radius:7px; background:var(--wb-ui-surface); color:var(--wb-ui-muted); font-size:var(--wb-font-meta); line-height:1.8; }
.st-templates { margin-top:28px; }
.st-templates-heading { display:flex; align-items:center; justify-content:space-between; gap:15px; margin-bottom:14px; }
.st-templates-heading>div { display:flex; align-items:baseline; gap:12px; }
.st-templates h3 { margin:0; color:var(--wb-ui-text); font-size:var(--wb-font-body); font-weight:500; }
.st-templates-heading p { margin:0; color:var(--wb-ui-text); font-size:var(--wb-font-meta); }
.st-templates-heading>span { color:var(--wb-ui-muted); font-size:var(--wb-font-meta); white-space:nowrap; }
.st-template-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:16px; }
.st-template-grid>button { display:flex; flex-direction:column; align-items:stretch; gap:0; min-width:0; padding:18px 20px; border:1px solid var(--wb-ui-border); border-radius:12px; background:var(--wb-ui-surface); text-align:left; }
.st-template-grid>button:hover { border-color:var(--wb-ui-border); background:var(--wb-ui-selection); }
.st-template-top { display:flex; align-items:center; gap:9px; margin-bottom:13px; }
.st-template-top .st-scene-icon { width:28px; height:28px; border-radius:8px; }
.st-template-top .st-scene-icon svg { width:15px; height:15px; }
.st-template-top>span:not(.st-scene-icon) { color:var(--wb-ui-muted); font-size:var(--wb-font-meta); }
.st-template-top>svg { margin-left:auto; width:14px; height:14px; color:var(--wb-ui-muted); }
.st-template-grid strong { color:var(--wb-ui-text); font-size:var(--wb-font-control); font-weight:500; }
.st-template-grid p { margin:8px 0 16px; color:var(--wb-ui-muted); font-size:var(--wb-font-meta); line-height:1.7; }
.st-template-grid small { display:flex; align-items:center; gap:5px; margin-top:auto; color:var(--wb-ui-muted); font-size:var(--wb-font-meta); }
.st-template-grid small svg { width:12px; height:12px; }
.st-template-grid small i { padding:0 3px; color:var(--wb-ui-muted); font-style:normal; }
.st-message { margin:16px 0 0; color:var(--wb-ui-success); font-size:var(--wb-font-meta); }
.st-empty { display:flex; flex-direction:column; align-items:center; gap:10px; padding:45px 16px; text-align:center; color:var(--wb-ui-muted); }
.st-empty>svg { width:26px; height:26px; color:var(--wb-ui-muted); }
.st-empty h3 { margin:0; color:var(--wb-ui-text); font-size:var(--wb-font-body); font-weight:500; }
.st-empty p { margin:0; color:var(--wb-ui-muted); font-size:var(--wb-font-meta); }
.st-empty button { padding:3px 7px; border:0; background:transparent; color:var(--wb-ui-muted); font-size:var(--wb-font-meta); }
.st-modal { position:fixed; inset:0; z-index:100; display:flex; align-items:center; justify-content:center; padding:24px; background:#20212445; backdrop-filter:blur(3px); }
.st-modal form { display:flex; flex-direction:column; width:700px; max-height:calc(100dvh - 48px); overflow:hidden; margin:0; padding:0; border:1px solid #ffffffc0; border-radius:16px; background:var(--wb-ui-surface-raised); box-shadow:0 20px 70px #17241c20; }
.st-dialog-heading { display:flex; align-items:flex-start; justify-content:space-between; gap:20px; padding:22px 26px 18px; border-bottom:1px solid var(--wb-ui-border); }
.st-dialog-heading h3 { margin:0; color:var(--wb-ui-text); font-size:19px; font-weight:600; }
.st-dialog-heading p { margin:6px 0 0; color:var(--wb-ui-muted); font-size:var(--wb-font-meta); }
.st-dialog-heading>.st-icon-action { color:var(--wb-ui-text); }
.st-form-body { overflow:auto; padding:0 26px 22px; scrollbar-width:thin; }
.st-form-section { padding-top:20px; }
.st-form-section+.st-form-section { margin-top:19px; border-top:1px solid var(--wb-ui-border); }
.st-form-section h4 { display:flex; justify-content:space-between; gap:10px; margin:0 0 12px; color:var(--wb-ui-text); font-size:var(--wb-font-meta); font-weight:500; }
.st-form-section h4>span { color:var(--wb-ui-muted); font-size:var(--wb-font-meta); font-weight:400; }
.st-modal label { display:block; margin-top:12px; color:var(--wb-ui-muted); font-size:var(--wb-font-meta); }
.st-modal input,.st-modal select,.st-modal textarea { display:block; width:100%; min-width:0; margin-top:7px; padding:10px 11px; border:1px solid var(--wb-ui-border); border-radius:8px; background:var(--wb-ui-surface-raised); color:var(--wb-ui-text); font-size:var(--wb-font-control); }
.st-modal textarea { min-height:96px; resize:vertical; line-height:1.7; }
.st-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:0 14px; }
.st-form-error { margin:16px 0 0; padding:9px 12px; border:1px solid var(--wb-ui-border); border-radius:7px; background:var(--wb-ui-danger-soft); color:var(--wb-ui-danger); font-size:var(--wb-font-meta); }
.st-form-actions { display:flex; align-items:center; justify-content:flex-end; gap:10px; padding:16px 24px; border-top:1px solid var(--wb-ui-border); background:var(--wb-ui-surface-raised); }
.st-form-actions>span { margin-right:auto; color:var(--wb-ui-muted); font-size:var(--wb-font-meta); }
.st-form-actions button { min-height:34px; padding:7px 17px; border:1px solid var(--wb-ui-border); border-radius:7px; background:var(--wb-ui-surface-raised); color:var(--wb-ui-muted); font-size:var(--wb-font-meta); }
.st-delete-overlay>section { width:min(380px,100%); padding:25px; border:1px solid var(--wb-ui-border); border-radius:14px; background:var(--wb-ui-surface-raised); box-shadow:0 16px 60px #20212420; }
.st-delete-icon { display:grid; place-items:center; width:35px; height:35px; margin-bottom:13px; border-radius:9px; background:var(--wb-ui-danger-soft); color:var(--wb-ui-danger); }
.st-delete-icon svg { width:17px; height:17px; }
.st-delete-overlay h3 { margin:0; color:var(--wb-ui-text); font-size:17px; font-weight:600; }
.st-delete-overlay p { margin:12px 0 20px; color:var(--wb-ui-muted); font-size:var(--wb-font-meta); line-height:1.8; overflow-wrap:anywhere; }
.st-delete-overlay section>div { display:flex; justify-content:flex-end; gap:10px; }
.st-delete-overlay button { padding:7px 14px; border:1px solid var(--wb-ui-border); border-radius:7px; background:var(--wb-ui-surface-raised); color:var(--wb-ui-muted); font-size:var(--wb-font-meta); }
.st-delete-overlay button:last-child { color:var(--wb-ui-on-primary); background:var(--wb-ui-danger); border-color:var(--wb-ui-border-strong); }
@media(max-width:1250px) { .st-table-heading,.st-task-row { grid-template-columns:minmax(190px,1fr) 105px 105px 215px; gap:10px; }.st-task-row { padding:18px; }.st-task-actions{gap:9px}.st-table-heading {padding-left:18px;padding-right:18px}.st-template-grid {gap:12px}.st-template-grid>button{padding:16px}.st-templates-heading>div {flex-wrap:wrap;gap:5px} }
@media(max-width:900px) { .st-toolbar>div {gap:5px;flex-direction:column;align-items:flex-start}.st-table-heading {display:none}.st-task-row {grid-template-columns:minmax(0,1fr) auto;gap:14px 10px}.st-task-main{grid-column:1/-1}.st-plan {grid-row:2;gap:3px}.st-plan>b {font-size:var(--wb-font-body)}.st-state {grid-column:2;grid-row:2;align-items:flex-end}.st-task-actions {grid-column:1/-1;padding-top:11px;border-top:1px solid var(--wb-ui-border)}.st-action-divider{margin-right:auto}.st-template-grid{grid-template-columns:1fr}.st-template-grid>button{padding:16px}.st-template-top {margin-bottom:10px}.st-list-toolbar{flex-wrap:wrap;gap:10px;padding:12px}.st-search {width:100%}.st-list-footer {padding:12px;align-items:flex-start}.st-scheduler-note{max-width:230px}.st-filters{gap:0}.st-filters button{padding:6px 8px}.st-templates-heading>div{display:block}.st-templates-heading p{margin-top:5px}.st-modal{padding:12px}.st-modal form{max-height:calc(100dvh - 24px)}.st-dialog-heading{padding:18px}.st-form-body{padding:0 18px 18px}.st-grid{grid-template-columns:repeat(2,minmax(0,1fr));gap:0 12px}.st-form-actions{padding:13px 18px}.st-form-actions>span{display:none} }
@media(prefers-reduced-motion:reduce){.st-toggle>span{transition:none}}
</style>
