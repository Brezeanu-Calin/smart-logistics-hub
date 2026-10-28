<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import RouteMap from '../components/RouteMap.vue'
import PaginationControl from '../../shared/components/PaginationControl.vue'
import FinanceView from './FinanceView.vue'
import api, {
  getErrorMessage,
  isApiNotFoundError,
  type Driver,
  type DriverRequest,
  type Goods,
  type GoodsRequest,
  type Route,
  type RouteCreateRequest,
  type RouteSimulationPosition,
  type Trailer,
  type TrailerRequest,
  type Vehicle,
  type VehicleMaintenanceComponent,
  type VehicleRequest,
  type WarehouseConfig,
} from '../api'

type Section = 'overview' | 'routes' | 'freight' | 'drivers' | 'fleet' | 'maintenance' | 'finance'
type DemoContract = {
  reference: string
  client: string
  category: 'HEAVY HAUL' | 'GENERAL' | 'REFRIGERATED' | 'ADR'
  pickup: string
  delivery: string
  departure: string
  departureSort: number
  distanceKm: number
  revenueEur: number
}
type MaintenanceEntry = {
  performedAtLocal: string
  odometerKm: string
  costEur: string
}
const PAGE_SIZE = 10
const section = ref<Section>('overview')
const drivers = ref<Driver[]>([])
const vehicles = ref<Vehicle[]>([])
const goods = ref<Goods[]>([])
const routes = ref<Route[]>([])
const trailers = ref<Trailer[]>([])
const simulationPositions = reactive<Record<number, RouteSimulationPosition>>({})
const warehouseCity = ref('Cluj-Napoca, Romania')
const maintenanceEntries = reactive<Record<string, MaintenanceEntry>>({})
const busy = ref(false)
const error = ref('')
const success = ref('')
const snackbarMessage = computed(() => error.value || success.value)
const snackbarIsError = computed(() => Boolean(error.value))

const driverForm = reactive<DriverRequest>({ name: '', email: '', licenseCategories: ['B'] })
const editingDriverId = ref<number | null>(null)
const vehicleForm = reactive<VehicleRequest>({
  licensePlate: '',
  vehicleType: 'VAN',
  make: '',
  model: '',
  year: new Date().getFullYear(),
  maxWeightCapacityKg: 1000,
  maxVolumeM3: 10,
  currentMileageKm: 0,
  transmissionType: 'MANUAL',
})
const editingVehicleId = ref<number | null>(null)
const trailerForm = reactive<TrailerRequest>({
  licensePlate: '',
  trailerType: 'BOX_TRAILER',
  compatibleVehicleType: 'VAN',
  make: '',
  model: '',
  year: new Date().getFullYear(),
  maxWeightCapacityKg: 1000,
  maxVolumeM3: 10,
})
const editingTrailerId = ref<number | null>(null)
const goodsForm = reactive<GoodsRequest>({
  clientName: '',
  goodsType: 'GENERAL',
  description: null,
  weightKg: 1,
  revenueEur: 0,
  volumeM3: 1,
  pickupCity: '',
  deliveryCity: '',
})
const editingGoodsId = ref<number | null>(null)
const routeForm = reactive({
  driverId: 0,
  vehicleId: 0,
  trailerId: 0,
  departureLocal: '',
})
const selectedGoodsIds = ref<number[]>([])
const expandedRouteMaps = ref(new Set<number>())
const loadingRouteMaps = ref(new Set<number>())
const selectedVehicleId = ref<number | null>(null)
const contractCategory = ref('ALL')
const contractSort = ref<'RATE' | 'REVENUE' | 'DEPARTURE'>('RATE')
const fleetSort = ref<'CLASS' | 'MILEAGE' | 'STATUS'>('CLASS')
const driverPage = ref(1)
const vehiclePage = ref(1)
const freightPage = ref(1)
const routePage = ref(1)
let snackbarTimer: number | undefined
const demoContracts: DemoContract[] = [
  { reference: 'HM-2048', client: 'NorthSea Energy', category: 'HEAVY HAUL', pickup: 'Rotterdam', delivery: 'Munich', departure: 'Today · 18:00', departureSort: 1, distanceKm: 824, revenueEur: 7420 },
  { reference: 'HM-2052', client: 'Bavaria Components', category: 'GENERAL', pickup: 'Utrecht', delivery: 'Stuttgart', departure: 'Tomorrow · 06:30', departureSort: 2, distanceKm: 637, revenueEur: 2860 },
  { reference: 'HM-2057', client: 'Frostline Foods', category: 'REFRIGERATED', pickup: 'Eindhoven', delivery: 'Frankfurt', departure: 'Tomorrow · 09:00', departureSort: 3, distanceKm: 392, revenueEur: 2050 },
  { reference: 'HM-2061', client: 'RheinChem GmbH', category: 'ADR', pickup: 'Antwerp', delivery: 'Cologne', departure: '18 Oct · 13:00', departureSort: 4, distanceKm: 214, revenueEur: 1760 },
]
const activeRoutes = computed(() => routes.value.filter((route) => route.status === 'ACTIVE').length)
const liveRoutes = computed(() => routes.value.filter((route) => route.status === 'ACTIVE'))
const availableDrivers = computed(() => drivers.value.filter((driver) =>
  driver.status === 'AVAILABLE' && !isDriverScheduled(driver.id),
).length)
const availableVehicles = computed(() => vehicles.value.filter((vehicle) =>
  vehicle.status === 'AVAILABLE' && !vehicle.maintenanceAutoTriggered && !isVehicleScheduled(vehicle.id),
).length)
const maintenanceVehicles = computed(() => vehicles.value.filter((vehicle) => vehicle.status === 'MAINTENANCE'))
const selectedVehicle = computed(() =>
  vehicles.value.find((vehicle) => vehicle.id === selectedVehicleId.value) ?? vehicles.value[0] ?? null,
)
const sortedVehicles = computed(() => [...vehicles.value].sort((first, second) => {
  if (fleetSort.value === 'MILEAGE') return second.currentMileageKm - first.currentMileageKm
  if (fleetSort.value === 'STATUS') return first.status.localeCompare(second.status)
  return first.vehicleType.localeCompare(second.vehicleType) || first.licensePlate.localeCompare(second.licensePlate)
}))
const paginatedDrivers = computed(() => pageItems(drivers.value, driverPage.value))
const paginatedVehicles = computed(() => pageItems(sortedVehicles.value, vehiclePage.value))
const paginatedGoods = computed(() => pageItems(goods.value, freightPage.value))
const paginatedRoutes = computed(() => pageItems(routes.value, routePage.value))
const selectedGoods = computed(() =>
  selectedGoodsIds.value
    .map((id) => goods.value.find((item) => item.id === id))
    .filter((item): item is Goods => item !== undefined),
)
const selectedRouteVehicle = computed(() =>
  vehicles.value.find((vehicle) => vehicle.id === routeForm.vehicleId) ?? null,
)
const routeNeedsTrailer = computed(() => {
  const vehicle = selectedRouteVehicle.value
  if (!vehicle) return false
  if (vehicle.vehicleType === 'ARTICULATED_TRUCK') return true
  const cargo = selectedGoods.value
  if (!cargo.length) return false
  const totalWeight = cargo.reduce((total, item) => total + item.weightKg, 0)
  const totalVolume = cargo.reduce((total, item) => total + item.volumeM3, 0)
  return totalWeight > vehicle.maxWeightCapacityKg
    || totalVolume > vehicle.maxVolumeM3
    || cargo.some((item) => item.goodsType !== 'GENERAL')
})
const compatibleRouteTrailers = computed(() => {
  const vehicle = selectedRouteVehicle.value
  const cargo = selectedGoods.value
  if (!vehicle) return []
  const specializedCargo = cargo.some((item) => item.goodsType !== 'GENERAL')
  const totalWeight = cargo.reduce((total, item) => total + item.weightKg, 0)
  const totalVolume = cargo.reduce((total, item) => total + item.volumeM3, 0)
  const requiredWeight = specializedCargo ? totalWeight : Math.max(0, totalWeight - vehicle.maxWeightCapacityKg)
  const requiredVolume = specializedCargo ? totalVolume : Math.max(0, totalVolume - vehicle.maxVolumeM3)
  return trailers.value.filter((trailer) =>
    trailer.status === 'AVAILABLE'
    && trailer.compatibleVehicleType === vehicle.vehicleType
    && trailer.maxWeightCapacityKg >= requiredWeight
    && trailer.maxVolumeM3 >= requiredVolume
    && cargo.every((item) => trailerSupportsGoods(trailer.trailerType, item.goodsType))
    && !routes.value.some((route) =>
      route.trailerId === trailer.id && (route.status === 'PLANNED' || route.status === 'ACTIVE'),
    ),
  )
})
const visibleContracts = computed(() => {
  const filtered = demoContracts.filter((contract) =>
    contractCategory.value === 'ALL' || contract.category === contractCategory.value,
  )
  return filtered.sort((first, second) => {
    if (contractSort.value === 'REVENUE') return second.revenueEur - first.revenueEur
    if (contractSort.value === 'DEPARTURE') return first.departureSort - second.departureSort
    return (second.revenueEur / second.distanceKm) - (first.revenueEur / first.distanceKm)
  })
})
const unassignedGoods = computed(() => goods.value.filter((item) => item.status === 'UNASSIGNED'))
const screenTitle = computed(() => ({
  overview: 'Overview',
  routes: 'Route Planner & Dispatch',
  freight: 'Freight Jobs & Loads',
  drivers: 'Drivers',
  fleet: 'Fleet Operations & Telemetry',
  maintenance: 'Maintenance & Diagnostics',
  finance: 'Financial Analysis',
})[section.value])

function pageItems<T>(items: T[], page: number) {
  const start = (page - 1) * PAGE_SIZE
  return items.slice(start, start + PAGE_SIZE)
}

function pageCount(total: number) {
  return Math.max(1, Math.ceil(total / PAGE_SIZE))
}

function trailerSupportsGoods(
  trailerType: Trailer['trailerType'],
  goodsType: Goods['goodsType'],
) {
  const compatible: Record<Trailer['trailerType'], Goods['goodsType'][]> = {
    VAN_TRAILER: ['GENERAL'],
    RIGID_TRUCK_TRAILER: ['GENERAL'],
    ARTICULATED_TRUCK_TRAILER: ['GENERAL'],
    BOX_TRAILER: ['GENERAL'],
    CURTAIN_SIDE_TRAILER: ['GENERAL', 'OVERSIZED'],
    REFRIGERATED_TRAILER: ['GENERAL', 'REFRIGERATED'],
    HAZARDOUS_TRAILER: ['GENERAL', 'HAZARDOUS'],
    TANKER_TRAILER: ['LIQUID', 'HAZARDOUS'],
    FLATBED_TRAILER: ['GENERAL', 'OVERSIZED', 'VEHICLES'],
    LOW_LOADER_TRAILER: ['OVERSIZED', 'VEHICLES'],
    CONTAINER_CHASSIS_TRAILER: ['CONTAINERIZED'],
    BULK_TIPPER_TRAILER: ['BULK'],
    LIVESTOCK_TRAILER: ['LIVESTOCK'],
  }
  return compatible[trailerType].includes(goodsType)
}

function trailerTypeLabel(type: Trailer['trailerType']) {
  return type.replaceAll('_', ' ').replace(/\b\w/g, (letter) => letter.toUpperCase())
}

function healthClass(health: number) {
  return health > 65 ? 'healthy' : health > 35 ? 'caution' : 'critical'
}

function vehicleStatusLabel(vehicle: Vehicle) {
  return vehicle.status === 'MAINTENANCE' ? 'IN MAINTENANCE' : formatStatus(vehicle.status)
}

function maintenanceInterval(component: VehicleMaintenanceComponent) {
  const distance = component.serviceIntervalKm === null
    ? null
    : `${component.serviceIntervalKm.toLocaleString()} km`
  const time = component.serviceIntervalMonths === null
    ? null
    : `${component.serviceIntervalMonths} mo`
  return [distance, time].filter((value): value is string => value !== null).join(' / ')
    || component.inspectionNote
    || 'As needed'
}

function remainingInterval(component: VehicleMaintenanceComponent) {
  const distance = component.distanceRemainingKm === null
    ? null
    : `${component.distanceRemainingKm.toLocaleString()} km left`
  const days = component.daysRemaining === null ? null : `${component.daysRemaining} days left`
  return [distance, days].filter((value): value is string => value !== null).join(' · ')
    || component.inspectionNote
    || 'No fixed replacement interval'
}

function maintenanceSummary(vehicle: Vehicle) {
  return vehicle.maintenanceComponents
    .filter((component) => component.healthPercent !== null && component.healthPercent <= 10)
    .map((component) => component.label)
    .join(', ') || 'Vehicle manually placed in maintenance'
}

function maintenanceEntryKey(vehicle: Vehicle, component: VehicleMaintenanceComponent) {
  return `${vehicle.id}:${component.key}`
}

function defaultMaintenanceEntry(vehicle: Vehicle): MaintenanceEntry {
  return {
    performedAtLocal: defaultDepartureTime(),
    odometerKm: String(vehicle.currentMileageKm),
    costEur: '',
  }
}

function maintenanceEntry(vehicle: Vehicle, component: VehicleMaintenanceComponent) {
  return maintenanceEntries[maintenanceEntryKey(vehicle, component)] ?? defaultMaintenanceEntry(vehicle)
}

function syncMaintenanceEntries() {
  for (const vehicle of vehicles.value) {
    for (const component of vehicle.maintenanceComponents) {
      const key = maintenanceEntryKey(vehicle, component)
      if (!maintenanceEntries[key]) maintenanceEntries[key] = defaultMaintenanceEntry(vehicle)
    }
  }
}

function setMaintenanceDate(vehicle: Vehicle, component: VehicleMaintenanceComponent, performedAtLocal: string) {
  const key = maintenanceEntryKey(vehicle, component)
  maintenanceEntries[key] = { ...maintenanceEntry(vehicle, component), performedAtLocal }
}

function setMaintenanceOdometer(vehicle: Vehicle, component: VehicleMaintenanceComponent, odometerKm: string) {
  const key = maintenanceEntryKey(vehicle, component)
  maintenanceEntries[key] = { ...maintenanceEntry(vehicle, component), odometerKm }
}

function setMaintenanceCost(vehicle: Vehicle, component: VehicleMaintenanceComponent, costEur: string) {
  const key = maintenanceEntryKey(vehicle, component)
  maintenanceEntries[key] = { ...maintenanceEntry(vehicle, component), costEur }
}

function assignedDriver(vehicleId: number) {
  const route = routes.value.find((item) =>
    item.vehicleId === vehicleId && (
      item.status === 'PLANNED'
      || item.status === 'ACTIVE'
      || ((item.status === 'COMPLETED' || item.status === 'CANCELLED') && item.estimatedReturnTime !== null && !item.returnedAt)
    ),
  )
  if (!route) return '—'
  const driverState = route.status === 'ACTIVE' ? 'ON ROUTE' : route.status === 'PLANNED' ? 'PLANNED' : 'RETURNING'
  return `${route.driverName} · ${driverState}`
}

function clearMessages() {
  error.value = ''
  success.value = ''
}

watch([error, success], ([nextError, nextSuccess]) => {
  if (snackbarTimer !== undefined) window.clearTimeout(snackbarTimer)
  if (nextError || nextSuccess) {
    snackbarTimer = window.setTimeout(() => {
      error.value = ''
      success.value = ''
      snackbarTimer = undefined
    }, 5000)
  }
})

watch(() => routeForm.vehicleId, () => {
  routeForm.trailerId = 0
})
watch(routeNeedsTrailer, (required) => {
  if (!required) routeForm.trailerId = 0
})
watch(() => drivers.value.length, (total) => {
  driverPage.value = Math.min(driverPage.value, pageCount(total))
})
watch(() => vehicles.value.length, (total) => {
  vehiclePage.value = Math.min(vehiclePage.value, pageCount(total))
})
watch(() => goods.value.length, (total) => {
  freightPage.value = Math.min(freightPage.value, pageCount(total))
})
watch(() => routes.value.length, (total) => {
  routePage.value = Math.min(routePage.value, pageCount(total))
})

async function toggleRouteMap(route: Route) {
  const routeId = route.id
  if (expandedRouteMaps.value.has(routeId)) {
    const expanded = new Set(expandedRouteMaps.value)
    expanded.delete(routeId)
    expandedRouteMaps.value = expanded
    return
  }

  const loading = new Set(loadingRouteMaps.value)
  loading.add(routeId)
  loadingRouteMaps.value = loading
  clearMessages()
  try {
    const response = await api.post<Route>(`/routes/${routeId}/directions`)
    const routeIndex = routes.value.findIndex((item) => item.id === routeId)
    if (routeIndex >= 0) routes.value[routeIndex] = response.data
    const expanded = new Set(expandedRouteMaps.value)
    expanded.add(routeId)
    expandedRouteMaps.value = expanded
  } catch (requestError) {
    error.value = isApiNotFoundError(requestError)
      ? 'The running backend does not have the route-directions endpoint. Restart the backend and verify the frontend API URL points to it.'
      : getErrorMessage(requestError)
  } finally {
    const pending = new Set(loadingRouteMaps.value)
    pending.delete(routeId)
    loadingRouteMaps.value = pending
  }
}

async function loadData() {
  const [driverResponse, vehicleResponse, goodsResponse, routeResponse, trailerResponse, configResponse] = await Promise.all([
    api.get<Driver[]>('/drivers'),
    api.get<Vehicle[]>('/vehicles'),
    api.get<Goods[]>('/goods'),
    api.get<Route[]>('/routes'),
    api.get<Trailer[]>('/trailers'),
    api.get<WarehouseConfig>('/config'),
  ])
  warehouseCity.value = configResponse.data.warehouseCity
  if (!goodsForm.pickupCity) goodsForm.pickupCity = warehouseCity.value
  drivers.value = driverResponse.data
  vehicles.value = vehicleResponse.data
  syncMaintenanceEntries()
  if (!vehicles.value.some((vehicle) => vehicle.id === selectedVehicleId.value)) {
    selectedVehicleId.value = vehicles.value[0]?.id ?? null
  }
  goods.value = goodsResponse.data
  routes.value = routeResponse.data
  trailers.value = trailerResponse.data
}

async function refresh() {
  try {
    await loadData()
    await refreshSimulationPositions()
  } catch (requestError) {
    error.value = getErrorMessage(requestError)
  }
}

async function refreshSimulationPositions() {
  if (simulationRefreshInFlight) return
  if (activeRoutes.value === 0) {
    Object.keys(simulationPositions).forEach((routeId) => delete simulationPositions[Number(routeId)])
    return
  }
  simulationRefreshInFlight = true
  try {
    const response = await api.get<RouteSimulationPosition[]>('/routes/simulations')
    const activeRouteIds = new Set(response.data.map((position) => position.routeId))
    Object.keys(simulationPositions).forEach((routeId) => {
      if (!activeRouteIds.has(Number(routeId))) delete simulationPositions[Number(routeId)]
    })
    response.data.forEach((position) => {
      simulationPositions[position.routeId] = position
    })
  } catch (requestError) {
    Object.keys(simulationPositions).forEach((routeId) => delete simulationPositions[Number(routeId)])
    error.value = getErrorMessage(requestError)
  } finally {
    simulationRefreshInFlight = false
  }
}

let refreshTimer: number
let simulationRefreshTimer: number
let simulationRefreshInFlight = false
onMounted(() => {
  void refresh()
  refreshTimer = window.setInterval(() => void refresh(), 30_000)
  simulationRefreshTimer = window.setInterval(() => void refreshSimulationPositions(), 30_000)
})
onUnmounted(() => {
  window.clearInterval(refreshTimer)
  window.clearInterval(simulationRefreshTimer)
  if (snackbarTimer !== undefined) window.clearTimeout(snackbarTimer)
})

function resetDriverForm() {
  editingDriverId.value = null
  Object.assign(driverForm, { name: '', email: '', licenseCategories: ['B'] })
}

function editDriver(driver: Driver) {
  editingDriverId.value = driver.id
  Object.assign(driverForm, {
    name: driver.name,
    email: driver.email,
    licenseCategories: [...driver.licenseCategories],
  })
  section.value = 'drivers'
}

async function saveDriver() {
  clearMessages()
  busy.value = true
  try {
    if (editingDriverId.value === null) {
      await api.post('/drivers', driverForm)
    } else {
      await api.put(`/drivers/${editingDriverId.value}`, driverForm)
    }
    success.value = editingDriverId.value === null ? 'Driver added.' : 'Driver updated.'
    resetDriverForm()
    await loadData()
  } catch (requestError) {
    error.value = getErrorMessage(requestError)
  } finally {
    busy.value = false
  }
}

async function deleteDriver(driver: Driver) {
  if (!window.confirm(`Delete driver ${driver.name}?`)) return
  await mutate(() => api.delete(`/drivers/${driver.id}`), 'Driver deleted.')
}

async function updateDriverStatus(driver: Driver, status: 'AVAILABLE' | 'ON_LEAVE') {
  await mutate(() => api.patch(`/drivers/${driver.id}/status`, { status }), 'Driver status updated.')
}

function resetVehicleForm() {
  editingVehicleId.value = null
  Object.assign(vehicleForm, {
    licensePlate: '',
    vehicleType: 'VAN',
    make: '',
    model: '',
    year: new Date().getFullYear(),
    maxWeightCapacityKg: 1000,
    maxVolumeM3: 10,
    currentMileageKm: 0,
    transmissionType: 'MANUAL',
  })
}

function editVehicle(vehicle: Vehicle) {
  editingVehicleId.value = vehicle.id
  selectedVehicleId.value = vehicle.id
  Object.assign(vehicleForm, {
    licensePlate: vehicle.licensePlate,
    vehicleType: vehicle.vehicleType,
    make: vehicle.make,
    model: vehicle.model,
    year: vehicle.year,
    maxWeightCapacityKg: vehicle.maxWeightCapacityKg,
    maxVolumeM3: vehicle.maxVolumeM3,
    currentMileageKm: vehicle.currentMileageKm,
    transmissionType: vehicle.transmissionType,
  })
  section.value = 'fleet'
}

function resetTrailerForm() {
  editingTrailerId.value = null
  Object.assign(trailerForm, {
    licensePlate: '',
    trailerType: 'BOX_TRAILER',
    compatibleVehicleType: 'VAN',
    make: '',
    model: '',
    year: new Date().getFullYear(),
    maxWeightCapacityKg: 1000,
    maxVolumeM3: 10,
  })
}

function editTrailer(trailer: Trailer) {
  editingTrailerId.value = trailer.id
  Object.assign(trailerForm, {
    licensePlate: trailer.licensePlate,
    trailerType: trailer.trailerType,
    compatibleVehicleType: trailer.compatibleVehicleType,
    make: trailer.make,
    model: trailer.model,
    year: trailer.year,
    maxWeightCapacityKg: trailer.maxWeightCapacityKg,
    maxVolumeM3: trailer.maxVolumeM3,
  })
  section.value = 'fleet'
}

async function saveTrailer() {
  clearMessages()
  busy.value = true
  try {
    if (editingTrailerId.value === null) {
      await api.post('/trailers', trailerForm)
    } else {
      await api.put(`/trailers/${editingTrailerId.value}`, trailerForm)
    }
    success.value = editingTrailerId.value === null ? 'Trailer added.' : 'Trailer updated.'
    resetTrailerForm()
    await loadData()
  } catch (requestError) {
    error.value = getErrorMessage(requestError)
  } finally {
    busy.value = false
  }
}

async function deleteTrailer(trailer: Trailer) {
  if (!window.confirm(`Delete trailer ${trailer.licensePlate}?`)) return
  await mutate(() => api.delete(`/trailers/${trailer.id}`), 'Trailer deleted.')
}

async function updateTrailerStatus(trailer: Trailer, status: 'AVAILABLE' | 'MAINTENANCE') {
  await mutate(() => api.patch(`/trailers/${trailer.id}/status`, { status }), 'Trailer status updated.')
}

function updateVehicleTypeDefaults() {
  vehicleForm.transmissionType = vehicleForm.vehicleType === 'VAN' ? 'MANUAL' : 'AMT'
}

async function saveVehicle() {
  clearMessages()
  busy.value = true
  try {
    if (editingVehicleId.value === null) {
      await api.post('/vehicles', vehicleForm)
    } else {
      await api.put(`/vehicles/${editingVehicleId.value}`, vehicleForm)
    }
    success.value = editingVehicleId.value === null ? 'Vehicle added.' : 'Vehicle updated.'
    resetVehicleForm()
    await loadData()
  } catch (requestError) {
    error.value = getErrorMessage(requestError)
  } finally {
    busy.value = false
  }
}

async function deleteVehicle(vehicle: Vehicle) {
  if (!window.confirm(`Delete vehicle ${vehicle.licensePlate}?`)) return
  await mutate(() => api.delete(`/vehicles/${vehicle.id}`), 'Vehicle deleted.')
}

async function updateVehicleStatus(vehicle: Vehicle, status: 'AVAILABLE' | 'MAINTENANCE') {
  await mutate(() => api.patch(`/vehicles/${vehicle.id}/status`, { status }), 'Vehicle status updated.')
}

async function recordMaintenance(vehicle: Vehicle, component: VehicleMaintenanceComponent) {
  clearMessages()
  const entry = maintenanceEntry(vehicle, component)
  const performedAt = new Date(entry.performedAtLocal)
  const odometerKm = Number(entry.odometerKm)
  const costEur = Number(entry.costEur)
  if (!entry.performedAtLocal || Number.isNaN(performedAt.getTime())
    || !Number.isInteger(odometerKm) || odometerKm < 0
    || !entry.costEur || !Number.isFinite(costEur) || costEur < 0
    || !/^\d+(\.\d{1,2})?$/.test(entry.costEur)) {
    error.value = 'Enter a valid service date, odometer reading, and non-negative EUR cost (up to two decimals).'
    return
  }
  if (odometerKm > vehicle.currentMileageKm) {
    error.value = 'The service odometer cannot exceed the vehicle current mileage.'
    return
  }
  if (!window.confirm(
    `Confirm ${component.label} on ${vehicle.licensePlate} was completed ${performedAt.toLocaleString()} at ${odometerKm.toLocaleString()} km for €${costEur.toFixed(2)}?`,
  )) return
  await mutate(
    () => api.post(`/vehicles/${vehicle.id}/maintenance`, {
      componentKey: component.key,
      performedAt: performedAt.toISOString(),
      odometerKm,
      costEur,
    }),
    `${component.label} service history recorded.`,
  )
  if (!error.value) maintenanceEntries[maintenanceEntryKey(vehicle, component)] = defaultMaintenanceEntry(vehicle)
}

function resetGoodsForm() {
  editingGoodsId.value = null
  Object.assign(goodsForm, {
    clientName: '',
    goodsType: 'GENERAL',
    description: null,
    weightKg: 1,
    revenueEur: 0,
    volumeM3: 1,
    pickupCity: warehouseCity.value,
    deliveryCity: '',
  })
}

function editGoods(item: Goods) {
  editingGoodsId.value = item.id
  Object.assign(goodsForm, {
    clientName: item.clientName,
    goodsType: item.goodsType,
    description: item.description,
    weightKg: item.weightKg,
    revenueEur: item.revenueEur ?? 0,
    volumeM3: item.volumeM3,
    pickupCity: item.pickupCity,
    deliveryCity: item.deliveryCity,
  })
  section.value = 'freight'
}

async function saveGoods() {
  clearMessages()
  busy.value = true
  try {
    if (editingGoodsId.value === null) {
      await api.post('/goods', goodsForm)
    } else {
      await api.put(`/goods/${editingGoodsId.value}`, goodsForm)
    }
    success.value = editingGoodsId.value === null ? 'Freight added.' : 'Freight updated.'
    resetGoodsForm()
    await loadData()
  } catch (requestError) {
    error.value = getErrorMessage(requestError)
  } finally {
    busy.value = false
  }
}

async function deleteGoods(item: Goods) {
  if (!window.confirm(`Delete freight ${item.trackingCode}?`)) return
  await mutate(() => api.delete(`/goods/${item.id}`), 'Freight deleted.')
}

function defaultDepartureTime() {
  const now = new Date()
  now.setMinutes(now.getMinutes() - now.getTimezoneOffset())
  return now.toISOString().slice(0, 16)
}

async function createRoute() {
  clearMessages()
  if (!selectedGoodsIds.value.length) {
    error.value = 'Select at least one unassigned freight item.'
    return
  }
  if (routeNeedsTrailer.value && !routeForm.trailerId) {
    error.value = compatibleRouteTrailers.value.length
      ? 'Select a compatible trailer for this vehicle and freight.'
      : 'No compatible trailer is available. Add one in Fleet Ops before planning this route.'
    return
  }
  if (selectedGoodsIds.value.length > 12) {
    error.value = 'A route can contain at most 12 freight items (24 pickup/delivery stops).'
    return
  }
  busy.value = true
  try {
    const request: RouteCreateRequest = {
      driverId: Number(routeForm.driverId),
      vehicleId: Number(routeForm.vehicleId),
      departureTime: new Date(routeForm.departureLocal).toISOString(),
      trailerId: routeNeedsTrailer.value ? Number(routeForm.trailerId) : null,
      stops: [
        ...selectedGoodsIds.value.map((goodsId) => ({ goodsId, type: 'PICKUP' as const })),
        ...selectedGoodsIds.value.map((goodsId) => ({ goodsId, type: 'DELIVERY' as const })),
      ],
    }
    await api.post('/routes', request)
    success.value = 'Route planned. Distance and driving time were calculated.'
    selectedGoodsIds.value = []
    routeForm.departureLocal = defaultDepartureTime()
    await loadData()
  } catch (requestError) {
    error.value = getErrorMessage(requestError)
  } finally {
    busy.value = false
  }
}

function moveSelectedGoods(index: number, offset: number) {
  const nextIndex = index + offset
  if (nextIndex < 0 || nextIndex >= selectedGoodsIds.value.length) return
  const reordered = [...selectedGoodsIds.value]
  const [item] = reordered.splice(index, 1)
  if (item === undefined) return
  reordered.splice(nextIndex, 0, item)
  selectedGoodsIds.value = reordered
}

async function setRouteStatus(route: Route, status: 'ACTIVE' | 'CANCELLED') {
  await mutate(
    () => api.patch(`/routes/${route.id}/status`, { status }),
    status === 'ACTIVE' ? 'Route started.' : 'Route cancelled.',
  )
}

async function deliverGoods(route: Route, goodsId: number) {
  await mutate(
    () => api.patch(`/routes/${route.id}/goods/${goodsId}/deliver`),
    'Delivery confirmed.',
  )
}

async function deleteRoute(route: Route) {
  if (!window.confirm(`Delete route #${route.id}?`)) return
  await mutate(() => api.delete(`/routes/${route.id}`), 'Route deleted.')
}

async function mutate(action: () => Promise<unknown>, message: string) {
  clearMessages()
  busy.value = true
  try {
    await action()
    success.value = message
    await loadData()
  } catch (requestError) {
    error.value = getErrorMessage(requestError)
  } finally {
    busy.value = false
  }
}

function goodsStatus(goodsId: number) {
  return goods.value.find((item) => item.id === goodsId)?.status
}

function canConfirmDelivery(route: Route, goodsId: number) {
  const deliveryStop = route.stops.find((stop) => stop.goodsId === goodsId && stop.type === 'DELIVERY')
  if (!deliveryStop) return false
  return route.stops
    .filter((stop) => stop.type === 'DELIVERY' && stop.stopOrder < deliveryStop.stopOrder)
    .every((stop) => ['DELIVERED', 'ARCHIVED'].includes(goodsStatus(stop.goodsId) ?? ''))
}

function hasDriverRoute(driverId: number) {
  return routes.value.some((route) => route.driverId === driverId)
}

function hasVehicleRoute(vehicleId: number) {
  return routes.value.some((route) => route.vehicleId === vehicleId)
}

function isGoodsOnRoute(goodsId: number) {
  return routes.value.some((route) => route.stops.some((stop) => stop.goodsId === goodsId))
}

function isDriverScheduled(driverId: number) {
  return routes.value.some((route) =>
    route.driverId === driverId && (route.status === 'PLANNED' || route.status === 'ACTIVE'),
  )
}

function isVehicleScheduled(vehicleId: number) {
  return routes.value.some((route) =>
    route.vehicleId === vehicleId && (route.status === 'PLANNED' || route.status === 'ACTIVE'),
  )
}

function displayDate(value: string | null) {
  return value ? new Date(value).toLocaleString() : '—'
}

function displayDuration(seconds: number) {
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.round((seconds % 3600) / 60)
  return hours ? `${hours}h ${minutes}m` : `${minutes}m`
}

function formatStatus(status: string) {
  return status.replaceAll('_', ' ').toLowerCase()
}

routeForm.departureLocal = defaultDepartureTime()
</script>

<template>
  <main class="app-shell">
    <aside class="sidebar">
      <a class="brand" href="#" @click.prevent="section = 'overview'">
        <span class="brand-mark"><svg viewBox="0 0 48 32" aria-hidden="true"><path d="M2 5h27v19H2zM29 11h9l8 8v5H29zM33 13v6h9" /><circle cx="11" cy="26" r="3" /><circle cx="37" cy="26" r="3" /><path d="M7 11h7m-7 5h12" /></svg></span>
        <span><strong>HAULMASTER</strong><small>Dispatch console</small></span>
      </a>
      <nav class="side-nav" aria-label="Main navigation">
        <button :class="{ selected: section === 'overview' }" @click="section = 'overview'"><span>▦</span> Overview</button>
        <button :class="{ selected: section === 'routes' }" @click="section = 'routes'"><span>↗</span> Route Planner <b>{{ activeRoutes }}</b></button>
        <button :class="{ selected: section === 'fleet' }" @click="section = 'fleet'"><span>▰</span> Fleet Ops</button>
        <button :class="{ selected: section === 'freight' }" @click="section = 'freight'"><span>▣</span> Freight Jobs</button>
        <button :class="{ selected: section === 'maintenance' }" @click="section = 'maintenance'"><span>⚒</span> Maintenance <b>{{ maintenanceVehicles.length }}</b></button>
        <button :class="{ selected: section === 'drivers' }" @click="section = 'drivers'"><span>♙</span> Drivers</button>
        <button :class="{ selected: section === 'finance' }" @click="section = 'finance'"><span>€</span> Finance</button>
      </nav>
      <div class="sidebar-note">
        <span class="note-icon">i</span>
        <div><strong>WAREHOUSE · {{ warehouseCity }}</strong><p>Vehicle maintenance status and service intervals come from backend records.</p></div>
      </div>
      <div class="sidebar-footer">HAULMASTER DISPATCH <span>v1.0</span></div>
    </aside>

    <section class="main-panel">
      <header class="topbar">
        <div class="crumb">HAULMASTER <span>/</span> {{ screenTitle }}</div>
        <div class="topbar-right"><span class="live-dot"></span> WAREHOUSE · {{ warehouseCity }} <button class="refresh-button" :disabled="busy" @click="refresh" aria-label="Refresh data">↻</button></div>
      </header>

      <div class="content">
        <Transition name="snackbar">
          <div
            v-if="snackbarMessage"
            class="app-snackbar"
            :class="snackbarIsError ? 'app-snackbar-error' : 'app-snackbar-success'"
            :role="snackbarIsError ? 'alert' : 'status'"
          >
            <span>{{ snackbarIsError ? '!' : '✓' }}</span>
            <p>{{ snackbarMessage }}</p>
            <button aria-label="Dismiss notification" @click="clearMessages">×</button>
          </div>
        </Transition>

        <template v-if="section === 'overview'">
          <div class="page-heading">
            <div><p class="eyebrow">OPERATIONS SNAPSHOT</p><h1>Good day, dispatcher</h1><p class="subtitle">Here’s what’s happening across your fleet today.</p></div>
            <button class="primary-button" @click="section = 'routes'">＋ Plan a route</button>
          </div>
          <div class="stat-grid">
            <article class="stat-card"><div class="stat-top"><span>Active routes</span><span class="stat-icon purple">↗</span></div><strong>{{ activeRoutes }}</strong><small>Currently on the road</small></article>
            <article class="stat-card"><div class="stat-top"><span>Available drivers</span><span class="stat-icon green">♙</span></div><strong>{{ availableDrivers }}</strong><small>Ready for assignment</small></article>
            <article class="stat-card"><div class="stat-top"><span>Available vehicles</span><span class="stat-icon blue">▰</span></div><strong>{{ availableVehicles }}</strong><small>Ready for dispatch</small></article>
            <article class="stat-card"><div class="stat-top"><span>Unassigned freight</span><span class="stat-icon orange">▣</span></div><strong>{{ unassignedGoods.length }}</strong><small>Awaiting a route</small></article>
          </div>
          <section class="panel">
            <div class="panel-heading"><div><h2>Recent routes</h2><p>Latest route plans and delivery progress</p></div><button class="text-button" @click="section = 'routes'">View all routes →</button></div>
            <div v-if="!routes.length" class="empty-state"><span>↗</span><strong>No routes yet</strong><p>Create a route to see its distance, driving estimate, and delivery progress here.</p><button class="secondary-button" @click="section = 'routes'">Plan your first route</button></div>
            <div v-else class="table-wrap"><table><thead><tr><th>ROUTE</th><th>DRIVER / VEHICLE</th><th>DEPARTURE</th><th>EST. RETURN</th><th>STATUS</th><th></th></tr></thead><tbody><tr v-for="route in routes.slice(0, 5)" :key="route.id"><td><strong>#{{ route.id }}</strong><small>{{ (route.distanceMeters / 1000).toFixed(1) }} km · {{ displayDuration(route.drivingDurationSeconds) }}</small></td><td>{{ route.driverName }}<small>{{ route.vehicleLicensePlate }}</small></td><td>{{ displayDate(route.departureTime) }}</td><td>{{ displayDate(route.estimatedReturnTime) }}</td><td><span class="status-pill" :class="route.status.toLowerCase()">{{ formatStatus(route.status) }}</span></td><td><button class="text-button" @click="section = 'routes'">Details</button></td></tr></tbody></table></div>
          </section>
          <div class="quick-grid">
            <button class="quick-card" @click="section = 'freight'"><span class="quick-icon orange">＋</span><span><strong>Add freight</strong><small>Register an incoming shipment</small></span><b>→</b></button>
            <button class="quick-card" @click="section = 'drivers'"><span class="quick-icon green">＋</span><span><strong>Add a driver</strong><small>Update your available workforce</small></span><b>→</b></button>
            <button class="quick-card" @click="section = 'fleet'"><span class="quick-icon blue">＋</span><span><strong>Add a vehicle</strong><small>Register fleet capacity</small></span><b>→</b></button>
          </div>
        </template>

        <template v-else-if="section === 'routes'">
          <div class="page-heading"><div><p class="eyebrow">ROUTE PLANNER / DISPATCH</p><h1>Route Planner &amp; Dispatch</h1><p class="subtitle">Plan multi-stop freight, review saved road routes, and follow simulated driver and vehicle positions.</p></div><span class="demo-flag">SIMULATED · NOT GPS</span></div>
          <section class="panel">
            <div class="panel-heading"><div><h2>Live navigation</h2><p>Active vehicles move along their saved OSRM road geometry at the estimated route duration. Position is illustrative, not live GPS.</p></div><span class="status-pill active">{{ liveRoutes.length }} ACTIVE</span></div>
            <div v-if="!liveRoutes.length" class="empty-state"><span>↗</span><strong>No active routes</strong><p>Start a planned route to see its actual road geometry and simulated vehicle position here.</p></div>
            <div v-else class="route-list">
              <article v-for="route in liveRoutes" :key="route.id" class="route-card">
                <div class="route-card-head"><div><span class="route-number">ROUTE #{{ route.id }}</span><span class="status-pill active">EN ROUTE</span></div><span class="route-number">{{ route.vehicleLicensePlate }} · {{ route.driverName }}</span></div>
                <RouteMap
                  :route-id="route.id"
                  :geometry="route.geometry"
                  :stops="route.stops"
                  :status="route.status"
                  :simulation-position="simulationPositions[route.id] ?? null"
                  :driver-name="route.driverName"
                  :vehicle-license-plate="route.vehicleLicensePlate"
                />
              </article>
            </div>
          </section>
          <section class="panel form-panel">
            <div class="panel-heading"><div><h2>Plan a route</h2><p>Cities are geocoded with Nominatim and routed by OSRM.</p></div><span class="form-step">NEW ROUTE</span></div>
            <form class="form-grid route-form" @submit.prevent="createRoute">
              <label>Driver<select v-model.number="routeForm.driverId" required><option :value="0" disabled>Select an available driver</option><option v-for="driver in drivers.filter((item) => item.status === 'AVAILABLE' && !isDriverScheduled(item.id))" :key="driver.id" :value="driver.id">{{ driver.name }} · {{ driver.licenseCategories.join(', ') }}</option></select></label>
              <label>Vehicle<select v-model.number="routeForm.vehicleId" required><option :value="0" disabled>Select an available vehicle</option><option v-for="vehicle in vehicles.filter((item) => item.status === 'AVAILABLE' && !item.maintenanceAutoTriggered && !isVehicleScheduled(item.id))" :key="vehicle.id" :value="vehicle.id">{{ vehicle.licensePlate }} · {{ vehicle.vehicleType.replaceAll('_', ' ') }}</option></select></label>
              <label v-if="routeNeedsTrailer" class="trailer-select-label">Trailer {{ selectedRouteVehicle?.vehicleType === 'ARTICULATED_TRUCK' ? '(required)' : '(required for this load)' }}<select v-model.number="routeForm.trailerId" required><option :value="0" disabled>Select a compatible available trailer</option><option v-for="trailer in compatibleRouteTrailers" :key="trailer.id" :value="trailer.id">{{ trailer.licensePlate }} · {{ trailerTypeLabel(trailer.trailerType) }} · {{ trailer.maxWeightCapacityKg.toLocaleString() }} kg / {{ trailer.maxVolumeM3 }} m³</option></select><small v-if="!compatibleRouteTrailers.length">No matching trailer available. <button type="button" class="text-button" @click="section = 'fleet'">Add a trailer in Fleet Ops</button></small></label>
              <label>Departure time<input v-model="routeForm.departureLocal" type="datetime-local" required></label>
              <fieldset class="goods-picker"><legend>Unassigned freight <span>{{ selectedGoodsIds.length }} selected</span></legend><div v-if="!unassignedGoods.length" class="small-empty">No unassigned freight is available.</div><label v-for="item in unassignedGoods" :key="item.id" class="check-row"><input v-model="selectedGoodsIds" type="checkbox" :value="item.id"><span><strong>{{ item.clientName }}</strong><small>{{ item.trackingCode }} · {{ item.pickupCity }} → {{ item.deliveryCity }}</small></span><b>{{ item.weightKg }} kg</b></label></fieldset>
              <div v-if="selectedGoods.length" class="selected-order"><strong>Pickup and delivery order</strong><p>All pickups are planned first; delivery order follows this list. Use the arrows to reorder.</p><ol><li v-for="(item, index) in selectedGoods" :key="item.id"><span class="order-number">{{ index + 1 }}</span><span class="order-name">{{ item.clientName }} <small>{{ item.trackingCode }}</small></span><button type="button" :disabled="index === 0" :aria-label="`Move ${item.clientName} up`" @click="moveSelectedGoods(index, -1)">↑</button><button type="button" :disabled="index === selectedGoods.length - 1" :aria-label="`Move ${item.clientName} down`" @click="moveSelectedGoods(index, 1)">↓</button></li></ol></div>
              <div class="form-actions"><button class="primary-button" :disabled="busy || !routeForm.driverId || !routeForm.vehicleId || selectedGoodsIds.length > 12 || (routeNeedsTrailer && (!routeForm.trailerId || !compatibleRouteTrailers.some((item) => item.id === routeForm.trailerId)))">Calculate &amp; plan route</button><span>City-level estimates use geocoded city locations, not exact pickup or delivery points. Freight type, combined weight/volume, vehicle, and trailer compatibility are checked before planning.</span></div>
            </form>
          </section>
          <section class="panel">
            <div class="panel-heading"><div><h2>All routes</h2><p>{{ routes.length }} route{{ routes.length === 1 ? '' : 's' }} in the system</p></div></div>
            <div v-if="!routes.length" class="empty-state"><span>↗</span><strong>No routes planned</strong><p>Assign an available driver, vehicle, and freight to create your first route.</p></div>
            <div v-else class="route-list">
              <article v-for="route in paginatedRoutes" :key="route.id" class="route-card">
                <div class="route-card-head"><div><span class="route-number">ROUTE #{{ route.id }}</span><span class="status-pill" :class="route.status.toLowerCase()">{{ formatStatus(route.status) }}</span></div><div class="route-actions"><button v-if="route.status !== 'ACTIVE'" class="text-button" :disabled="loadingRouteMaps.has(route.id)" @click="toggleRouteMap(route)">{{ loadingRouteMaps.has(route.id) ? 'Calculating route…' : expandedRouteMaps.has(route.id) ? 'Hide map' : 'Show map' }}</button><button v-if="route.status === 'PLANNED'" class="small-button" :disabled="busy" @click="setRouteStatus(route, 'ACTIVE')">Start route</button><button v-if="route.status === 'PLANNED' || (route.status === 'CANCELLED' && (!route.estimatedReturnTime || route.returnedAt))" class="icon-button danger" :disabled="busy" aria-label="Delete route" @click="deleteRoute(route)">⌫</button></div></div>
                <div class="route-summary"><div><small>DRIVER</small><strong>{{ route.driverName }}</strong></div><div><small>VEHICLE</small><strong>{{ route.vehicleLicensePlate }}<template v-if="route.trailerLicensePlate"> + {{ route.trailerLicensePlate }}</template></strong></div><div><small>DEPARTURE</small><strong>{{ displayDate(route.departureTime) }}</strong></div><div><small>EST. AT DEPOT</small><strong>{{ displayDate(route.estimatedReturnTime) }}</strong></div></div>
                <div class="route-metrics"><span>↔ {{ (route.distanceMeters / 1000).toFixed(1) }} km total distance</span><span>◷ {{ displayDuration(route.drivingDurationSeconds) }} driving</span><span v-if="route.returnedAt">✓ Returned {{ displayDate(route.returnedAt) }}</span></div>
                <RouteMap
                  v-if="expandedRouteMaps.has(route.id) && route.status !== 'ACTIVE'"
                  :route-id="route.id"
                  :geometry="route.geometry"
                  :stops="route.stops"
                  :status="route.status"
                  :simulation-position="null"
                  :driver-name="route.driverName"
                  :vehicle-license-plate="route.vehicleLicensePlate"
                />
                <ol class="stop-list"><li v-for="stop in route.stops" :key="`${stop.goodsId}-${stop.type}`"><span class="stop-marker" :class="stop.type.toLowerCase()"></span><span><small>{{ stop.type }} · {{ stop.trackingCode }}</small><strong>{{ stop.city }}</strong></span><button v-if="route.status === 'ACTIVE' && stop.type === 'DELIVERY' && goodsStatus(stop.goodsId) === 'IN_TRANSIT' && canConfirmDelivery(route, stop.goodsId)" class="text-button" :disabled="busy" @click="deliverGoods(route, stop.goodsId)">Confirm delivery</button><span v-else-if="stop.type === 'DELIVERY' && ['DELIVERED', 'ARCHIVED'].includes(goodsStatus(stop.goodsId) ?? '')" class="delivered-label">Delivered</span><span v-else-if="route.status === 'ACTIVE' && stop.type === 'DELIVERY' && goodsStatus(stop.goodsId) === 'IN_TRANSIT'" class="delivered-label">Next stop pending</span></li></ol>
                <div v-if="route.status === 'PLANNED'" class="route-footer"><button class="text-button danger-text" :disabled="busy" @click="setRouteStatus(route, 'CANCELLED')">Cancel route</button></div>
              </article>
            </div>
            <PaginationControl v-if="routes.length > PAGE_SIZE" v-model:current-page="routePage" :page-count="pageCount(routes.length)" :total="routes.length" :page-size="PAGE_SIZE" />
          </section>
        </template>

        <template v-else-if="section === 'freight'">
          <div class="page-heading"><div><p class="eyebrow">FREIGHT JOBS / LOADS</p><h1>Freight Jobs &amp; Loads</h1><p class="subtitle">Warehouse: {{ warehouseCity }} · Manage live freight records and route assignments.</p></div><span class="demo-flag">CONTRACT BOARD · DEMO DATA</span></div>
          <section class="panel">
            <div class="panel-heading"><div><h2>Freight market board</h2><p>Sample opportunities · not bookable · values are illustrative</p></div><div class="market-controls"><select v-model="contractCategory" aria-label="Filter contracts by category"><option value="ALL">All cargo</option><option value="HEAVY HAUL">Heavy haul</option><option value="GENERAL">General</option><option value="REFRIGERATED">Refrigerated</option><option value="ADR">ADR</option></select><select v-model="contractSort" aria-label="Sort contracts"><option value="RATE">Sort: €/km</option><option value="REVENUE">Sort: revenue</option><option value="DEPARTURE">Sort: departure</option></select></div></div>
            <div class="table-wrap"><table><thead><tr><th>CONTRACT</th><th>CARGO</th><th>LANE</th><th>DEPARTURE</th><th>DISTANCE</th><th>REVENUE</th><th>RATE</th></tr></thead><tbody><tr v-for="contract in visibleContracts" :key="contract.reference"><td><strong>{{ contract.reference }}</strong><small>{{ contract.client }}</small></td><td><span class="status-pill warning">{{ contract.category }}</span></td><td>{{ contract.pickup }} → {{ contract.delivery }}</td><td>{{ contract.departure }}</td><td>{{ contract.distanceKm.toLocaleString() }} KM</td><td>€ {{ contract.revenueEur.toLocaleString() }}</td><td class="rate-cell">€ {{ (contract.revenueEur / contract.distanceKm).toFixed(2) }}</td></tr></tbody></table></div>
          </section>
          <section class="panel envelope-panel">
            <div class="panel-heading"><div><h2>Load envelope inspector</h2><p>Sample cargo profile · compatibility and route checks are illustrative only</p></div><span class="demo-flag">DEMO LOAD</span></div>
            <div class="envelope-grid"><div class="cargo-envelope"><div class="cargo-outline"><span>OVERSIZE LOAD</span><strong>TRANSFORMER</strong><small>54,000 KG · 8.4 × 3.6 × 4.1 M</small></div><div class="envelope-dimensions"><span>↔ 8.4 M</span><span>↕ 4.1 M</span></div></div><div class="envelope-data"><div><small>ELIGIBLE TRACTOR</small><strong>Scania R730 8×4 <span>vs.</span> Actros SLT</strong></div><div><small>ESTIMATED NET MARGIN</small><strong class="good-text">€ 1,284 · 17.3%</strong></div><div><small>COMPLIANCE CHECKLIST</small><strong>✓ Axle load · ✓ Route clearance · ! Permit review <em>DEMO</em></strong></div></div></div>
          </section>
          <section class="panel form-panel">
            <div class="panel-heading"><div><h2>{{ editingGoodsId === null ? 'Add freight' : 'Edit freight' }}</h2><p>Tracking codes are generated automatically.</p></div><button v-if="editingGoodsId !== null" class="text-button" @click="resetGoodsForm">Discard changes</button></div>
            <form class="form-grid two-column" @submit.prevent="saveGoods">
              <label>Client name<input v-model.trim="goodsForm.clientName" maxlength="150" required></label>
              <label>Freight type<select v-model="goodsForm.goodsType"><option value="GENERAL">General cargo</option><option value="REFRIGERATED">Refrigerated</option><option value="HAZARDOUS">Hazardous</option><option value="OVERSIZED">Oversized / heavy haul</option><option value="BULK">Dry bulk</option><option value="LIQUID">Liquid</option><option value="CONTAINERIZED">Containerized</option><option value="VEHICLES">Vehicles</option><option value="LIVESTOCK">Livestock</option></select></label>
              <label>Weight (kg)<input v-model.number="goodsForm.weightKg" type="number" min="0.01" step="0.01" required></label>
              <label>Freight revenue (EUR)<input v-model.number="goodsForm.revenueEur" type="number" min="0" step="0.01" required></label>
              <label>Volume (m³)<input v-model.number="goodsForm.volumeM3" type="number" min="0.01" step="0.01" required></label>
              <label class="full-width">Pickup city<input v-model.trim="goodsForm.pickupCity" :placeholder="warehouseCity" maxlength="500" required></label>
              <label class="full-width">Delivery city<input v-model.trim="goodsForm.deliveryCity" maxlength="500" required></label>
              <label class="full-width">Description <span class="optional">(optional)</span><textarea v-model.trim="goodsForm.description" maxlength="1000" rows="2"></textarea></label>
              <div class="form-actions"><button class="primary-button" :disabled="busy">{{ editingGoodsId === null ? 'Add freight' : 'Save changes' }}</button></div>
            </form>
          </section>
          <section class="panel"><div class="panel-heading"><div><h2>Freight register</h2><p>{{ goods.length }} items including delivered freight kept for route history</p></div></div>
            <div v-if="!goods.length" class="empty-state"><span>▣</span><strong>No freight registered</strong><p>Add freight details here before creating a route.</p></div>
            <div v-else class="table-wrap"><table><thead><tr><th>TRACKING</th><th>CLIENT / TYPE</th><th>ROUTE</th><th>WEIGHT / VOLUME</th><th>REVENUE</th><th>STATUS</th><th>ACTIONS</th></tr></thead><tbody><tr v-for="item in paginatedGoods" :key="item.id"><td><strong>{{ item.trackingCode }}</strong><small>{{ item.pickupCity }} → {{ item.deliveryCity }}</small></td><td>{{ item.clientName }}<small>{{ formatStatus(item.goodsType) }}</small></td><td>{{ item.status === 'ARCHIVED' ? 'Completed route history' : item.status === 'UNASSIGNED' ? 'Not assigned' : 'In a route' }}</td><td>{{ item.weightKg }} kg<small>{{ item.volumeM3 }} m³</small></td><td>€ {{ item.revenueEur?.toLocaleString() ?? 'Not set' }}</td><td><span class="status-pill" :class="item.status.toLowerCase()">{{ formatStatus(item.status) }}</span></td><td><div class="row-actions"><button v-if="item.status === 'UNASSIGNED' && !isGoodsOnRoute(item.id)" class="text-button" @click="editGoods(item)">Edit</button><button v-if="item.status === 'UNASSIGNED' && !isGoodsOnRoute(item.id)" class="text-button danger-text" @click="deleteGoods(item)">Delete</button></div></td></tr></tbody></table></div>
            <PaginationControl v-if="goods.length > PAGE_SIZE" v-model:current-page="freightPage" :page-count="pageCount(goods.length)" :total="goods.length" :page-size="PAGE_SIZE" />
          </section>
        </template>

        <template v-else-if="section === 'drivers'">
          <div class="page-heading"><div><p class="eyebrow">PEOPLE</p><h1>Drivers</h1><p class="subtitle">Manage driver details, license categories, and availability.</p></div></div>
          <section class="panel form-panel"><div class="panel-heading"><div><h2>{{ editingDriverId === null ? 'Add a driver' : 'Edit driver' }}</h2><p>Drivers on a route or returning cannot be edited or manually reassigned.</p></div><button v-if="editingDriverId !== null" class="text-button" @click="resetDriverForm">Discard changes</button></div>
            <form class="form-grid two-column" @submit.prevent="saveDriver"><label>Full name<input v-model.trim="driverForm.name" maxlength="100" required></label><label>Email<input v-model.trim="driverForm.email" type="email" maxlength="254" required></label><fieldset class="license-picker"><legend>License categories</legend><label v-for="license in ['B', 'BE', 'C', 'CE']" :key="license" class="check-inline"><input v-model="driverForm.licenseCategories" type="checkbox" :value="license">{{ license }}</label></fieldset><div class="form-actions"><button class="primary-button" :disabled="busy || !driverForm.licenseCategories.length">{{ editingDriverId === null ? 'Add driver' : 'Save changes' }}</button></div></form>
          </section>
          <section class="panel"><div class="panel-heading"><div><h2>Driver roster</h2><p>{{ drivers.length }} drivers registered</p></div></div><div v-if="!drivers.length" class="empty-state"><strong>No drivers registered</strong><p>Add drivers to make them available for route planning.</p></div><div v-else class="table-wrap"><table><thead><tr><th>DRIVER</th><th>LICENSES</th><th>STATUS</th><th>ACTIONS</th></tr></thead><tbody><tr v-for="driver in paginatedDrivers" :key="driver.id"><td><strong>{{ driver.name }}</strong><small>{{ driver.email }}</small></td><td><span v-for="license in driver.licenseCategories" :key="license" class="license-chip">{{ license }}</span></td><td><span class="status-pill" :class="driver.status.toLowerCase()">{{ formatStatus(driver.status) }}</span></td><td><div class="row-actions"><button v-if="!isDriverScheduled(driver.id) && driver.status !== 'IN_TRANSIT' && driver.status !== 'RETURNING'" class="text-button" @click="editDriver(driver)">Edit</button><select v-if="!isDriverScheduled(driver.id) && (driver.status === 'AVAILABLE' || driver.status === 'ON_LEAVE')" class="compact-select" :value="driver.status" aria-label="Driver availability" @change="updateDriverStatus(driver, ($event.target as HTMLSelectElement).value as 'AVAILABLE' | 'ON_LEAVE')"><option value="AVAILABLE">Available</option><option value="ON_LEAVE">On leave</option></select><button class="text-button danger-text" :disabled="driver.status === 'IN_TRANSIT' || driver.status === 'RETURNING' || hasDriverRoute(driver.id)" :title="hasDriverRoute(driver.id) ? 'Route history references this driver' : ''" @click="deleteDriver(driver)">Delete</button></div></td></tr></tbody></table></div><PaginationControl v-if="drivers.length > PAGE_SIZE" v-model:current-page="driverPage" :page-count="pageCount(drivers.length)" :total="drivers.length" :page-size="PAGE_SIZE" /></section>
        </template>

        <template v-else-if="section === 'fleet'">
          <div class="page-heading"><div><p class="eyebrow">FLEET OPERATIONS / TELEMETRY</p><h1>Fleet Operations &amp; Telemetry</h1><p class="subtitle">Live fleet records with a clearly marked sample telemetry console.</p></div><span class="demo-flag">TELEMETRY · DEMO FEED</span></div>
          <div class="fleet-command-grid">
            <article class="command-metric"><small>UNITS ON ROUTE</small><strong>84<span> / 96</span></strong><em>Illustrative fleet snapshot</em></article>
            <article class="command-metric"><small>AVG. FLEET SPEED</small><strong>68<span> KM/H</span></strong><em>Sample telemetry · not live</em></article>
            <article class="command-metric"><small>CUMULATIVE DIESEL</small><strong>12,480<span> L</span></strong><em>Sample telemetry · today</em></article>
            <article class="command-metric command-alert"><small>ROAD HAZARDS</small><strong>03<span> OPEN</span></strong><em>2 route cautions · 1 weather</em></article>
          </div>
          <section class="telemetry-grid">
            <article class="panel telemetry-panel">
              <div class="panel-heading"><div><h2>Unit inspection · sample feed</h2><p>{{ selectedVehicle ? `${selectedVehicle.licensePlate} · ${selectedVehicle.make} ${selectedVehicle.model}` : 'No registered unit selected' }}</p></div><div class="market-controls"><select v-if="vehicles.length" v-model.number="selectedVehicleId" aria-label="Select vehicle for inspection"><option v-for="vehicle in vehicles" :key="vehicle.id" :value="vehicle.id">{{ vehicle.licensePlate }}</option></select><span class="demo-flag">DEMO</span></div></div>
              <div v-if="selectedVehicle" class="engine-console">
                <div class="engine-gauges"><div class="gauge"><span>ENGINE SPEED</span><strong>1,480 <i>RPM</i></strong><div class="gauge-track"><b style="width: 48%"></b></div></div><div class="gauge"><span>TURBO BOOST</span><strong>1.8 <i>BAR</i></strong><div class="gauge-track"><b style="width: 62%"></b></div></div><div class="gauge"><span>OIL TEMP</span><strong>92 <i>°C</i></strong><div class="gauge-track"><b style="width: 58%"></b></div></div><div class="gauge"><span>COOLANT</span><strong>84 <i>°C</i></strong><div class="gauge-track"><b style="width: 53%"></b></div></div></div>
                <div class="inspection-foot"><span>ECO SCORE <strong>87 / 100</strong></span><span>GEAR <strong>{{ selectedVehicle ? `${(selectedVehicle.id % 12) + 1}A · DEMO` : '—' }}</strong></span><span>CB / DISPATCH <strong>CH 12 · SAMPLE</strong></span><button class="secondary-button" @click="section = 'maintenance'">Open diagnostics</button></div>
              </div>
              <div v-else class="empty-state"><strong>No vehicles available</strong><p>Add a vehicle to populate fleet operations.</p></div>
            </article>
            <aside class="panel hazard-feed"><div class="panel-heading"><div><h2>Road hazard attention</h2><p>Illustrative operational feed</p></div><span class="status-pill warning">DEMO</span></div><div class="feed-item critical-feed"><b>!</b><span><strong>Kassel Hills · steep incline</strong><small>Heavy-haul corridor · route scenario</small></span><time>08:42</time></div><div class="feed-item warning-feed"><b>!</b><span><strong>Low bridge bypass · A7</strong><small>Clearance review recommended</small></span><time>08:15</time></div><div class="feed-item"><b>i</b><span><strong>Wet road advisory · Bavaria</strong><small>Weather sample · no live feed</small></span><time>07:58</time></div></aside>
          </section>
          <section class="panel form-panel"><div class="panel-heading"><div><h2>{{ editingVehicleId === null ? 'Add a vehicle' : 'Edit vehicle' }}</h2><p>Fleet capacity is checked automatically when planning routes.</p></div><button v-if="editingVehicleId !== null" class="text-button" @click="resetVehicleForm">Discard changes</button></div>
            <form class="form-grid three-column" @submit.prevent="saveVehicle"><label>License plate<input v-model.trim="vehicleForm.licensePlate" maxlength="20" required></label>            <label>Vehicle type<select v-model="vehicleForm.vehicleType" @change="updateVehicleTypeDefaults"><option value="VAN">Van</option><option value="RIGID_TRUCK">Rigid truck</option><option value="ARTICULATED_TRUCK">Articulated truck</option></select></label><label>Transmission<select v-model="vehicleForm.transmissionType"><option v-if="vehicleForm.vehicleType === 'VAN'" value="MANUAL">Manual</option><option v-if="vehicleForm.vehicleType === 'VAN'" value="AUTOMATIC">Automatic</option><option v-else value="AMT">Automated manual (AMT)</option></select></label><label>Make<input v-model.trim="vehicleForm.make" maxlength="100" required></label><label>Model<input v-model.trim="vehicleForm.model" maxlength="100" required></label><label>Year<input v-model.number="vehicleForm.year" type="number" min="1900" max="2100" required></label><label>Mileage (km)<input v-model.number="vehicleForm.currentMileageKm" type="number" min="0" step="1" required></label><label>Max weight (kg)<input v-model.number="vehicleForm.maxWeightCapacityKg" type="number" min="0.01" step="0.01" required></label><label>Max volume (m³)<input v-model.number="vehicleForm.maxVolumeM3" type="number" min="0.01" step="0.01" required></label><div class="form-actions"><button class="primary-button" :disabled="busy">{{ editingVehicleId === null ? 'Add vehicle' : 'Save changes' }}</button></div></form>
          </section>
          <section class="panel">
            <div class="panel-heading"><div><h2>Vehicle manifest</h2><p>{{ vehicles.length }} registered units · driver/status from route and fleet records</p></div><div class="market-controls"><label class="sort-label" for="fleet-sort">SORT BY</label><select id="fleet-sort" v-model="fleetSort" aria-label="Sort vehicle manifest"><option value="CLASS">Vehicle class</option><option value="MILEAGE">Mileage</option><option value="STATUS">Status</option></select></div></div>
            <div v-if="!vehicles.length" class="empty-state"><strong>No vehicles registered</strong><p>Add a vehicle to start planning routes.</p></div>
            <div v-else class="table-wrap"><table><thead><tr><th>VEHICLE</th><th>CLASS / GEARBOX</th><th>ASSIGNED DRIVER</th><th>CAPACITY</th><th>MILEAGE</th><th>GEAR · DEMO</th><th>STATUS</th><th>ACTIONS</th></tr></thead><tbody><tr v-for="vehicle in paginatedVehicles" :key="vehicle.id"><td><button class="text-button manifest-select" @click="selectedVehicleId = vehicle.id">{{ vehicle.licensePlate }}</button><small>{{ vehicle.make }} {{ vehicle.model }} · {{ vehicle.year }}</small></td><td>{{ formatStatus(vehicle.vehicleType) }}<small>{{ formatStatus(vehicle.transmissionType) }}</small></td><td>{{ assignedDriver(vehicle.id) }}</td><td>{{ vehicle.maxWeightCapacityKg }} kg<small>{{ vehicle.maxVolumeM3 }} m³</small></td><td>{{ vehicle.currentMileageKm.toLocaleString() }} km</td><td><span class="demo-gear">{{ vehicle.status === 'IN_TRANSIT' ? `${(vehicle.id % 12) + 1}A` : '—' }}</span></td><td><span class="status-pill" :class="vehicle.status.toLowerCase()">{{ vehicleStatusLabel(vehicle) }}</span><span v-if="vehicle.maintenanceAutoTriggered && vehicle.status === 'IN_TRANSIT'" class="status-pill warning">MAINTENANCE DUE AFTER ROUTE</span></td><td><div class="row-actions"><button v-if="!isVehicleScheduled(vehicle.id) && vehicle.status !== 'IN_TRANSIT'" class="text-button" @click="editVehicle(vehicle)">Edit</button><select v-if="!isVehicleScheduled(vehicle.id) && (vehicle.status === 'AVAILABLE' || vehicle.status === 'MAINTENANCE')" class="compact-select" :value="vehicle.status" aria-label="Vehicle status" @change="updateVehicleStatus(vehicle, ($event.target as HTMLSelectElement).value as 'AVAILABLE' | 'MAINTENANCE')"><option value="AVAILABLE">Available</option><option value="MAINTENANCE">In maintenance</option></select><button class="text-button danger-text" :disabled="vehicle.status === 'IN_TRANSIT' || hasVehicleRoute(vehicle.id)" :title="hasVehicleRoute(vehicle.id) ? 'Route history references this vehicle' : ''" @click="deleteVehicle(vehicle)">Delete</button></div></td></tr></tbody></table></div>
            <PaginationControl v-if="vehicles.length > PAGE_SIZE" v-model:current-page="vehiclePage" :page-count="pageCount(vehicles.length)" :total="vehicles.length" :page-size="PAGE_SIZE" />
          </section>
          <section class="panel form-panel">
            <div class="panel-heading"><div><h2>{{ editingTrailerId === null ? 'Add a trailer' : 'Edit trailer' }}</h2><p>Set the trailer’s cargo category and compatible towing vehicle.</p></div><button v-if="editingTrailerId !== null" class="text-button" @click="resetTrailerForm">Discard changes</button></div>
            <form class="form-grid three-column" @submit.prevent="saveTrailer">
              <label>License plate<input v-model.trim="trailerForm.licensePlate" maxlength="20" required></label>
              <label>Trailer category<select v-model="trailerForm.trailerType" required><option value="BOX_TRAILER">Box / general cargo</option><option value="CURTAIN_SIDE_TRAILER">Curtain side / oversized</option><option value="REFRIGERATED_TRAILER">Refrigerated</option><option value="HAZARDOUS_TRAILER">Hazardous goods</option><option value="TANKER_TRAILER">Tanker / liquid</option><option value="FLATBED_TRAILER">Flatbed</option><option value="LOW_LOADER_TRAILER">Low loader / heavy haul</option><option value="CONTAINER_CHASSIS_TRAILER">Container chassis</option><option value="BULK_TIPPER_TRAILER">Bulk tipper</option><option value="LIVESTOCK_TRAILER">Livestock</option><option value="VAN_TRAILER">Van trailer · general</option><option value="RIGID_TRUCK_TRAILER">Rigid truck trailer · general</option><option value="ARTICULATED_TRUCK_TRAILER">Articulated trailer · general</option></select></label>
              <label>Compatible vehicle<select v-model="trailerForm.compatibleVehicleType" required><option value="VAN">Van</option><option value="RIGID_TRUCK">Rigid truck</option><option value="ARTICULATED_TRUCK">Articulated truck</option></select></label>
              <label>Make<input v-model.trim="trailerForm.make" maxlength="100" required></label>
              <label>Model<input v-model.trim="trailerForm.model" maxlength="100" required></label>
              <label>Year<input v-model.number="trailerForm.year" type="number" min="1900" max="2100" required></label>
              <label>Max weight (kg)<input v-model.number="trailerForm.maxWeightCapacityKg" type="number" min="0.01" step="0.01" required></label>
              <label>Max volume (m³)<input v-model.number="trailerForm.maxVolumeM3" type="number" min="0.01" step="0.01" required></label>
              <div class="form-actions"><button class="primary-button" :disabled="busy">{{ editingTrailerId === null ? 'Add trailer' : 'Save changes' }}</button></div>
            </form>
          </section>
          <section class="panel">
            <div class="panel-heading"><div><h2>Trailer register</h2><p>{{ trailers.length }} trailers · assigned by cargo compatibility, capacity, vehicle type, and schedule</p></div></div>
            <div v-if="!trailers.length" class="empty-state"><strong>No trailers registered</strong><p>Add compatible trailers to support articulated trucks and freight requiring specialist equipment.</p></div>
            <div v-else class="table-wrap"><table><thead><tr><th>TRAILER</th><th>CATEGORY</th><th>TOWING VEHICLE</th><th>CAPACITY</th><th>STATUS</th><th>ACTIONS</th></tr></thead><tbody>
              <tr v-for="trailer in trailers" :key="trailer.id"><td><strong>{{ trailer.licensePlate }}</strong><small>{{ trailer.make }} {{ trailer.model }} · {{ trailer.year }}</small></td><td>{{ trailerTypeLabel(trailer.trailerType) }}</td><td>{{ formatStatus(trailer.compatibleVehicleType) }}</td><td>{{ trailer.maxWeightCapacityKg.toLocaleString() }} kg<small>{{ trailer.maxVolumeM3 }} m³</small></td><td><span class="status-pill" :class="trailer.status.toLowerCase()">{{ formatStatus(trailer.status) }}</span></td><td><div class="row-actions"><button v-if="trailer.status !== 'IN_TRANSIT'" class="text-button" @click="editTrailer(trailer)">Edit</button><select v-if="trailer.status !== 'IN_TRANSIT'" class="compact-select" :value="trailer.status" aria-label="Trailer status" @change="updateTrailerStatus(trailer, ($event.target as HTMLSelectElement).value as 'AVAILABLE' | 'MAINTENANCE')"><option value="AVAILABLE">Available</option><option value="MAINTENANCE">In maintenance</option></select><button class="text-button danger-text" :disabled="trailer.status === 'IN_TRANSIT' || routes.some((route) => route.trailerId === trailer.id)" @click="deleteTrailer(trailer)">Delete</button></div></td></tr>
            </tbody></table></div>
          </section>
        </template>
        <template v-else-if="section === 'maintenance'">
          <div class="page-heading"><div><p class="eyebrow">MAINTENANCE / SERVICE HISTORY</p><h1>Maintenance &amp; Diagnostics</h1><p class="subtitle">Health is calculated from each component’s most recent recorded operation and current odometer/time.</p></div><span class="status-pill warning">WAREHOUSE · {{ warehouseCity }}</span></div>
          <div class="maintenance-note"><strong>INTERVAL POLICY</strong><span>For intervals supplied as ranges, the conservative lower bound is used. Where both distance and time apply, the bar reflects whichever limit is closer. A vehicle enters maintenance at 10% or less and cannot be planned on a route.</span></div>
          <section class="panel"><div class="panel-heading"><div><h2>Vehicle component health</h2><p>Every service entry stores the operation date and odometer reading.</p></div><span class="status-pill" :class="maintenanceVehicles.length ? 'maintenance' : 'available'">{{ maintenanceVehicles.length }} IN MAINTENANCE</span></div>
            <div v-if="!vehicles.length" class="empty-state"><strong>No vehicles registered</strong><p>Register vehicles to track their maintenance history.</p></div>
            <div v-else class="health-list"><article v-for="vehicle in vehicles" :key="vehicle.id" class="health-vehicle"><header><div><strong>{{ vehicle.licensePlate }}</strong><small>{{ vehicle.make }} {{ vehicle.model }} · {{ vehicle.currentMileageKm.toLocaleString() }} KM · {{ formatStatus(vehicle.transmissionType) }}</small></div><div><span class="status-pill" :class="vehicle.status.toLowerCase()">{{ vehicleStatusLabel(vehicle) }}</span><span v-if="vehicle.maintenanceAutoTriggered && vehicle.status === 'IN_TRANSIT'" class="status-pill warning">DUE AFTER RETURN</span></div></header><div class="health-components"><div v-for="component in vehicle.maintenanceComponents" :key="component.key" class="health-component"><div><span>{{ component.label }}</span><strong v-if="component.healthPercent !== null">{{ component.healthPercent }}%</strong><span v-else class="history-only">HISTORY ONLY</span></div><div v-if="component.healthPercent !== null" class="health-track"><span :class="healthClass(component.healthPercent)" :style="{ width: `${component.healthPercent}%` }"></span></div><small>Interval: {{ maintenanceInterval(component) }}<template v-if="component.healthPercent !== null"> · {{ remainingInterval(component) }}</template></small><small v-if="component.lastPerformedAt">Last operation: {{ displayDate(component.lastPerformedAt) }} at {{ component.lastOdometerKm?.toLocaleString() }} km<template v-if="component.lastCostEur !== null"> · €{{ component.lastCostEur.toFixed(2) }}</template></small><small v-else-if="component.healthPercent !== null" class="missing-service">No service history; vehicle is held from route planning until this operation is recorded.</small><small v-else class="missing-service">No service history recorded.</small><details class="maintenance-entry"><summary>{{ component.lastPerformedAt ? 'Record completed service' : 'Enter last service operation' }}</summary><label class="maintenance-input">Service date<input type="datetime-local" :value="maintenanceEntry(vehicle, component).performedAtLocal" @input="setMaintenanceDate(vehicle, component, ($event.target as HTMLInputElement).value)"></label><label class="maintenance-input">Odometer at service (km)<input type="number" min="0" step="1" :value="maintenanceEntry(vehicle, component).odometerKm" @input="setMaintenanceOdometer(vehicle, component, ($event.target as HTMLInputElement).value)"></label><label class="maintenance-input">Service cost (EUR)<input type="number" min="0" step="0.01" :value="maintenanceEntry(vehicle, component).costEur" @input="setMaintenanceCost(vehicle, component, ($event.target as HTMLInputElement).value)"></label><button class="text-button service-button" :disabled="busy || isVehicleScheduled(vehicle.id)" :title="isVehicleScheduled(vehicle.id) ? 'Maintenance cannot be recorded while a route is planned or active' : ''" @click="recordMaintenance(vehicle, component)">Save service history</button></details></div></div></article></div>
          </section>
          <section class="maintenance-lower">
            <article class="panel"><div class="panel-heading"><div><h2>Automatic workshop queue</h2><p>Vehicles due at or below the 10% threshold</p></div><span class="status-pill" :class="maintenanceVehicles.length ? 'maintenance' : 'available'">{{ maintenanceVehicles.length }} UNITS</span></div><div v-if="!maintenanceVehicles.length" class="empty-state"><strong>No vehicles currently due</strong><p>Vehicles at 10% health or below appear here automatically.</p></div><div v-for="vehicle in maintenanceVehicles" :key="vehicle.id" class="workshop-row"><b class="bay-number">{{ vehicle.licensePlate }}</b><span><strong>{{ vehicle.make }} {{ vehicle.model }}</strong><small>{{ maintenanceSummary(vehicle) }}</small></span><span class="status-pill maintenance">IN MAINTENANCE</span></div></article>
            <article class="panel"><div class="panel-heading"><div><h2>Diagnostic trouble codes</h2><p>Illustrative fault feed · no vehicle connection</p></div><span class="demo-flag">DEMO</span></div><div class="dtc-row"><span class="dtc-code">DTC P0420</span><span><strong>Catalyst efficiency warning</strong><small>Sample fault · requires workshop diagnosis</small></span><span class="status-pill warning">ADVISORY</span></div><div class="dtc-row"><span class="dtc-code">DTC B10A1</span><span><strong>Filament open circuit</strong><small>Sample fault · lighting circuit</small></span><span class="status-pill critical">ACTIVE</span></div></article>
          </section>
        </template>
        <template v-else>
          <FinanceView />
        </template>
        <footer class="page-footer">HAULMASTER DISPATCH <span>·</span> Routing and map data © OpenStreetMap contributors.</footer>
      </div>
    </section>
  </main>
</template>
