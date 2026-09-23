import { createApp } from 'vue'
import { createPinia } from 'pinia'
import './styles/tokens.css'
import router from './router'
import App from './App.vue'
import { installAuthFetch } from './utils/auth'

const app = createApp(App)
installAuthFetch()

app.use(createPinia())
app.use(router)

app.mount('#app')
