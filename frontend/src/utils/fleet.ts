import type { LifecycleStatus, VehicleCreateRequest } from '@/types/vehicle'

export const VEHICLE_CSV_HEADERS = [
  'vehicleId', 'companyId', 'lockId', 'controllerId', 'plateNumber', 'filingCode',
  'model', 'batchNo', 'operationCityCode', 'operationAreaCode', 'launchDate', 'lifecycleStatus',
] as const

export interface VehicleCsvResult {
  rows: VehicleCreateRequest[]
  errors: string[]
}

/** 输入: 一行 CSV; 输出: 支持双引号和转义引号的字段数组。 */
function splitCsvLine(line: string): string[] {
  const fields: string[] = []
  let field = ''
  let quoted = false
  for (let index = 0; index < line.length; index += 1) {
    const character = line[index]!
    if (character === '"' && quoted && line[index + 1] === '"') {
      field += '"'
      index++
    } else if (character === '"') quoted = !quoted
    else if (character === ',' && !quoted) {
      fields.push(field.trim())
      field = ''
    } else field += character
  }
  fields.push(field.trim())
  return fields
}

/**
 * 输入: CSV 文本和可用城市代码; 输出: 可提交车辆及预检错误。
 *
 * 步骤:
 * 1. 校验固定表头，避免列错位后生成错误资产。
 * 2. 校验必填值、行政区、日期、状态和当前可用城市。
 * 3. 对文件内车辆、车锁、控制器去重，数据库仍负责最终并发判重。
 */
export function parseVehicleCsv(text: string, cityCodes: ReadonlySet<string>): VehicleCsvResult {
  const lines = text.replace(/^\uFEFF/, '').split(/\r?\n/).filter((line) => line.trim())
  if (lines.length === 0) return { rows: [], errors: ['CSV 内容为空'] }
  const headers = splitCsvLine(lines[0]!)
  if (headers.join(',') !== VEHICLE_CSV_HEADERS.join(',')) {
    return { rows: [], errors: ['CSV 表头不匹配，请使用页面提供的模板'] }
  }
  if (lines.length > 501) return { rows: [], errors: ['单次最多导入 500 辆车辆'] }

  const rows: VehicleCreateRequest[] = []
  const errors: string[] = []
  const vehicleIds = new Set<string>()
  const lockIds = new Set<string>()
  const controllerIds = new Set<string>()
  const statuses = new Set<LifecycleStatus>([
    'PENDING', 'OPERATING', 'MAINTENANCE', 'DISPATCHING', 'RETIRED', 'IMPOUNDED',
  ])

  lines.slice(1).forEach((line, index) => {
    const rowNumber = index + 2
    const values = splitCsvLine(line)
    if (values.length !== VEHICLE_CSV_HEADERS.length) {
      errors.push(`第 ${rowNumber} 行列数不正确`)
      return
    }
    const [vehicleId, companyId, lockId, controllerId, plateNumber, filingCode, model, batchNo,
      operationCityCode, operationAreaCode, launchDate, lifecycleStatus] = values as string[]
    if (![vehicleId, companyId, lockId, controllerId, model, operationCityCode, operationAreaCode, launchDate, lifecycleStatus].every(Boolean)) {
      errors.push(`第 ${rowNumber} 行缺少必填字段`)
      return
    }
    if (!/^\d{6}$/.test(operationAreaCode!) || !cityCodes.has(operationCityCode!)) {
      errors.push(`第 ${rowNumber} 行城市或运营区域无效`)
      return
    }
    const parsedDate = new Date(`${launchDate}T00:00:00Z`)
    const validDate = /^\d{4}-\d{2}-\d{2}$/.test(launchDate!)
      && !Number.isNaN(parsedDate.getTime())
      && parsedDate.toISOString().slice(0, 10) === launchDate
    if (!validDate || !statuses.has(lifecycleStatus as LifecycleStatus)) {
      errors.push(`第 ${rowNumber} 行日期或生命周期状态无效`)
      return
    }
    if (vehicleIds.has(vehicleId!) || lockIds.has(lockId!) || controllerIds.has(controllerId!)) {
      errors.push(`第 ${rowNumber} 行车辆、车锁或控制器编号在文件内重复`)
      return
    }
    vehicleIds.add(vehicleId!); lockIds.add(lockId!); controllerIds.add(controllerId!)
    rows.push({
      vehicleId: vehicleId!, companyId: companyId!, lockId: lockId!, controllerId: controllerId!,
      plateNumber: plateNumber || null, filingCode: filingCode || null, model: model!, batchNo: batchNo || null,
      operationCityCode: operationCityCode!, operationAreaCode: operationAreaCode!, launchDate: launchDate!,
      lifecycleStatus: lifecycleStatus as LifecycleStatus,
    })
  })
  return { rows, errors }
}
