import axios from 'axios'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api',
})

export interface Driver {
  id: number
  name: string
  email: string
  licenseCategories: string[]
  status: 'AVAILABLE' | 'ON_LEAVE' | 'IN_TRANSIT' | 'RETURNING'
}

export interface DriverRequest {
  name: string
  email: string
  licenseCategories: string[]
}

export interface Vehicle {
  id: number
  licensePlate: string
  vehicleType: 'VAN' | 'RIGID_TRUCK' | 'ARTICULATED_TRUCK'
  make: string
  model: string
  year: number
  maxWeightCapacityKg: number
  maxVolumeM3: number
  status: 'AVAILABLE' | 'IN_TRANSIT' | 'MAINTENANCE'
  currentMileageKm: number
  transmissionType: 'MANUAL' | 'AUTOMATIC' | 'AMT'
  maintenanceAutoTriggered: boolean
  maintenanceComponents: VehicleMaintenanceComponent[]
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
}

export interface VehicleRequest {
  licensePlate: string
  vehicleType: Vehicle['vehicleType']
  make: string
  model: string
  year: number
  maxWeightCapacityKg: number
  maxVolumeM3: number
  currentMileageKm: number
  transmissionType: Vehicle['transmissionType']
}

export interface WarehouseConfig {
  warehouseCity: string
}

export interface Goods {
  id: number
  trackingCode: string
  clientName: string
  goodsType: 'GENERAL' | 'REFRIGERATED' | 'HAZARDOUS'
  description: string | null
  weightKg: number
  volumeM3: number
  pickupCity: string
  deliveryCity: string
  status: 'UNASSIGNED' | 'ASSIGNED' | 'IN_TRANSIT' | 'DELIVERED' | 'ARCHIVED'
}

export interface GoodsRequest {
  clientName: string
  goodsType: Goods['goodsType']
  description: string | null
  weightKg: number
  volumeM3: number
  pickupCity: string
  deliveryCity: string
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

export interface RouteCreateRequest {
  driverId: number
  vehicleId: number
  departureTime: string
  stops: { goodsId: number; type: RouteStop['type'] }[]
}

export function getErrorMessage(error: unknown): string {
  if (axios.isAxiosError<{ message?: string }>(error)) {
    return error.response?.data?.message ?? error.message
  }
  return error instanceof Error ? error.message : 'An unexpected error occurred.'
}

export default api
