import assert from 'node:assert/strict'
import { writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const OUTPUT_DIRECTORY = resolve(import.meta.dirname, '../src/main/resources/mock')
const VEHICLE_OUTPUT = resolve(OUTPUT_DIRECTORY, 'vehicles.json')
const EVENT_OUTPUT = resolve(OUTPUT_DIRECTORY, 'yadea-cloud-events.json')
const API_URL = 'https://restapi.amap.com/v5/direction/electrobike'
const API_KEY = process.env.AMAP_WEB_SERVICE_KEY?.trim()
const COUNT_PER_CITY = 100
const CONCURRENCY = 1

const CITIES = [
  {
    prefix: 'BJ',
    cityCode: '110000',
    platePrefix: '?',
    seed: 110000,
    timeOffsetHours: 0,
    areas: [
      { code: '110101', center: [116.418, 39.926] },
      { code: '110102', center: [116.365, 39.914] },
      { code: '110105', center: [116.474, 39.923] },
      { code: '110106', center: [116.286, 39.858] },
      { code: '110107', center: [116.224, 39.906] },
      { code: '110108', center: [116.314, 39.974] },
    ],
  },
  {
    prefix: 'SH',
    cityCode: '310000',
    platePrefix: '?',
    seed: 310000,
    timeOffsetHours: 12,
    areas: [
      { code: '310101', center: [121.484, 31.231] },
      { code: '310104', center: [121.438, 31.188] },
      { code: '310105', center: [121.405, 31.214] },
      { code: '310106', center: [121.452, 31.238] },
      { code: '310110', center: [121.522, 31.296] },
      { code: '310115', center: [121.558, 31.229] },
    ],
  },
]

function createRandom(seed) {
  let value = seed >>> 0
  return () => {
    value += 0x6d2b79f5
    let result = value
    result = Math.imul(result ^ (result >>> 15), result | 1)
    result ^= result + Math.imul(result ^ (result >>> 7), result | 61)
    return ((result ^ (result >>> 14)) >>> 0) / 4294967296
  }
}

function round(value, digits = 7) {
  return Number(value.toFixed(digits))
}

function toCoordinate(value) {
  return value.map((item) => Number(item))
}

function transformLatitude(longitude, latitude) {
  let result = -100 + 2 * longitude + 3 * latitude
    + 0.2 * latitude * latitude + 0.1 * longitude * latitude
    + 0.2 * Math.sqrt(Math.abs(longitude))
  result += (20 * Math.sin(6 * longitude * Math.PI)
    + 20 * Math.sin(2 * longitude * Math.PI)) * 2 / 3
  result += (20 * Math.sin(latitude * Math.PI)
    + 40 * Math.sin(latitude / 3 * Math.PI)) * 2 / 3
  result += (160 * Math.sin(latitude / 12 * Math.PI)
    + 320 * Math.sin(latitude * Math.PI / 30)) * 2 / 3
  return result
}

function transformLongitude(longitude, latitude) {
  let result = 300 + longitude + 2 * latitude
    + 0.1 * longitude * longitude + 0.1 * longitude * latitude
    + 0.1 * Math.sqrt(Math.abs(longitude))
  result += (20 * Math.sin(6 * longitude * Math.PI)
    + 20 * Math.sin(2 * longitude * Math.PI)) * 2 / 3
  result += (20 * Math.sin(longitude * Math.PI)
    + 40 * Math.sin(longitude / 3 * Math.PI)) * 2 / 3
  result += (150 * Math.sin(longitude / 12 * Math.PI)
    + 300 * Math.sin(longitude / 30 * Math.PI)) * 2 / 3
  return result
}

function wgs84ToGcj02(longitude, latitude) {
  const axis = 6378245
  const eccentricity = 0.006693421622965943
  let latitudeOffset = transformLatitude(longitude - 105, latitude - 35)
  let longitudeOffset = transformLongitude(longitude - 105, latitude - 35)
  const radianLatitude = latitude / 180 * Math.PI
  let magic = Math.sin(radianLatitude)
  magic = 1 - eccentricity * magic * magic
  const squareRootMagic = Math.sqrt(magic)
  latitudeOffset = latitudeOffset * 180
    / ((axis * (1 - eccentricity)) / (magic * squareRootMagic) * Math.PI)
  longitudeOffset = longitudeOffset * 180
    / (axis / squareRootMagic * Math.cos(radianLatitude) * Math.PI)
  return [longitude + longitudeOffset, latitude + latitudeOffset]
}

function gcj02ToWgs84(longitude, latitude) {
  let wgsLongitude = longitude
  let wgsLatitude = latitude
  for (let iteration = 0; iteration < 4; iteration += 1) {
    const converted = wgs84ToGcj02(wgsLongitude, wgsLatitude)
    wgsLongitude -= converted[0] - longitude
    wgsLatitude -= converted[1] - latitude
  }
  return [round(wgsLongitude), round(wgsLatitude)]
}

function distanceMeters(first, second) {
  const earthRadius = 6371008.8
  const latitude1 = first[1] * Math.PI / 180
  const latitude2 = second[1] * Math.PI / 180
  const latitudeDelta = (second[1] - first[1]) * Math.PI / 180
  const longitudeDelta = (second[0] - first[0]) * Math.PI / 180
  const haversine = Math.sin(latitudeDelta / 2) ** 2
    + Math.cos(latitude1) * Math.cos(latitude2) * Math.sin(longitudeDelta / 2) ** 2
  return earthRadius * 2 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine))
}

function directionDegrees(first, second) {
  const latitude1 = first[1] * Math.PI / 180
  const latitude2 = second[1] * Math.PI / 180
  const longitudeDelta = (second[0] - first[0]) * Math.PI / 180
  const y = Math.sin(longitudeDelta) * Math.cos(latitude2)
  const x = Math.cos(latitude1) * Math.sin(latitude2)
    - Math.sin(latitude1) * Math.cos(latitude2) * Math.cos(longitudeDelta)
  return Math.round((Math.atan2(y, x) * 180 / Math.PI + 360) % 360)
}

function samplePolyline(points, count) {
  const cumulative = [0]
  for (let index = 1; index < points.length; index += 1) {
    cumulative.push(cumulative[index - 1] + distanceMeters(points[index - 1], points[index]))
  }
  const total = cumulative[cumulative.length - 1]
  const sampled = []
  let segment = 1
  for (let index = 0; index < count; index += 1) {
    const target = total * index / (count - 1)
    while (segment < cumulative.length - 1 && cumulative[segment] < target) segment += 1
    const startDistance = cumulative[segment - 1]
    const segmentDistance = cumulative[segment] - startDistance || 1
    const ratio = (target - startDistance) / segmentDistance
    const start = points[segment - 1]
    const end = points[segment]
    sampled.push([
      start[0] + (end[0] - start[0]) * ratio,
      start[1] + (end[1] - start[1]) * ratio,
    ])
  }
  return sampled
}

function parsePolyline(path) {
  const points = path.steps
    .flatMap((step) => String(step.polyline ?? '').split(';'))
    .filter(Boolean)
    .map((pair) => toCoordinate(pair.split(',')))
  return points.filter((point, index) =>
    index === 0 || point[0] !== points[index - 1][0] || point[1] !== points[index - 1][1])
}

function routeEndpoints(city, random, attempt) {
  const area = city.areas[Math.floor(random() * city.areas.length)]
  const origin = [
    area.center[0] + (random() - 0.5) * 0.012,
    area.center[1] + (random() - 0.5) * 0.010,
  ]
  const distanceKm = 1.5 + random() * 4.5 + attempt * 0.2
  const bearing = random() * Math.PI * 2
  const destination = [
    origin[0] + Math.sin(bearing) * distanceKm / (111 * Math.cos(origin[1] * Math.PI / 180)),
    origin[1] + Math.cos(bearing) * distanceKm / 111,
  ]
  return { area, origin, destination }
}

async function requestRoute(city, random, vehicleId) {
  let lastError
  for (let attempt = 0; attempt < 4; attempt += 1) {
    await new Promise((resolvePromise) => setTimeout(resolvePromise, 650))
    const endpoints = routeEndpoints(city, random, attempt)
    try {
      const parameters = new URLSearchParams({
        key: API_KEY,
        origin: endpoints.origin.map((value) => value.toFixed(6)).join(','),
        destination: endpoints.destination.map((value) => value.toFixed(6)).join(','),
        show_fields: 'polyline,cost',
      })
      const response = await fetch(API_URL + '?' + parameters)
      if (!response.ok) throw new Error('HTTP ' + response.status)
      const payload = await response.json()
      const path = payload.route?.paths?.[0]
      if (payload.status !== '1' || !path) {
        throw new Error(payload.info || '???????')
      }
      const distance = Number(path.distance)
      const duration = Number(path.duration)
      const points = parsePolyline(path)
      if (distance < 1000 || distance > 9000 || duration <= 0 || points.length < 10) {
        throw new Error('?????????? Mock ??')
      }
      return { ...endpoints, distance, duration, points }
    } catch (error) {
      lastError = error
      await new Promise((resolvePromise) => setTimeout(resolvePromise, 400 * (attempt + 1)))
    }
  }
  throw new Error(vehicleId + ' ??????: ' + (lastError?.message ?? '????'))
}

function lifecycleStatus(index) {
  if (index % 50 === 0) return 'IMPOUNDED'
  if (index % 25 === 0) return 'MAINTENANCE'
  if (index % 20 === 0) return 'DISPATCHING'
  if (index % 33 === 0) return 'PENDING'
  return 'OPERATING'
}

function vehicleAsset(city, area, index) {
  const sequence = String(index).padStart(6, '0')
  return {
    vehicleId: 'YD-' + city.prefix + '-' + sequence,
    companyId: 'MOCK01',
    lockId: 'YDLOCK-' + city.prefix + '-' + sequence,
    controllerId: 'YDCTRL-' + city.prefix + '-' + sequence,
    plateNumber: city.platePrefix + '????' + sequence,
    filingCode: city.prefix + 'MOCK' + sequence,
    model: index % 3 === 0 ? '??????? Mock B?' : '??????? Mock A?',
    batchNo: city.prefix + '-2026-' + String(1 + index % 3).padStart(2, '0'),
    operationCityCode: city.cityCode,
    operationAreaCode: area.code,
    launchDate: index % 3 === 0 ? '2026-07-15' : '2026-07-01',
    lifecycleStatus: lifecycleStatus(index),
  }
}

function addGpsNoise(point, random, accuracyMeters) {
  const angle = random() * Math.PI * 2
  const distance = accuracyMeters * (0.15 + random() * 0.45)
  return [
    point[0] + Math.cos(angle) * distance / (111320 * Math.cos(point[1] * Math.PI / 180)),
    point[1] + Math.sin(angle) * distance / 110540,
  ]
}

function createEvents(city, route, index, random) {
  const sequence = String(index).padStart(6, '0')
  const vehicleId = 'YD-' + city.prefix + '-' + sequence
  const deviceId = 'YDCTRL-' + city.prefix + '-' + sequence
  const sampleCount = Math.min(60, Math.max(20, Math.round(route.duration / 20) + 1))
  const sampled = samplePolyline(route.points, sampleCount)
  const intervalSeconds = route.duration / (sampleCount - 1)
  const routeStart = Date.UTC(2026, 7, 9, city.timeOffsetHours, 0, 0) + index * 180000
  const lowBattery = index % 17 === 0
  const offline = index % 20 === 0
  const fault = index % 37 === 0
  const batteryStart = lowBattery ? 18 : 55 + Math.floor(random() * 41)
  const batteryDrop = Math.max(2, Math.round(route.distance / 1000 * 1.4))

  return sampled.map((gcjPoint, pointIndex) => {
    const isLast = pointIndex === sampled.length - 1
    const accuracyMeters = round(3.5 + random() * 7.5, 1)
    const wgsPoint = addGpsNoise(gcj02ToWgs84(gcjPoint[0], gcjPoint[1]), random, accuracyMeters)
    const nextPoint = sampled[Math.min(pointIndex + 1, sampled.length - 1)]
    const previousPoint = sampled[Math.max(pointIndex - 1, 0)]
    const segmentStart = isLast ? previousPoint : gcjPoint
    const segmentEnd = isLast ? gcjPoint : nextPoint
    const speed = isLast
      ? 0
      : Math.min(25, Math.max(5, distanceMeters(segmentStart, segmentEnd) / intervalSeconds * 3.6))
    const batteryPercent = Math.max(8,
      Math.round(batteryStart - batteryDrop * pointIndex / (sampled.length - 1)))
    const status = lifecycleStatus(index)
    const finalRideStatus = status === 'MAINTENANCE'
      ? 'MAINTENANCE'
      : status === 'DISPATCHING' ? 'DISPATCHING' : 'IDLE'
    const faultCodes = []
    if (isLast && lowBattery) faultCodes.push('MOCK_LOW_BATTERY')
    if (isLast && fault) faultCodes.push('MOCK_CONTROLLER_COMMUNICATION')

    return {
      eventId: 'MOCK-ROAD-' + city.prefix + '-' + sequence + '-' + String(pointIndex + 1).padStart(3, '0'),
      vehicleId,
      deviceId,
      occurredAt: new Date(routeStart + Math.round(pointIndex * intervalSeconds * 1000)).toISOString(),
      location: {
        longitude: round(wgsPoint[0]),
        latitude: round(wgsPoint[1]),
        coordinateSystem: 'WGS84',
        accuracyMeters,
        speedKmh: round(speed, 1),
        directionDegrees: directionDegrees(segmentStart, segmentEnd),
        satelliteCount: 9 + Math.floor(random() * 9),
      },
      state: {
        lockStatus: isLast ? 'LOCKED' : 'UNLOCKED',
        rideStatus: isLast ? finalRideStatus : 'RIDING',
        controllerStatus: isLast && offline ? 'OFFLINE' : isLast && fault ? 'FAULT' : 'NORMAL',
        batteryPercent,
        remainingRangeKm: round(batteryPercent * 0.55, 1),
        online: !(isLast && offline),
        signalStrength: -55 - Math.floor(random() * 36),
        faultCodes,
      },
      rawData: {
        schema: 'mock-yadea-cloud-v1',
        source: 'amap-electrobike-v5',
        mockRoute: true,
        routeDistanceMeters: route.distance,
        routeDurationSeconds: route.duration,
        sampleIntervalSeconds: round(intervalSeconds, 1),
        firmwareVersion: 'MOCK-2.0.0',
        networkType: index % 5 === 0 ? 'NB-IoT' : '4G',
      },
    }
  })
}

async function generateVehicle(task) {
  const random = createRandom(task.city.seed + task.index * 97)
  const sequence = String(task.index).padStart(6, '0')
  const vehicleId = 'YD-' + task.city.prefix + '-' + sequence
  const route = await requestRoute(task.city, random, vehicleId)
  return {
    vehicle: vehicleAsset(task.city, route.area, task.index),
    events: createEvents(task.city, route, task.index, random),
  }
}

async function mapWithConcurrency(items, concurrency, operation) {
  const results = new Array(items.length)
  let nextIndex = 0
  await Promise.all(Array.from({ length: concurrency }, async () => {
    while (nextIndex < items.length) {
      const currentIndex = nextIndex
      nextIndex += 1
      results[currentIndex] = await operation(items[currentIndex])
      if ((currentIndex + 1) % 20 === 0) {
        console.log('??? ' + (currentIndex + 1) + '/' + items.length + ' ?')
      }
    }
  }))
  return results
}

function selfTest() {
  const converted = wgs84ToGcj02(116.397, 39.909)
  const restored = gcj02ToWgs84(converted[0], converted[1])
  assert.ok(Math.abs(restored[0] - 116.397) < 0.000001)
  assert.ok(Math.abs(restored[1] - 39.909) < 0.000001)
  const sampled = samplePolyline([[116.4, 39.9], [116.41, 39.9]], 5)
  assert.equal(sampled.length, 5)
  assert.equal(round(sampled[2][0], 3), 116.405)
  console.log('Mock ??????????')
}

if (process.argv.includes('--self-test')) {
  selfTest()
  process.exit(0)
}
if (!API_KEY) {
  throw new Error('??? AMAP_WEB_SERVICE_KEY')
}

const tasks = CITIES.flatMap((city) =>
  Array.from({ length: COUNT_PER_CITY }, (_, index) => ({ city, index: index + 1 })))
const generated = await mapWithConcurrency(tasks, CONCURRENCY, generateVehicle)
const vehicles = generated.map((item) => item.vehicle)
const events = generated.flatMap((item) => item.events)
await writeFile(VEHICLE_OUTPUT, JSON.stringify(vehicles, null, 2) + '\n', 'utf8')
await writeFile(EVENT_OUTPUT, JSON.stringify(events, null, 2) + '\n', 'utf8')
console.log('????: ?? ' + vehicles.length + ' ?, ??? ' + events.length + ' ?')

