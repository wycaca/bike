import assert from 'node:assert/strict'
import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname } from 'node:path'
import { performance } from 'node:perf_hooks'

function integerEnv(name, fallback, minimum = 1) {
  const value = Number.parseInt(process.env[name] ?? String(fallback), 10)
  if (!Number.isInteger(value) || value < minimum) throw new Error(`${name} 必须是不小于 ${minimum} 的整数`)
  return value
}

function numberEnv(name, fallback, minimum = 0) {
  const value = Number.parseFloat(process.env[name] ?? String(fallback))
  if (!Number.isFinite(value) || value < minimum) throw new Error(`${name} 必须是不小于 ${minimum} 的数字`)
  return value
}

function percentile(values, percentage) {
  if (!values.length) return 0
  const sorted = [...values].sort((left, right) => left - right)
  return sorted[Math.min(sorted.length - 1, Math.ceil((percentage / 100) * sorted.length) - 1)]
}

function vehicleId(number) {
  const city = number % 2 === 1 ? 'BJ' : 'SH'
  return `LT-${city}-${String(number).padStart(6, '0')}`
}

// 使用固定比例而不是随机权重, 保证不同轮次请求构成可比较.
function readScenario(index, vehicleCount, baseUrl) {
  const slot = index % 20
  const number = ((index * 7919) % vehicleCount) + 1
  if (slot < 7) {
    const page = (index % Math.max(1, Math.ceil(vehicleCount / 50))) + 1
    return { name: '车辆分页', url: `${baseUrl}/api/v1/vehicles?page=${page}&pageSize=50` }
  }
  if (slot < 12) {
    const bounds = index % 2 === 0
      ? 'minLongitude=116.10&minLatitude=39.65&maxLongitude=116.65&maxLatitude=40.10'
      : 'minLongitude=121.20&minLatitude=31.00&maxLongitude=121.75&maxLatitude=31.45'
    return { name: '地图聚合', url: `${baseUrl}/api/v1/map/vehicles?${bounds}&zoom=12&coordinateSystem=GCJ02` }
  }
  if (slot < 16) {
    const bounds = index % 2 === 0
      ? 'minLongitude=116.30&minLatitude=39.78&maxLongitude=116.38&maxLatitude=39.87'
      : 'minLongitude=121.40&minLatitude=31.13&maxLongitude=121.48&maxLatitude=31.22'
    return { name: '地图车辆点', url: `${baseUrl}/api/v1/map/vehicles?${bounds}&zoom=16&coordinateSystem=GCJ02` }
  }
  if (slot < 18) {
    return { name: '车辆详情', url: `${baseUrl}/api/v1/vehicles/${vehicleId(number)}` }
  }
  return {
    name: '历史轨迹',
    url: `${baseUrl}/api/v1/vehicles/${vehicleId(number)}/trajectory?startTime=2026-08-01T00:00:00Z&endTime=2026-08-02T00:00:00Z&coordinateSystem=GCJ02`,
  }
}

// 每条事件使用递增毫秒时间, 避免同车同时间主键冲突影响最终落库数.
function ingestScenario(index, config) {
  const number = ((index * 7919) % config.vehicleCount) + 1
  const beijing = number % 2 === 1
  const longitude = (beijing ? 116.2 : 121.3) + (number % 100) * 0.003
  const latitude = (beijing ? 39.75 : 31.1) + (Math.floor((number - 1) / 100) % 100) * 0.003
  return {
    name: '遥测写入',
    url: `${config.baseUrl}/api/v1/mock/yadea/events`,
    method: 'POST',
    body: JSON.stringify({
      eventId: `LT-${config.runId}-${index}`,
      vehicleId: vehicleId(number),
      deviceId: `LT-DEVICE-${String(number).padStart(6, '0')}`,
      occurredAt: new Date(config.eventBaseTime + index).toISOString(),
      location: {
        longitude,
        latitude,
        coordinateSystem: 'WGS84',
        accuracyMeters: 5,
        speedKmh: 15,
        directionDegrees: index % 360,
        satelliteCount: 12,
      },
      state: {
        lockStatus: 'UNLOCKED',
        rideStatus: 'RIDING',
        controllerStatus: 'NORMAL',
        batteryPercent: 80,
        remainingRangeKm: 44,
        online: true,
        signalStrength: -55,
        faultCodes: [],
      },
      rawData: { loadTest: true, loadTestRunId: config.runId },
    }),
  }
}

function scenarioFor(index, config) {
  return config.mode === 'ingest'
    ? ingestScenario(index, config)
    : readScenario(index, config.vehicleCount, config.baseUrl)
}

function metric() {
  return { requests: 0, successes: 0, failures: 0, latencies: [] }
}

function summarize(source) {
  const totalLatency = source.latencies.reduce((sum, value) => sum + value, 0)
  return {
    requests: source.requests,
    successes: source.successes,
    failures: source.failures,
    errorRate: source.requests ? source.failures / source.requests : 0,
    latencyMs: {
      average: source.requests ? totalLatency / source.requests : 0,
      p50: percentile(source.latencies, 50),
      p95: percentile(source.latencies, 95),
      p99: percentile(source.latencies, 99),
      max: source.latencies.length ? Math.max(...source.latencies) : 0,
    },
  }
}

async function runLoadTest(config) {
  const overall = metric()
  const scenarios = new Map()
  const errors = []
  let sequence = 0

  // 每个并发 worker 串行发送请求, 用并发数量控制在途请求并避免额外调度依赖.
  async function runPhase(seconds, collect) {
    const deadline = performance.now() + seconds * 1000
    async function worker() {
      while (performance.now() < deadline) {
        const scenario = scenarioFor(sequence++, config)
        const startedAt = performance.now()
        let error = null
        try {
          const response = await fetch(scenario.url, {
            method: scenario.method ?? 'GET',
            headers: {
              accept: 'application/json',
              ...(scenario.body ? { 'content-type': 'application/json' } : {}),
            },
            body: scenario.body,
            signal: AbortSignal.timeout(config.timeoutMs),
          })
          const body = await response.json()
          if (!response.ok || body.code !== 0) error = `HTTP ${response.status}, 业务码 ${body.code}`
        } catch (cause) {
          error = cause instanceof Error ? cause.message : String(cause)
        }
        if (!collect) continue

        const latency = performance.now() - startedAt
        const scenarioMetric = scenarios.get(scenario.name) ?? metric()
        scenarios.set(scenario.name, scenarioMetric)
        for (const target of [overall, scenarioMetric]) {
          target.requests++
          target.latencies.push(latency)
          if (error) target.failures++
          else target.successes++
        }
        if (error && errors.length < 10) errors.push({ scenario: scenario.name, error })
      }
    }
    await Promise.all(Array.from({ length: config.concurrency }, worker))
  }

  await runPhase(config.warmupSeconds, false)
  const startedAt = new Date().toISOString()
  await runPhase(config.durationSeconds, true)
  const finishedAt = new Date().toISOString()

  const summary = summarize(overall)
  const requestsPerSecond = summary.requests / config.durationSeconds
  // HTTP 写入只代表已排队, Kafka lag 和落库一致性由测试文档中的后置检查负责.
  const thresholds = {
    errorRate: { actual: summary.errorRate, maximum: config.maxErrorRate, passed: summary.errorRate <= config.maxErrorRate },
    p95Ms: { actual: summary.latencyMs.p95, maximum: config.maxP95Ms, passed: summary.latencyMs.p95 <= config.maxP95Ms },
    requestsPerSecond: { actual: requestsPerSecond, minimum: config.minRps, passed: requestsPerSecond >= config.minRps },
  }

  return {
    startedAt,
    finishedAt,
    config,
    summary: { ...summary, requestsPerSecond },
    scenarios: Object.fromEntries([...scenarios].map(([name, value]) => [name, summarize(value)])),
    thresholds,
    passed: Object.values(thresholds).every((threshold) => threshold.passed),
    errors,
  }
}

function selfTest() {
  assert.equal(percentile([1, 2, 3, 4], 95), 4)
  assert.equal(vehicleId(1), 'LT-BJ-000001')
  assert.equal(vehicleId(2), 'LT-SH-000002')
  const config = { mode: 'read', vehicleCount: 5000, baseUrl: 'http://localhost:8080' }
  assert.equal(scenarioFor(0, config).name, '车辆分页')
  assert.equal(scenarioFor(19, config).name, '历史轨迹')
  console.log('压测脚本自检通过')
}

if (process.argv.includes('--self-test')) {
  selfTest()
} else {
  const mode = process.env.MODE ?? 'read'
  if (!['read', 'ingest'].includes(mode)) throw new Error('MODE 只能是 read 或 ingest')
  const config = {
    mode,
    baseUrl: (process.env.BASE_URL ?? 'http://localhost:8080').replace(/\/$/, ''),
    concurrency: integerEnv('CONCURRENCY', 20),
    warmupSeconds: integerEnv('WARMUP_SECONDS', mode === 'ingest' ? 0 : 5, 0),
    durationSeconds: integerEnv('DURATION_SECONDS', 30),
    timeoutMs: integerEnv('TIMEOUT_MS', 5000),
    vehicleCount: integerEnv('VEHICLE_COUNT', 5000),
    maxErrorRate: numberEnv('MAX_ERROR_RATE', 0.001),
    maxP95Ms: numberEnv('MAX_P95_MS', 200),
    minRps: numberEnv('MIN_RPS', 500),
    runId: process.env.RUN_ID ?? new Date().toISOString().replace(/\D/g, '').slice(0, 17),
    eventBaseTime: Date.now(),
  }
  const result = await runLoadTest(config)
  const json = `${JSON.stringify(result, null, 2)}\n`
  if (process.env.LOAD_TEST_OUTPUT) {
    mkdirSync(dirname(process.env.LOAD_TEST_OUTPUT), { recursive: true })
    writeFileSync(process.env.LOAD_TEST_OUTPUT, json, 'utf8')
  }
  process.stdout.write(json)
  if (!result.passed) process.exitCode = 1
}
