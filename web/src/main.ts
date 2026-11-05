import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as echarts from 'echarts'
(window as any).echarts = echarts
import App from './App.vue'
import router from './router'
import './styles/base.css'

createApp(App).use(createPinia()).use(router).use(ElementPlus).mount('#app')
