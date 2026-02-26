import axios from 'axios'

export function getSpreadStats() {
  return axios.get('/api/spread-stats')
}

export function getExchangePrices(symbol = 'BTC') {
  return axios.get('/api/exchange-prices', { params: { symbol } })
}
