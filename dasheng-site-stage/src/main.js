import {createApp} from 'vue'
import {createRouter,createWebHistory} from 'vue-router'
import App from './App.vue'
import './style.css'
const pages=['home','products','research','developers','technology','applications','ecosystem','solutions','services','competitions','about','news','contact']
const router=createRouter({history:createWebHistory(),routes:pages.map(p=>({path:p==='home'?'/':'/'+p,name:p,component:App,props:{page:p}})),scrollBehavior(){return {top:0}}})
createApp(App).use(router).mount('#app')
