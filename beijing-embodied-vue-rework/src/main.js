import { createApp } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import App from './App.vue'
import './style.css'

const routes = [
  ['/', 'home'], ['/platform', 'platform'], ['/research', 'research'], ['/products', 'products'],
  ['/applications', 'applications'], ['/ecosystem', 'ecosystem'], ['/news', 'news'], ['/about', 'about']
].map(([path, name]) => ({ path, name, component: App }))

const router = createRouter({ history: createWebHistory(), routes, scrollBehavior: () => ({ top: 0 }) })
createApp(App).use(router).mount('#app')
