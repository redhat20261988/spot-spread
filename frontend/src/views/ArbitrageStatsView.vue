<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { getSpreadStats } from '../api/market'

const pairStats = ref([])
const loading = ref(true)
const error = ref(null)
let interval = null

const exchangeLabels = {
  binance: 'Binance', bitfinex: 'Bitfinex', coinex: 'CoinEx',
  mexc: 'MEXC', whitebit: 'WhiteBIT', bingx: 'BingX', coinw: 'CoinW',
  bitunix: 'Bitunix', lbank: 'LBank', hyperliquid: 'Hyperliquid', dydx: 'dYdX'
}

function exchangeLabel(ex) {
  return exchangeLabels[ex] || ex
}

function formatPct(v) {
  if (v == null || Number.isNaN(Number(v))) return '-'
  return Number(v).toFixed(4) + '%'
}

async function fetchStats() {
  try {
    error.value = null
    const res = await getSpreadStats()
    pairStats.value = res?.data?.pairStats ?? []
  } catch (e) {
    error.value = e.message || '获取失败'
    pairStats.value = []
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchStats()
  interval = setInterval(fetchStats, 30_000)
})
onUnmounted(() => {
  if (interval) clearInterval(interval)
})
</script>

<template>
  <div class="arb-view">
    <header class="header">
      <h1>价差套利统计</h1>
    </header>
    <p class="summary">按平均利润率、次数降序展示各交易所组合（套利利润率 &ge; 0.1% 时入库）</p>
    <div v-if="error" class="error">{{ error }}</div>
    <div v-if="loading && pairStats.length === 0" class="loading">加载中...</div>
    <div v-else class="table-wrap">
      <table class="data-table">
        <thead>
          <tr>
            <th>币种</th>
            <th>买入交易所</th>
            <th>卖出交易所</th>
            <th>次数</th>
            <th>平均利润率</th>
            <th>买入手续费</th>
            <th>卖出手续费</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, i) in pairStats" :key="i">
            <td>{{ row.symbol }}</td>
            <td>{{ exchangeLabel(row.exchangeBuy) }}</td>
            <td>{{ exchangeLabel(row.exchangeSell) }}</td>
            <td>{{ row.spreadCount }}</td>
            <td>{{ formatPct(row.avgProfitMarginPct) }}</td>
            <td>{{ formatPct(row.spotFeeBuyPct) }}</td>
            <td>{{ formatPct(row.spotFeeSellPct) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <p v-if="!loading && pairStats.length === 0" class="empty">暂无数据（约 15 秒后开始写入，需运行一段时间后才有统计）</p>
  </div>
</template>

<style scoped>
.arb-view { max-width: 900px; margin: 0 auto; padding: 24px; font-family: system-ui, sans-serif; }
.header h1 { margin: 0 0 16px 0; font-size: 1.5rem; }
.summary { color: #888; margin-bottom: 16px; font-size: 0.95rem; }
.error { padding: 12px; background: #fee; color: #c00; border-radius: 6px; margin-bottom: 16px; }
.loading { text-align: center; padding: 24px; color: #888; }
.table-wrap { overflow-x: auto; border: 1px solid #444; border-radius: 8px; }
.data-table { width: 100%; border-collapse: collapse; }
.data-table th, .data-table td { padding: 12px 16px; text-align: left; border-bottom: 1px solid #444; }
.data-table th { background: #3a3a3a; color: #e8e8e8; font-weight: 600; }
.data-table tbody tr { background: #fff; color: #1a1a1a; }
.data-table tbody tr:hover { background: #f5f5f5; }
.empty { color: #888; font-size: 0.95rem; margin-top: 16px; }
</style>
