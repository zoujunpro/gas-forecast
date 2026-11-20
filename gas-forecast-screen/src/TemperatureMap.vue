<template><div class="temperature"><canvas ref="canvas" @mousemove="hoverProvince" @mouseleave="hovered=null"/><div v-if="error" class="map-error">{{ error }}</div><div class="weather" v-for="c in cities" :key="c.name" :style="{left:c.x+'%',top:c.y+'%'}"><span v-if="!sales">{{ c.icon }}</span><div>{{ c.name }}<small>{{ sales ? `${salesData.find(r=>String(r.code)===String(c.code))?.volume ?? 0} 亿方` : c.temp }}</small></div></div><div class="legend">{{ sales ? "2026累计销量（亿方）" : "气温 (°C)" }}<div><i></i><span v-if="sales">≥0.6<br>0.45<br>0.3<br>0.15<br>0</span><span v-else>≥20<br>10<br>0<br>-10<br>≤-20</span></div></div><span class="live">● {{ paused ? '动画已暂停' : sales ? '2026累计 · 截至11-18 · 模拟数据' : '34地区模拟天气 · 悬浮查看' }}</span></div></template>
<script setup>
import {ref,computed,onMounted,onBeforeUnmount} from 'vue'
const props=defineProps({paused:Boolean,sales:Boolean})
const canvas=ref(),error=ref('');let frame=0,observer,alive=true,time=0,last=0
const salesData=ref([]);const weather=ref([]),hovered=ref(null);let hitPaths=[]
const cities=computed(()=>hovered.value?[hovered.value]:[])
function hoverProvince(event){
 const c=canvas.value;if(!c)return
 const rect=c.getBoundingClientRect(),scale=Math.min(rect.width/900,rect.height/560)
 const x=(event.clientX-rect.left-(rect.width-900*scale)/2)/scale
 const y=(event.clientY-rect.top-(rect.height-560*scale)/2)/scale
 const ctx=c.getContext('2d');const hit=hitPaths.find(item=>ctx.isPointInPath(item.path,x,y))
 const record=hit&&weather.value.find(item=>String(item.code)===String(hit.code))
 hovered.value=record?{...record,temp:`${record.minTemp}~${record.maxTemp}°C`,x:Math.max(1,Math.min(70,(event.clientX-rect.left+12)/rect.width*100)),y:Math.max(1,Math.min(78,(event.clientY-rect.top+12)/rect.height*100))}:null
}

onMounted(async()=>{try{const response=await fetch('/maps/china.json');if(!response.ok)throw Error();const geo=await response.json();const wr=await fetch('/weather.json');if(!wr.ok)throw Error();const wd=await wr.json();if(!alive)return;weather.value=wd.provinces;const sr=await fetch('/province-sales.json');if(!sr.ok)throw Error();const sd=await sr.json();if(!alive)return;salesData.value=sd.provinces;const paths=geo.features.map(f=>{const p=new Path2D();const polys=f.geometry.type==='MultiPolygon'?f.geometry.coordinates:[f.geometry.coordinates];for(const poly of polys)for(const ring of poly){ring.forEach(([lng,lat],i)=>{const x=(lng-73)/63*900,y=(54-lat)/36*560;i?p.lineTo(x,y):p.moveTo(x,y)});p.closePath()}hitPaths.push({path:p,code:f.properties.adcode});return p});const layer=document.createElement('canvas');layer.width=720;layer.height=450;const lc=layer.getContext('2d');const image=lc.createImageData(720,450);const colors=[[3,35,232],[0,98,255],[0,198,255],[49,241,230],[239,244,100],[255,176,35],[255,89,30]];
const field=new Float32Array(720*450);for(let y=0;y<450;y++)for(let x=0;x<720;x++){const lng=73+x/720*63,lat=54-y/450*36;let total=0,weights=0;for(const c of weather.value){const weight=1/Math.pow(Math.max(.7,Math.hypot((lng-c.longitude)*.8,lat-c.latitude)),3);total+=(c.minTemp+c.maxTemp)/2*weight;weights+=weight}field[y*720+x]=Math.max(0,Math.min(1,(total/weights+20)/50))}
const merged=new Path2D();paths.forEach(p=>merged.addPath(p));
function draw(now){frame=requestAnimationFrame(draw);if(now-last<33)return;const elapsed=last?Math.min((now-last)/1000,.1):0;last=now;if(!props.paused)time+=elapsed;const c=canvas.value;if(!c)return;const ctx=c.getContext('2d');const w=c.width,h=c.height;ctx.clearRect(0,0,w,h);const scale=Math.min(w/900,h/560);ctx.save();ctx.translate((w-900*scale)/2,(h-560*scale)/2);ctx.scale(scale,scale);
for(let y=0;y<450;y++)for(let x=0;x<720;x++){const nx=x/720,ny=y/450;let v=field[y*720+x]+.12*Math.sin(nx*18+ny*12-time*2.4)+.065*Math.cos(nx*32-ny*20+time*2.8);if(props.sales)v=.25+.45*Math.sin(nx*5)*Math.sin(ny*3)+.1*Math.sin(time+nx*15);v=Math.max(0,Math.min(.999,v));const f=v*6,k=Math.floor(f),t=f-k,i=(y*720+x)*4;for(let j=0;j<3;j++)image.data[i+j]=colors[k][j]*(1-t)+colors[k+1][j]*t;image.data[i+3]=255}lc.putImageData(image,0,0);
ctx.save();ctx.shadowColor='#00aeff';ctx.shadowBlur=22;ctx.fillStyle='#0099ff';paths.forEach(p=>ctx.fill(p));ctx.restore();ctx.save();ctx.clip(merged);if(props.sales){for(const item of hitPaths){const record=salesData.value.find(r=>String(r.code)===String(item.code));const v=Math.min(.999,(record?.volume||0)/.6);const f=v*6,k=Math.floor(f),t=f-k;const pulse=.88+.12*Math.sin(time*3.5+Number(item.code)*.017);const salesColors=[[8,45,95],[9,91,151],[0,151,190],[244,202,80],[247,135,35],[213,65,26],[151,24,22]];ctx.fillStyle=`rgb(${salesColors[k].map((n,j)=>Math.round((n*(1-t)+salesColors[k+1][j]*t)*pulse)).join(',')})`;ctx.fill(item.path)}}else{ctx.drawImage(layer,0,0,900,560);}ctx.restore();ctx.strokeStyle='rgba(157,239,255,.85)';ctx.lineWidth=.65;paths.forEach(p=>ctx.stroke(p));ctx.restore()}
observer=new ResizeObserver(()=>{const r=canvas.value.getBoundingClientRect();const ratio=Math.min(4,Math.max(3,devicePixelRatio||1));canvas.value.width=Math.round(r.width*ratio);canvas.value.height=Math.round(r.height*ratio)});observer.observe(canvas.value);frame=requestAnimationFrame(draw)
}catch{error.value='地图加载失败，请刷新页面重试'}})
onBeforeUnmount(()=>{alive=false;cancelAnimationFrame(frame);observer?.disconnect()})
</script>
