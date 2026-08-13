import axios from 'axios'
import { describe, expect, it } from 'vitest'

import { shouldRetryRequest } from '@/api/http'

describe('HTTP 重试策略', () => {
  it('仅重试幂等请求的临时故障', () => {
    expect(shouldRetryRequest(new axios.AxiosError('timeout', 'ECONNABORTED', { method: 'get', headers: new axios.AxiosHeaders() }))).toBe(true)
    expect(shouldRetryRequest(new axios.AxiosError('timeout', 'ECONNABORTED', { method: 'post', headers: new axios.AxiosHeaders() }))).toBe(false)
  })
})
