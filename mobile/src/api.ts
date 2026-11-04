import Constants from 'expo-constants'
import { Platform } from 'react-native'

export type Status = 'AVAILABLE' | 'IN_TRANSIT' | 'RETURNING' | 'MAINTENANCE' | 'ON_LEAVE'
export type VehicleType = 'VAN' | 'RIGID_TRUCK' | 'ARTICULATED_TRUCK'
export type GoodsType =
  | 'GENERAL'
  | 'REFRIGERATED'
  | 'HAZARDOUS'
  | 'OVERSIZED'
  | 'BULK'
  | 'LIQUID'
  | 'CONTAINERIZED'
  | 'VEHICLES'
  | 'LIVESTOCK'

export interface Driver {
  id: number
  name: string
  email: string
  licenseCategories: string[]
  status: Status
}

export interface VehicleMaintenanceComponent {
  key: string
  label: string
  serviceIntervalKm: number | null
  serviceIntervalMonths: number | null
  inspectionNote: string | null
  lastPerformedAt: string | null
  lastOdometerKm: number | null
  healthPercent: number | null
  distanceRemainingKm: number | null
  daysRemaining: number | null
  lastCostEur: number | null
}

export interface Vehicle {
  id: number
  licensePlate: string
  vehicleType: VehicleType
  make: string
  model: string
  year: number
  maxWeightCapacityKg: number
  maxVolumeM3: number
  status: Status
  currentMileageKm: number
  transmissionType: 'MANUAL' | 'AUTOMATIC' | 'AMT'
  maintenanceAutoTriggered: boolean
  maintenanceComponents: VehicleMaintenanceComponent[]
}

export interface Trailer {
  id: number
  licensePlate: string
  trailerType: string
  compatibleVehicleType: VehicleType
  make: string
  model: string
  year: number
  maxWeightCapacityKg: number
  maxVolumeM3: number
  status: Status
  maintenanceAutoTriggered: boolean
  maintenanceComponents: TrailerMaintenanceComponent[]
  maintenanceHistory: TrailerMaintenanceRecord[]
}

export interface TrailerMaintenanceComponent {
  key: string
  label: string
  serviceIntervalMonths: number
  lastPerformedAt: string | null
  healthPercent: number | null
  daysRemaining: number | null
  lastCostEur: number | null
}

export interface TrailerMaintenanceRecord {
  componentKey: string
  componentLabel: string
  performedAt: string
  costEur: number | null
}

export interface Goods {
  id: number
  trackingCode: string
  clientName: string
  goodsType: GoodsType
  description: string | null
  weightKg: number
  revenueEur: number | null
  volumeM3: number
  pickupCity: string
  deliveryCity: string
  status: 'UNASSIGNED' | 'ASSIGNED' | 'IN_TRANSIT' | 'DELIVERED' | 'ARCHIVED'
}

export interface RouteStop {
  goodsId: number
  trackingCode: string
  type: 'PICKUP' | 'DELIVERY'
  stopOrder: number
  city: string
  latitude: number | null
  longitude: number | null
}

export interface RouteCoordinate {
  latitude: number
  longitude: number
}

export interface RouteSimulationPosition extends RouteCoordinate {
  routeId: number
  headingDegrees: number
  heading: string
  progress: number
  calculatedAt: string
}

export interface Route {
  id: number
  driverId: number
  driverName: string
  vehicleId: number
  vehicleLicensePlate: string
  trailerId: number | null
  trailerLicensePlate: string | null
  departureTime: string
  estimatedArrivalTime: string
  estimatedReturnTime: string | null
  returnedAt: string | null
  distanceMeters: number
  drivingDurationSeconds: number
  status: 'PLANNED' | 'ACTIVE' | 'COMPLETED' | 'CANCELLED'
  stops: RouteStop[]
  geometry: RouteCoordinate[]
}

export interface FinanceReport {
  from: string
  to: string
  freightRevenueEur: number
  maintenanceCostsEur: number
  netProfitEur: number
  deliveredFreightCount: number
  unpricedFreightCount: number
  uncostedMaintenanceCount: number
  trend: { period: string; revenueEur: number; maintenanceCostsEur: number }[]
  drivers: {
    driverId: number
    driverName: string
    grossContributionEur: number
    deliveredFreightCount: number
  }[]
  vehicles: {
    vehicleId: number
    licensePlate: string
    freightRevenueEur: number
    maintenanceCostsEur: number
    netProfitEur: number
  }[]
  maintenanceByCategory: { category: string; costEur: number; operationCount: number }[]
  freightByCategory: { category: string; revenueEur: number; shipmentCount: number }[]
  freightMargins: {
    freightId: number
    trackingCode: string
    clientName: string
    category: string
    driverName: string
    vehicleLicensePlate: string
    revenueEur: number | null
    estimatedMaintenanceEur: number
    estimatedMarginEur: number | null
    estimatedMarginPercent: number | null
  }[]
}

export interface AssistantMessage {
  role: 'user' | 'assistant'
  content: string
}

export interface AssistantAction {
  type: string
  summary: string
  id: number | null
  payload: Record<string, unknown>
}

export interface AssistantReply {
  message: string
  action: AssistantAction | null
}

const expoHostUri = Constants.expoConfig?.hostUri
const expoHost = expoHostUri ? new URL(`http://${expoHostUri}`).hostname : undefined
const defaultApiHost = expoHost ?? (Platform.OS === 'android' ? '10.0.2.2' : 'localhost')
const apiBaseUrl = (
  process.env.EXPO_PUBLIC_API_BASE_URL ?? `http://${defaultApiHost}:8080/api`
).replace(/\/+$/, '')

export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(`${apiBaseUrl}${path}`, {
      ...init,
      headers: {
        Accept: 'application/json',
        ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
        ...init?.headers,
      },
    })
  } catch (error) {
    throw new Error(
      error instanceof Error
        ? `Cannot reach ${apiBaseUrl}. Check the backend URL and that your phone and computer are on the same network.`
        : 'Could not connect to the backend.',
    )
  }

  if (!response.ok) {
    let message = `Request failed (${response.status}).`
    try {
      const body = await response.json() as { message?: string; detail?: string }
      message = body.message ?? body.detail ?? message
    } catch {
      // Some successful or error responses have no JSON body.
    }
    throw new Error(message)
  }

  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export function send<T>(path: string, method: string, body?: unknown) {
  return request<T>(path, {
    method,
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  })
}
