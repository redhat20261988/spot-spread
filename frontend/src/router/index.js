import { createRouter, createWebHistory } from 'vue-router'
import ArbitrageStatsView from '../views/ArbitrageStatsView.vue'

const routes = [
  { path: '/', name: 'arbitrage-stats', component: ArbitrageStatsView }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
