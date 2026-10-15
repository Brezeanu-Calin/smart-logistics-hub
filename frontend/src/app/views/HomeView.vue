<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import RouteMap from '../components/RouteMap.vue'
import api, {
  getErrorMessage,
  type Driver,
  type DriverRequest,
  type Goods,
  type GoodsRequest,
  type Route,
  type RouteCreateRequest,
  type Vehicle,
  type VehicleRequest,
} from '../api'

type Section = 'overview' | 'routes' | 'freight' | 'drivers' | 'vehicles'

const section = ref<Section>('overview')
const drivers = ref<Driver[]>([])
const vehicles = ref<Vehicle[]>([])
const goods = ref<Goods[]>([])
const routes = ref<Route[]>([])
const busy = ref(false)
const error = ref('')
const success = ref('')

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
})
const editingVehicleId = ref<number | null>(null)
const goodsForm = reactive<GoodsRequest>({
  clientName: '',
  goodsType: 'GENERAL',
  description: null,
  weightKg: 1,
  volumeM3: 1,
  pickupAddress: '',
  deliveryAddress: '',
})
const editingGoodsId = ref<number | null>(null)
const routeForm = reactive({
  driverId: 0,
  vehicleId: 0,
  departureLocal: '',
})
const selectedGoodsIds = ref<number[]>([])
const expandedRouteMaps = ref(new Set<number>())

const activeRoutes = computed(() => routes.value.filter((route) => route.status === 'ACTIVE').length)
const availableDrivers = computed(() => drivers.value.filter((driver) => driver.status === 'AVAILABLE').length)
const availableVehicles = computed(() => vehicles.value.filter((vehicle) => vehicle.status === 'AVAILABLE').length)
const unassignedGoods = computed(() => goods.value.filter((item) => item.status === 'UNASSIGNED'))
const selectedGoods = computed(() =>
  selectedGoodsIds.value
    .map((id) => goods.value.find((item) => item.id === id))
    .filter((item): item is Goods => item !== undefined),
)

function clearMessages() {
  error.value = ''
  success.value = ''
}

function toggleRouteMap(routeId: number) {
  const expanded = new Set(expandedRouteMaps.value)
  if (expanded.has(routeId)) expanded.delete(routeId)
  else expanded.add(routeId)
  expandedRouteMaps.value = expanded
}

async function loadData() {
  const [driverResponse, vehicleResponse, goodsResponse, routeResponse] = await Promise.all([
    api.get<Driver[]>('/drivers'),
    api.get<Vehicle[]>('/vehicles'),
    api.get<Goods[]>('/goods'),
    api.get<Route[]>('/routes'),
  ])
  drivers.value = driverResponse.data
  vehicles.value = vehicleResponse.data
  goods.value = goodsResponse.data
  routes.value = routeResponse.data
}

async function refresh() {
  try {
    await loadData()
  } catch (requestError) {
    error.value = getErrorMessage(requestError)
  }
}

let refreshTimer: number
onMounted(() => {
  void refresh()
  refreshTimer = window.setInterval(() => void refresh(), 30_000)
})
onUnmounted(() => window.clearInterval(refreshTimer))

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
  })
}

function editVehicle(vehicle: Vehicle) {
  editingVehicleId.value = vehicle.id
  Object.assign(vehicleForm, {
    licensePlate: vehicle.licensePlate,
    vehicleType: vehicle.vehicleType,
    make: vehicle.make,
    model: vehicle.model,
    year: vehicle.year,
    maxWeightCapacityKg: vehicle.maxWeightCapacityKg,
    maxVolumeM3: vehicle.maxVolumeM3,
    currentMileageKm: vehicle.currentMileageKm,
  })
  section.value = 'vehicles'
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

function resetGoodsForm() {
  editingGoodsId.value = null
  Object.assign(goodsForm, {
    clientName: '',
    goodsType: 'GENERAL',
    description: null,
    weightKg: 1,
    volumeM3: 1,
    pickupAddress: '',
    deliveryAddress: '',
  })
}

function editGoods(item: Goods) {
  editingGoodsId.value = item.id
  Object.assign(goodsForm, {
    clientName: item.clientName,
    goodsType: item.goodsType,
    description: item.description,
    weightKg: item.weightKg,
    volumeM3: item.volumeM3,
    pickupAddress: item.pickupAddress,
    deliveryAddress: item.deliveryAddress,
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
        <span class="brand-mark">SL</span>
        <span><strong>Smart Logistics</strong><small>Operations hub</small></span>
      </a>
      <nav class="side-nav" aria-label="Main navigation">
        <button :class="{ selected: section === 'overview' }" @click="section = 'overview'"><span>▦</span> Overview</button>
        <button :class="{ selected: section === 'routes' }" @click="section = 'routes'"><span>↗</span> Routes <b>{{ activeRoutes }}</b></button>
        <button :class="{ selected: section === 'freight' }" @click="section = 'freight'"><span>▣</span> Freight</button>
        <button :class="{ selected: section === 'drivers' }" @click="section = 'drivers'"><span>♙</span> Drivers</button>
        <button :class="{ selected: section === 'vehicles' }" @click="section = 'vehicles'"><span>▰</span> Vehicles</button>
      </nav>
      <div class="sidebar-note">
        <span class="note-icon">i</span>
        <div><strong>AI freight entry</strong><p>Manual entry is enabled. AI assistant integration can be added later.</p></div>
      </div>
      <div class="sidebar-footer">SMART LOGISTICS HUB <span>v1.0</span></div>
    </aside>

    <section class="main-panel">
      <header class="topbar">
        <div class="crumb">Workspace <span>/</span> {{ section === 'overview' ? 'Overview' : section }}</div>
        <div class="topbar-right"><span class="live-dot"></span> Operations online <button class="refresh-button" :disabled="busy" @click="refresh" aria-label="Refresh data">↻</button></div>
      </header>

      <div class="content">
        <div v-if="error" class="notice notice-error" role="alert"><span>!</span>{{ error }}<button @click="error = ''">×</button></div>
        <div v-if="success" class="notice notice-success" role="status"><span>✓</span>{{ success }}<button @click="success = ''">×</button></div>

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
            <button class="quick-card" @click="section = 'vehicles'"><span class="quick-icon blue">＋</span><span><strong>Add a vehicle</strong><small>Register fleet capacity</small></span><b>→</b></button>
          </div>
        </template>

        <template v-else-if="section === 'routes'">
          <div class="page-heading"><div><p class="eyebrow">DISPATCH</p><h1>Routes</h1><p class="subtitle">Plan journeys, track delivery progress, and manage returns.</p></div></div>
          <section class="panel form-panel">
            <div class="panel-heading"><div><h2>Plan a route</h2><p>Addresses are geocoded with Nominatim and routed by OSRM.</p></div><span class="form-step">NEW ROUTE</span></div>
            <form class="form-grid route-form" @submit.prevent="createRoute">
              <label>Driver<select v-model.number="routeForm.driverId" required><option :value="0" disabled>Select an available driver</option><option v-for="driver in drivers.filter((item) => item.status === 'AVAILABLE')" :key="driver.id" :value="driver.id">{{ driver.name }} · {{ driver.licenseCategories.join(', ') }}</option></select></label>
              <label>Vehicle<select v-model.number="routeForm.vehicleId" required><option :value="0" disabled>Select an available vehicle</option><option v-for="vehicle in vehicles.filter((item) => item.status === 'AVAILABLE')" :key="vehicle.id" :value="vehicle.id">{{ vehicle.licensePlate }} · {{ vehicle.vehicleType.replaceAll('_', ' ') }}</option></select></label>
              <label>Departure time<input v-model="routeForm.departureLocal" type="datetime-local" required></label>
              <fieldset class="goods-picker"><legend>Unassigned freight <span>{{ selectedGoodsIds.length }} selected</span></legend><div v-if="!unassignedGoods.length" class="small-empty">No unassigned freight is available.</div><label v-for="item in unassignedGoods" :key="item.id" class="check-row"><input v-model="selectedGoodsIds" type="checkbox" :value="item.id"><span><strong>{{ item.clientName }}</strong><small>{{ item.trackingCode }} · {{ item.pickupAddress }} → {{ item.deliveryAddress }}</small></span><b>{{ item.weightKg }} kg</b></label></fieldset>
              <div v-if="selectedGoods.length" class="selected-order"><strong>Pickup and delivery order</strong><p>All pickups are planned first; delivery order follows this list. Use the arrows to reorder.</p><ol><li v-for="(item, index) in selectedGoods" :key="item.id"><span class="order-number">{{ index + 1 }}</span><span class="order-name">{{ item.clientName }} <small>{{ item.trackingCode }}</small></span><button type="button" :disabled="index === 0" :aria-label="`Move ${item.clientName} up`" @click="moveSelectedGoods(index, -1)">↑</button><button type="button" :disabled="index === selectedGoods.length - 1" :aria-label="`Move ${item.clientName} down`" @click="moveSelectedGoods(index, 1)">↓</button></li></ol></div>
              <div class="form-actions"><button class="primary-button" :disabled="busy || !routeForm.driverId || !routeForm.vehicleId || selectedGoodsIds.length > 12">Calculate &amp; plan route</button><span>Each freight item gets pickup and delivery stops. Addresses are geocoded once and cached.</span></div>
            </form>
          </section>
          <section class="panel">
            <div class="panel-heading"><div><h2>All routes</h2><p>{{ routes.length }} route{{ routes.length === 1 ? '' : 's' }} in the system</p></div></div>
            <div v-if="!routes.length" class="empty-state"><span>↗</span><strong>No routes planned</strong><p>Assign an available driver, vehicle, and freight to create your first route.</p></div>
            <div v-else class="route-list">
              <article v-for="route in routes" :key="route.id" class="route-card">
                <div class="route-card-head"><div><span class="route-number">ROUTE #{{ route.id }}</span><span class="status-pill" :class="route.status.toLowerCase()">{{ formatStatus(route.status) }}</span></div><div class="route-actions"><button class="text-button" @click="toggleRouteMap(route.id)">{{ expandedRouteMaps.has(route.id) ? 'Hide map' : 'Show map' }}</button><button v-if="route.status === 'PLANNED'" class="small-button" :disabled="busy" @click="setRouteStatus(route, 'ACTIVE')">Start route</button><button v-if="route.status === 'PLANNED' || (route.status === 'CANCELLED' && (!route.estimatedReturnTime || route.returnedAt))" class="icon-button danger" :disabled="busy" aria-label="Delete route" @click="deleteRoute(route)">⌫</button></div></div>
                <div class="route-summary"><div><small>DRIVER</small><strong>{{ route.driverName }}</strong></div><div><small>VEHICLE</small><strong>{{ route.vehicleLicensePlate }}<template v-if="route.trailerLicensePlate"> + {{ route.trailerLicensePlate }}</template></strong></div><div><small>DEPARTURE</small><strong>{{ displayDate(route.departureTime) }}</strong></div><div><small>EST. AT DEPOT</small><strong>{{ displayDate(route.estimatedReturnTime) }}</strong></div></div>
                <div class="route-metrics"><span>↔ {{ (route.distanceMeters / 1000).toFixed(1) }} km total distance</span><span>◷ {{ displayDuration(route.drivingDurationSeconds) }} driving</span><span v-if="route.returnedAt">✓ Returned {{ displayDate(route.returnedAt) }}</span></div>
                <RouteMap v-if="expandedRouteMaps.has(route.id)" :route-id="route.id" :geometry="route.geometry" :stops="route.stops" />
                <ol class="stop-list"><li v-for="stop in route.stops" :key="`${stop.goodsId}-${stop.type}`"><span class="stop-marker" :class="stop.type.toLowerCase()"></span><span><small>{{ stop.type }} · {{ stop.trackingCode }}</small><strong>{{ stop.address }}</strong></span><button v-if="route.status === 'ACTIVE' && stop.type === 'DELIVERY' && goodsStatus(stop.goodsId) === 'IN_TRANSIT' && canConfirmDelivery(route, stop.goodsId)" class="text-button" :disabled="busy" @click="deliverGoods(route, stop.goodsId)">Confirm delivery</button><span v-else-if="stop.type === 'DELIVERY' && ['DELIVERED', 'ARCHIVED'].includes(goodsStatus(stop.goodsId) ?? '')" class="delivered-label">Delivered</span><span v-else-if="route.status === 'ACTIVE' && stop.type === 'DELIVERY' && goodsStatus(stop.goodsId) === 'IN_TRANSIT'" class="delivered-label">Next stop pending</span></li></ol>
                <div v-if="route.status === 'PLANNED'" class="route-footer"><button class="text-button danger-text" :disabled="busy" @click="setRouteStatus(route, 'CANCELLED')">Cancel route</button></div>
              </article>
            </div>
          </section>
        </template>

        <template v-else-if="section === 'freight'">
          <div class="page-heading"><div><p class="eyebrow">SHIPMENTS</p><h1>Freight</h1><p class="subtitle">Register goods manually and assign them to delivery routes.</p></div></div>
          <section class="panel form-panel">
            <div class="panel-heading"><div><h2>{{ editingGoodsId === null ? 'Add freight' : 'Edit freight' }}</h2><p>Tracking codes are generated automatically.</p></div><button v-if="editingGoodsId !== null" class="text-button" @click="resetGoodsForm">Discard changes</button></div>
            <form class="form-grid two-column" @submit.prevent="saveGoods">
              <label>Client name<input v-model.trim="goodsForm.clientName" maxlength="150" required></label>
              <label>Freight type<select v-model="goodsForm.goodsType"><option value="GENERAL">General</option><option value="REFRIGERATED">Refrigerated</option><option value="HAZARDOUS">Hazardous</option></select></label>
              <label>Weight (kg)<input v-model.number="goodsForm.weightKg" type="number" min="0.01" step="0.01" required></label>
              <label>Volume (m³)<input v-model.number="goodsForm.volumeM3" type="number" min="0.01" step="0.01" required></label>
              <label class="full-width">Pickup address<input v-model.trim="goodsForm.pickupAddress" maxlength="500" required></label>
              <label class="full-width">Delivery address<input v-model.trim="goodsForm.deliveryAddress" maxlength="500" required></label>
              <label class="full-width">Description <span class="optional">(optional)</span><textarea v-model.trim="goodsForm.description" maxlength="1000" rows="2"></textarea></label>
              <div class="form-actions"><button class="primary-button" :disabled="busy">{{ editingGoodsId === null ? 'Add freight' : 'Save changes' }}</button></div>
            </form>
          </section>
          <section class="panel"><div class="panel-heading"><div><h2>Freight register</h2><p>{{ goods.length }} items including delivered freight kept for route history</p></div></div>
            <div v-if="!goods.length" class="empty-state"><span>▣</span><strong>No freight registered</strong><p>Add freight details here before creating a route.</p></div>
            <div v-else class="table-wrap"><table><thead><tr><th>TRACKING</th><th>CLIENT / TYPE</th><th>ROUTE</th><th>WEIGHT / VOLUME</th><th>STATUS</th><th>ACTIONS</th></tr></thead><tbody><tr v-for="item in goods" :key="item.id"><td><strong>{{ item.trackingCode }}</strong><small>{{ item.pickupAddress }} → {{ item.deliveryAddress }}</small></td><td>{{ item.clientName }}<small>{{ formatStatus(item.goodsType) }}</small></td><td>{{ item.status === 'ARCHIVED' ? 'Completed route history' : item.status === 'UNASSIGNED' ? 'Not assigned' : 'In a route' }}</td><td>{{ item.weightKg }} kg<small>{{ item.volumeM3 }} m³</small></td><td><span class="status-pill" :class="item.status.toLowerCase()">{{ formatStatus(item.status) }}</span></td><td><div class="row-actions"><button v-if="item.status === 'UNASSIGNED' && !isGoodsOnRoute(item.id)" class="text-button" @click="editGoods(item)">Edit</button><button v-if="item.status === 'UNASSIGNED' && !isGoodsOnRoute(item.id)" class="text-button danger-text" @click="deleteGoods(item)">Delete</button></div></td></tr></tbody></table></div>
          </section>
        </template>

        <template v-else-if="section === 'drivers'">
          <div class="page-heading"><div><p class="eyebrow">PEOPLE</p><h1>Drivers</h1><p class="subtitle">Manage driver details, license categories, and availability.</p></div></div>
          <section class="panel form-panel"><div class="panel-heading"><div><h2>{{ editingDriverId === null ? 'Add a driver' : 'Edit driver' }}</h2><p>Drivers on a route or returning cannot be edited or manually reassigned.</p></div><button v-if="editingDriverId !== null" class="text-button" @click="resetDriverForm">Discard changes</button></div>
            <form class="form-grid two-column" @submit.prevent="saveDriver"><label>Full name<input v-model.trim="driverForm.name" maxlength="100" required></label><label>Email<input v-model.trim="driverForm.email" type="email" maxlength="254" required></label><fieldset class="license-picker"><legend>License categories</legend><label v-for="license in ['B', 'BE', 'C', 'CE']" :key="license" class="check-inline"><input v-model="driverForm.licenseCategories" type="checkbox" :value="license">{{ license }}</label></fieldset><div class="form-actions"><button class="primary-button" :disabled="busy || !driverForm.licenseCategories.length">{{ editingDriverId === null ? 'Add driver' : 'Save changes' }}</button></div></form>
          </section>
          <section class="panel"><div class="panel-heading"><div><h2>Driver roster</h2><p>{{ drivers.length }} drivers registered</p></div></div><div v-if="!drivers.length" class="empty-state"><strong>No drivers registered</strong><p>Add drivers to make them available for route planning.</p></div><div v-else class="table-wrap"><table><thead><tr><th>DRIVER</th><th>LICENSES</th><th>STATUS</th><th>ACTIONS</th></tr></thead><tbody><tr v-for="driver in drivers" :key="driver.id"><td><strong>{{ driver.name }}</strong><small>{{ driver.email }}</small></td><td><span v-for="license in driver.licenseCategories" :key="license" class="license-chip">{{ license }}</span></td><td><span class="status-pill" :class="driver.status.toLowerCase()">{{ formatStatus(driver.status) }}</span></td><td><div class="row-actions"><button v-if="!isDriverScheduled(driver.id) && driver.status !== 'IN_TRANSIT' && driver.status !== 'RETURNING'" class="text-button" @click="editDriver(driver)">Edit</button><select v-if="!isDriverScheduled(driver.id) && (driver.status === 'AVAILABLE' || driver.status === 'ON_LEAVE')" class="compact-select" :value="driver.status" aria-label="Driver availability" @change="updateDriverStatus(driver, ($event.target as HTMLSelectElement).value as 'AVAILABLE' | 'ON_LEAVE')"><option value="AVAILABLE">Available</option><option value="ON_LEAVE">On leave</option></select><button class="text-button danger-text" :disabled="driver.status === 'IN_TRANSIT' || driver.status === 'RETURNING' || hasDriverRoute(driver.id)" :title="hasDriverRoute(driver.id) ? 'Route history references this driver' : ''" @click="deleteDriver(driver)">Delete</button></div></td></tr></tbody></table></div></section>
        </template>

        <template v-else>
          <div class="page-heading"><div><p class="eyebrow">FLEET</p><h1>Vehicles</h1><p class="subtitle">Maintain vehicle capacity, mileage, and operational status.</p></div></div>
          <section class="panel form-panel"><div class="panel-heading"><div><h2>{{ editingVehicleId === null ? 'Add a vehicle' : 'Edit vehicle' }}</h2><p>Fleet capacity is checked automatically when planning routes.</p></div><button v-if="editingVehicleId !== null" class="text-button" @click="resetVehicleForm">Discard changes</button></div>
            <form class="form-grid three-column" @submit.prevent="saveVehicle"><label>License plate<input v-model.trim="vehicleForm.licensePlate" maxlength="20" required></label><label>Vehicle type<select v-model="vehicleForm.vehicleType"><option value="VAN">Van</option><option value="RIGID_TRUCK">Rigid truck</option><option value="ARTICULATED_TRUCK">Articulated truck</option></select></label><label>Make<input v-model.trim="vehicleForm.make" maxlength="100" required></label><label>Model<input v-model.trim="vehicleForm.model" maxlength="100" required></label><label>Year<input v-model.number="vehicleForm.year" type="number" min="1900" max="2100" required></label><label>Mileage (km)<input v-model.number="vehicleForm.currentMileageKm" type="number" min="0" step="1" required></label><label>Max weight (kg)<input v-model.number="vehicleForm.maxWeightCapacityKg" type="number" min="0.01" step="0.01" required></label><label>Max volume (m³)<input v-model.number="vehicleForm.maxVolumeM3" type="number" min="0.01" step="0.01" required></label><div class="form-actions"><button class="primary-button" :disabled="busy">{{ editingVehicleId === null ? 'Add vehicle' : 'Save changes' }}</button></div></form>
          </section>
          <section class="panel"><div class="panel-heading"><div><h2>Fleet register</h2><p>{{ vehicles.length }} vehicles registered</p></div></div><div v-if="!vehicles.length" class="empty-state"><strong>No vehicles registered</strong><p>Add a vehicle to start planning routes.</p></div><div v-else class="table-wrap"><table><thead><tr><th>VEHICLE</th><th>TYPE</th><th>CAPACITY</th><th>MILEAGE</th><th>STATUS</th><th>ACTIONS</th></tr></thead><tbody><tr v-for="vehicle in vehicles" :key="vehicle.id"><td><strong>{{ vehicle.licensePlate }}</strong><small>{{ vehicle.make }} {{ vehicle.model }} · {{ vehicle.year }}</small></td><td>{{ formatStatus(vehicle.vehicleType) }}</td><td>{{ vehicle.maxWeightCapacityKg }} kg<small>{{ vehicle.maxVolumeM3 }} m³</small></td><td>{{ vehicle.currentMileageKm.toLocaleString() }} km</td><td><span class="status-pill" :class="vehicle.status.toLowerCase()">{{ formatStatus(vehicle.status) }}</span></td><td><div class="row-actions"><button v-if="!isVehicleScheduled(vehicle.id) && vehicle.status !== 'IN_TRANSIT'" class="text-button" @click="editVehicle(vehicle)">Edit</button><select v-if="!isVehicleScheduled(vehicle.id) && (vehicle.status === 'AVAILABLE' || vehicle.status === 'MAINTENANCE')" class="compact-select" :value="vehicle.status" aria-label="Vehicle status" @change="updateVehicleStatus(vehicle, ($event.target as HTMLSelectElement).value as 'AVAILABLE' | 'MAINTENANCE')"><option value="AVAILABLE">Available</option><option value="MAINTENANCE">Maintenance</option></select><button class="text-button danger-text" :disabled="vehicle.status === 'IN_TRANSIT' || hasVehicleRoute(vehicle.id)" :title="hasVehicleRoute(vehicle.id) ? 'Route history references this vehicle' : ''" @click="deleteVehicle(vehicle)">Delete</button></div></td></tr></tbody></table></div></section>
        </template>
        <footer class="page-footer">Smart Logistics Hub <span>·</span> Routing and map data © OpenStreetMap contributors.</footer>
      </div>
    </section>
  </main>
</template>
