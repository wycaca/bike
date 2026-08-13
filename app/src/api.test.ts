import axios from 'axios'

import { shouldRetryRequest } from '@/api'

describe('移动端 HTTP 重试策略', () => {
  it('仅重试幂等请求的临时故障', () => {
    expect(shouldRetryRequest(new axios.AxiosError('network', 'ERR_NETWORK', { method: 'get', headers: new axios.AxiosHeaders() }))).toBe(true)
    expect(shouldRetryRequest(new axios.AxiosError('network', 'ERR_NETWORK', { method: 'post', headers: new axios.AxiosHeaders() }))).toBe(false)
  })
})
