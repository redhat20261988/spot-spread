import axios from 'axios'

export function getSpreadStats() {
  return axios.get('/api/spread-stats')
}
