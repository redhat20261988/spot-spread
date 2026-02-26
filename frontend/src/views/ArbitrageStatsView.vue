<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { getSpreadStats, getExchangePrices } from '../api/market'

const pairStats = ref([])
const exchangePrices = ref([])
const loading = ref(true)
const loadingPrices = ref(true)
const error = ref(null)
const priceError = ref(null)
let interval = null
let priceInterval = null
let statsFetching = false
const symbol = ref('BTC')
const symbolOptions = ['BTC', 'ETH', 'SOL', 'XRP', 'HYPE', 'BNB']

const exchangeLabels = {
  binance: 'Binance', bitfinex: 'Bitfinex', coinex: 'CoinEx',
  okx: 'OKX', bybit: 'Bybit', gateio: 'Gate.io', bitget: 'Bitget',
  lbank: 'LBank', whitebit: 'WhiteBIT', bitunix: 'Bitunix', cryptocom: 'Crypto.com'
}

function exchangeLabel(ex) {
  return exchangeLabels[ex] || ex
}

function formatPct(v) {
  if (v == null || Number.isNaN(Number(v))) return '-'
  return Number(v).toFixed(4) + '%'
}

function formatPrice(v) {
  if (v == null || Number.isNaN(Number(v))) return '-'
  return Number(v).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 8 })
}

/** 利润率计算（与后端一致）：rawPct = (askSell/bidBuy - 1)*100，选择 maker+taker 组合使总手续费最小 */
function calcProfit(buyEx, sellEx, dataMap) {
  const buy = dataMap[buyEx]
  const sell = dataMap[sellEx]
  if (!buy?.bid1 || !sell?.ask1 || Number(buy.bid1) <= 0) return null
  const rawPct = (Number(sell.ask1) / Number(buy.bid1) - 1) * 100
  const makerSell = Number(sell.makerFeePct)
  const takerSell = Number(sell.takerFeePct)
  const makerBuy = Number(buy.makerFeePct)
  const takerBuy = Number(buy.takerFeePct)
  const totalA = makerSell + takerBuy
  const totalB = takerSell + makerBuy
  let feeSell, feeBuy, useTakerSell, useTakerBuy
  if (totalA <= totalB) {
    feeSell = makerSell
    feeBuy = takerBuy
    useTakerSell = false
    useTakerBuy = true
  } else {
    feeSell = takerSell
    feeBuy = makerBuy
    useTakerSell = true
    useTakerBuy = false
  }
  const profitMarginPct = rawPct - feeSell - feeBuy
  return { profitMarginPct, feeSell, feeBuy, useTakerSell, useTakerBuy }
}

/** 历史套利统计按当前选择的币种筛选 */
const filteredPairStats = computed(() => {
  const sym = symbol.value
  if (!sym) return pairStats.value
  return pairStats.value.filter(row => row.symbol === sym || row.symbol === sym + 'USDT')
})

/** 为每行计算最佳配对所及手续费显示信息 */
const exchangeTableRows = computed(() => {
  const list = exchangePrices.value
  if (!list?.length) return []
  const dataMap = Object.fromEntries(list.map(e => [e.exchange, e]))
  return list.map(curr => {
    let best = null
    let bestProfit = -Infinity
    let bestFeeInfo = null
    for (const other of list) {
      if (other.exchange === curr.exchange) continue
      if (!curr.bid1 || !curr.ask1 || !other.bid1 || !other.ask1) continue
      const r1 = calcProfit(curr.exchange, other.exchange, dataMap)
      const r2 = calcProfit(other.exchange, curr.exchange, dataMap)
      if (r1 != null && r1.profitMarginPct > bestProfit) {
        bestProfit = r1.profitMarginPct
        best = other
        bestFeeInfo = { ...r1, exBuy: curr.exchange, exSell: other.exchange }
      }
      if (r2 != null && r2.profitMarginPct > bestProfit) {
        bestProfit = r2.profitMarginPct
        best = other
        bestFeeInfo = { ...r2, exBuy: other.exchange, exSell: curr.exchange }
      }
    }
    return {
      exchange: curr.exchange,
      spotPrice: curr.spotPrice,
      takerFeePct: curr.takerFeePct,
      makerFeePct: curr.makerFeePct,
      bestPair: best,
      bestProfit,
      feeInfo: bestFeeInfo
    }
  })
})

async function fetchStats() {
  if (statsFetching) return
  statsFetching = true
  const isInitial = loading.value
  try {
    if (isInitial) error.value = null
    const res = await getSpreadStats()
    const next = res?.data?.pairStats ?? []
    pairStats.value = next
  } catch (e) {
    error.value = e.message || '获取失败'
    if (isInitial) pairStats.value = []
  } finally {
    loading.value = false
    statsFetching = false
  }
}

async function fetchExchangePrices() {
  try {
    priceError.value = null
    const res = await getExchangePrices(symbol.value)
    const arr = res?.data?.exchanges ?? []
    exchangePrices.value = arr
  } catch (e) {
    priceError.value = e.message || '获取失败'
    exchangePrices.value = []
  } finally {
    loadingPrices.value = false
  }
}

watch(symbol, () => {
  loadingPrices.value = true
  fetchExchangePrices()
})

onMounted(() => {
  fetchStats()
  fetchExchangePrices()
  interval = setInterval(fetchStats, 1000)
  priceInterval = setInterval(fetchExchangePrices, 1000)
})
onUnmounted(() => {
  if (interval) clearInterval(interval)
  if (priceInterval) clearInterval(priceInterval)
})
</script>

<template>
  <div class="arb-view">
    <header class="header">
      <h1>价差套利统计</h1>
    </header>

    <!-- 交易所实时价格与最佳配对表格 -->
    <section class="section" aria-label="交易所实时价格">
      <div class="symbol-row">
        <h2>交易所现货价格与最佳配对</h2>
        <select v-model="symbol" class="symbol-select" aria-label="选择币种">
          <option v-for="s in symbolOptions" :key="s" :value="s">{{ s }}</option>
        </select>
      </div>
      <p class="summary">每行展示当前交易所的现货价格、手续费率及与之利润率最高的配对交易所；利润率及参与计算的手续费以绿色显示</p>
      <div v-if="priceError" class="error">{{ priceError }}</div>
      <div class="table-wrap table-wrap-prices" :class="{ 'has-rows': exchangeTableRows.length > 0 }">
        <table class="data-table data-table-prices" :aria-busy="loadingPrices">
          <thead>
            <tr>
              <th>当前交易所</th>
              <th>现货价格</th>
              <th>Taker费率</th>
              <th>Maker费率</th>
              <th>最佳配对所</th>
              <th>配对所现货价格</th>
              <th>配对所Taker</th>
              <th>配对所Maker</th>
              <th>价差</th>
              <th>利润率</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loadingPrices && exchangeTableRows.length === 0">
              <td colspan="10" class="loading-cell">加载中...</td>
            </tr>
            <tr v-for="row in exchangeTableRows" :key="row.exchange">
              <td>{{ exchangeLabel(row.exchange) }}</td>
              <td>{{ formatPrice(row.spotPrice) }}</td>
              <td>
                <span :class="{ 'fee-used': row.feeInfo && row.feeInfo.exBuy === row.exchange && row.feeInfo.useTakerBuy || row.feeInfo && row.feeInfo.exSell === row.exchange && row.feeInfo.useTakerSell }">
                  {{ formatPct(row.takerFeePct) }}
                </span>
              </td>
              <td>
                <span :class="{ 'fee-used': row.feeInfo && row.feeInfo.exBuy === row.exchange && !row.feeInfo.useTakerBuy || row.feeInfo && row.feeInfo.exSell === row.exchange && !row.feeInfo.useTakerSell }">
                  {{ formatPct(row.makerFeePct) }}
                </span>
              </td>
              <template v-if="row.bestPair">
                <td>{{ exchangeLabel(row.bestPair.exchange) }}</td>
                <td>{{ formatPrice(row.bestPair.spotPrice) }}</td>
                <td>
                  <span :class="{ 'fee-used': row.feeInfo && (row.feeInfo.exBuy === row.bestPair.exchange && row.feeInfo.useTakerBuy || row.feeInfo.exSell === row.bestPair.exchange && row.feeInfo.useTakerSell) }">
                    {{ formatPct(row.bestPair.takerFeePct) }}
                  </span>
                </td>
                <td>
                  <span :class="{ 'fee-used': row.feeInfo && (row.feeInfo.exBuy === row.bestPair.exchange && !row.feeInfo.useTakerBuy || row.feeInfo.exSell === row.bestPair.exchange && !row.feeInfo.useTakerSell) }">
                    {{ formatPct(row.bestPair.makerFeePct) }}
                  </span>
                </td>
                <td>{{ formatPrice(row.spotPrice != null && row.bestPair?.spotPrice != null ? Number(row.spotPrice) - Number(row.bestPair.spotPrice) : null) }}</td>
                <td>
                  <span class="profit-value">{{ formatPct(row.bestProfit) }}</span>
                </td>
              </template>
              <template v-else>
                <td colspan="6">-</td>
              </template>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-if="!loadingPrices && exchangeTableRows.length === 0" class="empty">暂无交易所数据（约 15 秒后 WebSocket 连接建立）</p>
    </section>

    <!-- 历史套利统计 -->
    <section class="section" aria-label="历史套利统计">
      <h2>历史套利组合统计</h2>
      <p class="summary">按平均利润率、次数降序展示各交易所组合（套利利润率 &ge; 0.5% 时入库）</p>
      <div v-if="error" class="error">{{ error }}</div>
      <div v-if="loading && pairStats.length === 0" class="loading">加载中...</div>
      <div v-else class="table-wrap table-wrap-stats">
        <table class="data-table">
          <thead>
            <tr>
              <th>币种（{{ symbol }}）</th>
              <th>买入交易所</th>
              <th>买入手续费</th>
              <th>卖出交易所</th>
              <th>卖出手续费</th>
              <th>次数</th>
              <th>平均利润率</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in filteredPairStats" :key="`${row.symbol}-${row.exchangeBuy}-${row.exchangeSell}`">
              <td>{{ row.symbol }}</td>
              <td>{{ exchangeLabel(row.exchangeBuy) }}</td>
              <td>{{ formatPct(row.spotFeeBuyPct) }}</td>
              <td>{{ exchangeLabel(row.exchangeSell) }}</td>
              <td>{{ formatPct(row.spotFeeSellPct) }}</td>
              <td>{{ row.spreadCount }}</td>
              <td>{{ formatPct(row.avgProfitMarginPct) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-if="!loading && pairStats.length === 0" class="empty">暂无数据（约 15 秒后开始写入，需运行一段时间后才有统计）</p>
      <p v-else-if="!loading && filteredPairStats.length === 0 && pairStats.length > 0" class="empty">当前币种 {{ symbol }} 暂无历史套利记录</p>
    </section>
  </div>
</template>

<style scoped>
.arb-view { max-width: 1200px; margin: 0 auto; padding: 24px; font-family: system-ui, sans-serif; }
.header h1 { margin: 0 0 16px 0; font-size: 1.5rem; }
.section { margin-bottom: 32px; }
.section h2 { margin: 0; font-size: 1.2rem; }
.symbol-row { display: flex; align-items: center; gap: 12px; margin-bottom: 8px; flex-wrap: wrap; }
.symbol-select {
  padding: 8px 14px;
  font-size: 1rem;
  font-weight: 600;
  border: 1px solid #555;
  border-radius: 6px;
  background: #f5f5f5;
  color: #111;
  cursor: pointer;
  min-width: 110px;
  color-scheme: light;
}
.symbol-select option {
  background: #fff;
  color: #111;
}
.summary { color: #888; margin-bottom: 16px; font-size: 0.95rem; }
.error { padding: 12px; background: #fee; color: #c00; border-radius: 6px; margin-bottom: 16px; }
.loading { text-align: center; padding: 24px; color: #888; }
.table-wrap { overflow-x: auto; border: 1px solid #444; border-radius: 8px; }
.table-wrap-stats { contain: layout; min-height: 120px; }
.table-wrap-prices { contain: layout; min-height: 180px; }
.table-wrap-prices.has-rows { min-height: auto; }
.data-table { width: 100%; border-collapse: collapse; table-layout: fixed; }
.data-table-prices { table-layout: auto; }
.data-table th, .data-table td { padding: 12px 16px; text-align: left; border-bottom: 1px solid #444; }
.data-table th { background: #3a3a3a; color: #e8e8e8; font-weight: 600; }
.data-table tbody tr { background: #fff; color: #1a1a1a; }
.data-table tbody tr:hover { background: #f5f5f5; }
.fee-used, .profit-value { color: #0a7d0a; font-weight: 600; }
.loading-cell { text-align: center; padding: 24px; color: #888; }
.empty { color: #888; font-size: 0.95rem; margin-top: 16px; }
</style>
