import { useCallback, useEffect, useMemo, useState } from 'react'
import { StatusBar } from 'expo-status-bar'
import { router, useFocusEffect, useLocalSearchParams } from 'expo-router'
import {
  Alert,
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native'
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context'
import RouteMap from '../components/RouteMap'
import DatePickerField from '../components/DatePickerField'
import DateTimePickerField from '../components/DateTimePickerField'
import {
  type AssistantAction,
  type AssistantMessage,
  type AssistantReply,
  type Driver,
  type FinanceReport,
  type Goods,
  type GoodsType,
  type Route,
  type RouteSimulationPosition,
  type Trailer,
  type TrailerMaintenanceComponent,
  type Vehicle,
  type VehicleMaintenanceComponent,
  send,
  request,
} from '../api'

type Tab = 'Home' | 'Routes' | 'Freight' | 'Drivers' | 'Fleet' | 'Service' | 'Finance'
type SelectOption = { value: string; label: string; detail?: string }
type FormKind = 'freight' | 'driver' | 'vehicle' | 'trailer' | 'route' | 'maintenance' | 'trailerMaintenance'
type FormModal = {
  kind: FormKind
  values: Record<string, string>
  editingId?: number
  vehicle?: Vehicle
  trailer?: Trailer
  component?: VehicleMaintenanceComponent
  trailerComponent?: TrailerMaintenanceComponent
}
type ChatLine = AssistantMessage & { action?: AssistantAction }

const TABS: Tab[] = ['Home', 'Routes', 'Freight', 'Drivers', 'Fleet', 'Service', 'Finance']
const ACCENT = '#f59e0b'
const NAVY = '#111416'
const MAX_OPERATIONAL_CONTEXT_BYTES = 150_000
const GOODS_TYPES: GoodsType[] = [
  'GENERAL', 'REFRIGERATED', 'HAZARDOUS', 'OVERSIZED', 'BULK',
  'LIQUID', 'CONTAINERIZED', 'VEHICLES', 'LIVESTOCK',
]
const VEHICLE_TYPES = ['VAN', 'RIGID_TRUCK', 'ARTICULATED_TRUCK']
const TRANSMISSIONS = ['MANUAL', 'AUTOMATIC', 'AMT']
const TRAILER_TYPES = [
  'BOX_TRAILER', 'CURTAIN_SIDE_TRAILER', 'REFRIGERATED_TRAILER', 'HAZARDOUS_TRAILER',
  'TANKER_TRAILER', 'FLATBED_TRAILER', 'LOW_LOADER_TRAILER', 'CONTAINER_CHASSIS_TRAILER',
  'BULK_TIPPER_TRAILER', 'LIVESTOCK_TRAILER',
]

const dateString = (value: string | null | undefined) =>
  value ? new Date(value).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' }) : '—'
const dateInputValue = (value: Date) => {
  const year = value.getFullYear()
  const month = String(value.getMonth() + 1).padStart(2, '0')
  const day = String(value.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}
const dateTimeInputValue = (value: Date) => {
  const adjusted = new Date(value.getTime() - value.getTimezoneOffset() * 60_000)
  return adjusted.toISOString().slice(0, 16)
}
const money = (value: number | null | undefined) =>
  value == null ? 'Not recorded' : `€${value.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
const displayStatus = (value: string) => value.replaceAll('_', ' ').toLowerCase()

function toFormValues(value: object) {
  return Object.fromEntries(Object.entries(value).map(([key, item]) => [
    key,
    item == null ? '' : Array.isArray(item) ? item.join(', ') : String(item),
  ]))
}

function confirm(title: string, message: string, action: () => void) {
  Alert.alert(title, message, [
    { text: 'Cancel', style: 'cancel' },
    { text: 'Confirm', style: 'destructive', onPress: action },
  ])
}

function MobileApp() {
  const params = useLocalSearchParams<{ screen?: string }>()
  const screenParam = Array.isArray(params.screen) ? params.screen[0] : params.screen
  const tab = TABS.find((item) => item.toLowerCase() === screenParam?.toLowerCase()) ?? 'Home'
  const [drivers, setDrivers] = useState<Driver[]>([])
  const [vehicles, setVehicles] = useState<Vehicle[]>([])
  const [trailers, setTrailers] = useState<Trailer[]>([])
  const [goods, setGoods] = useState<Goods[]>([])
  const [routes, setRoutes] = useState<Route[]>([])
  const [simulationPositions, setSimulationPositions] = useState<Record<number, RouteSimulationPosition>>({})
  const [finance, setFinance] = useState<FinanceReport | null>(null)
  const [fromDate, setFromDate] = useState(() => dateInputValue(new Date(new Date().getFullYear(), new Date().getMonth(), 1)))
  const [toDate, setToDate] = useState(() => dateInputValue(new Date()))
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [search, setSearch] = useState('')
  const [modal, setModal] = useState<FormModal | null>(null)
  const [chat, setChat] = useState<ChatLine[]>([])
  const [chatInput, setChatInput] = useState('')
  const [chatBusy, setChatBusy] = useState(false)
  const [pendingAction, setPendingAction] = useState<AssistantAction | null>(null)
  const [assistantOpen, setAssistantOpen] = useState(false)
  const [expandedHealth, setExpandedHealth] = useState<Record<string, boolean>>({})

  const setTab = (nextTab: Tab) => {
    router.setParams({ screen: nextTab.toLowerCase() })
    setSearch('')
  }

  const fetchData = useCallback(async () => {
    const [nextDrivers, nextVehicles, nextTrailers, nextGoods, nextRoutes, nextFinance, positions] = await Promise.all([
      request<Driver[]>('/drivers'),
      request<Vehicle[]>('/vehicles'),
      request<Trailer[]>('/trailers'),
      request<Goods[]>('/goods'),
      request<Route[]>('/routes'),
      request<FinanceReport>(`/finance?from=${encodeURIComponent(fromDate)}&to=${encodeURIComponent(toDate)}`),
      request<RouteSimulationPosition[]>('/routes/simulations'),
    ])
    setDrivers(nextDrivers)
    setVehicles(nextVehicles)
    setTrailers(nextTrailers)
    setGoods(nextGoods)
    setRoutes(nextRoutes)
    setFinance(nextFinance)
    setSimulationPositions(Object.fromEntries(positions.map((position) => [position.routeId, position])))
  }, [fromDate, toDate])

  const refresh = useCallback(async () => {
    setLoading(true)
    try {
      await fetchData()
      setError('')
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : 'Could not load operations data.')
    } finally {
      setLoading(false)
    }
  }, [fetchData, setError, setLoading])

  useFocusEffect(
    useCallback(() => {
      void refresh()
    }, [refresh]),
  )

  useEffect(() => {
    const timer = setInterval(() => {
      void request<RouteSimulationPosition[]>('/routes/simulations')
        .then((positions) => setSimulationPositions(Object.fromEntries(positions.map((position) => [position.routeId, position]))))
        .catch((simulationError: unknown) => setError(simulationError instanceof Error ? simulationError.message : 'Could not refresh route positions.'))
    }, 5000)
    return () => clearInterval(timer)
  }, [])

  useEffect(() => {
    if (!error && !notice) return
    const timer = setTimeout(() => {
      setError('')
      setNotice('')
    }, 5000)
    return () => clearTimeout(timer)
  }, [error, notice])

  const counts = useMemo(() => ({
    activeRoutes: routes.filter((route) => route.status === 'ACTIVE').length,
    availableDrivers: drivers.filter((driver) => driver.status === 'AVAILABLE'
      && !routes.some((route) => route.driverId === driver.id
        && (route.status === 'PLANNED' || route.status === 'ACTIVE'))).length,
    availableVehicles: vehicles.filter((vehicle) => vehicle.status === 'AVAILABLE' && !vehicle.maintenanceAutoTriggered
      && !routes.some((route) => route.vehicleId === vehicle.id
        && (route.status === 'PLANNED' || route.status === 'ACTIVE'))).length,
    unassignedGoods: goods.filter((item) => item.status === 'UNASSIGNED').length,
    dueVehicles: vehicles.filter((vehicle) => vehicle.status === 'MAINTENANCE').length,
  }), [drivers, goods, routes, vehicles])

  const hasScheduledDriverRoute = (id: number) => routes.some((route) =>
    route.driverId === id && (route.status === 'PLANNED' || route.status === 'ACTIVE'))
  const hasScheduledVehicleRoute = (id: number) => routes.some((route) =>
    route.vehicleId === id && (route.status === 'PLANNED' || route.status === 'ACTIVE'))
  const hasScheduledTrailerRoute = (id: number) => routes.some((route) =>
    route.trailerId === id && (route.status === 'PLANNED' || route.status === 'ACTIVE'))
  const hasDriverHistory = (id: number) => routes.some((route) => route.driverId === id)
  const hasVehicleHistory = (id: number) => routes.some((route) => route.vehicleId === id)
  const hasTrailerHistory = (id: number) => routes.some((route) => route.trailerId === id)
  const hasGoodsHistory = (id: number) => routes.some((route) =>
    route.stops.some((stop) => stop.goodsId === id))

  function openForm(kind: FormKind, value: object = {}, extra: Partial<FormModal> = {}) {
    setError('')
    setModal({ kind, values: toFormValues(value), ...extra })
  }

  function editRoute(route: Route) {
    const departure = new Date(route.departureTime)
    openForm('route', {
      departureTime: dateTimeInputValue(departure),
      driverId: route.driverId,
      vehicleId: route.vehicleId,
      trailerId: route.trailerId ?? '',
      goodsIds: route.stops
        .filter((stop) => stop.type === 'PICKUP')
        .sort((first, second) => first.stopOrder - second.stopOrder)
        .map((stop) => stop.goodsId)
        .join(','),
    }, { editingId: route.id })
  }

  async function mutate(action: () => Promise<unknown>, message: string) {
    setBusy(true)
    setError('')
    setNotice('')
    try {
      await action()
      setNotice(message)
      setModal(null)
      await fetchData()
    } catch (actionError) {
      setError(actionError instanceof Error ? actionError.message : 'The operation failed.')
    } finally {
      setBusy(false)
    }
  }

  function updateField(key: string, value: string) {
    setModal((current) => current ? {
      ...current,
      values: { ...current.values, [key]: value, ...(key === 'vehicleId' ? { trailerId: '' } : {}) },
    } : current)
  }

  async function saveForm() {
    if (!modal) return
    const values = modal.values
    const id = modal.editingId
    const text = (key: string) => (values[key] ?? '').trim()
    const number = (key: string) => text(key) ? Number(text(key)) : Number.NaN
    const invalid = (message: string) => {
      setError(message)
      return
    }
    let path = ''
    let method = id === undefined ? 'POST' : 'PUT'
    let payload: Record<string, unknown> = {}

    switch (modal.kind) {
      case 'freight': {
        const revenueText = text('revenueEur')
        const revenueEur = revenueText ? Number(revenueText) : Number.NaN
        const weightKg = number('weightKg')
        const volumeM3 = number('volumeM3')
        if (!text('clientName') || text('clientName').length > 150
          || !text('pickupCity') || text('pickupCity').length > 500
          || !text('deliveryCity') || text('deliveryCity').length > 500
          || (values.description ?? '').length > 1000) {
          invalid('Enter a client, pickup city, and delivery city within the allowed field lengths.')
          return
        }
        if (text('pickupCity').toLocaleLowerCase() === text('deliveryCity').toLocaleLowerCase()) {
          invalid('Pickup and delivery cities must be different.')
          return
        }
        if (!Number.isFinite(weightKg) || weightKg <= 0 || !Number.isFinite(volumeM3) || volumeM3 <= 0) {
          invalid('Weight and volume must be finite values greater than zero.')
          return
        }
        if (!revenueText || !Number.isFinite(revenueEur) || revenueEur < 0
          || revenueEur > 9_999_999_999.99
          || !/^\d+(\.\d{1,2})?$/.test(revenueText)) {
          invalid('Freight revenue is required and must be between €0 and €9,999,999,999.99 with at most two decimals.')
          return
        }
        path = id === undefined ? '/goods' : `/goods/${id}`
        payload = {
          clientName: text('clientName'),
          goodsType: values.goodsType,
          description: values.description?.trim() || null,
          weightKg,
          revenueEur,
          volumeM3,
          pickupCity: text('pickupCity'),
          deliveryCity: text('deliveryCity'),
        }
        break
      }
      case 'driver': {
        const licenses = text('licenseCategories').split(',').map((item) => item.trim()).filter(Boolean)
        const allowedLicenses = ['B', 'BE', 'C', 'CE']
        if (!text('name') || text('name').length > 100
          || !text('email') || text('email').length > 254
          || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(text('email'))) {
          invalid('Enter a valid driver name and email address within the allowed field lengths.')
          return
        }
        if (!licenses.length || licenses.some((license) => !allowedLicenses.includes(license))
          || new Set(licenses).size !== licenses.length) {
          invalid('Select one or more valid, non-duplicated license categories: B, BE, C, or CE.')
          return
        }
        path = id === undefined ? '/drivers' : `/drivers/${id}`
        payload = {
          name: text('name'),
          email: text('email'),
          licenseCategories: licenses,
        }
        break
      }
      case 'vehicle': {
        const year = number('year')
        const mileage = number('currentMileageKm')
        const weight = number('maxWeightCapacityKg')
        const volume = number('maxVolumeM3')
        const van = values.vehicleType === 'VAN'
        if (!text('licensePlate') || text('licensePlate').length > 20
          || !text('make') || text('make').length > 100
          || !text('model') || text('model').length > 100) {
          invalid('Enter a license plate, make, and model within the allowed field lengths.')
          return
        }
        if (!Number.isInteger(year) || year < 1900 || year > 2100
          || !Number.isInteger(mileage) || mileage < 0
          || !Number.isFinite(weight) || weight <= 0
          || !Number.isFinite(volume) || volume <= 0) {
          invalid('Enter a valid year (1900–2100), non-negative whole-kilometre odometer, and positive finite capacities.')
          return
        }
        if ((van && !['MANUAL', 'AUTOMATIC'].includes(values.transmissionType ?? ''))
          || (!van && values.transmissionType !== 'AMT')) {
          invalid('Vans require manual or automatic transmission; trucks require AMT.')
          return
        }
        const existingVehicle = id === undefined ? undefined : vehicles.find((item) => item.id === id)
        if (existingVehicle && mileage < existingVehicle.currentMileageKm) {
          invalid('Vehicle mileage cannot be reduced below its recorded odometer.')
          return
        }
        path = id === undefined ? '/vehicles' : `/vehicles/${id}`
        payload = {
          licensePlate: text('licensePlate').toUpperCase(),
          vehicleType: values.vehicleType,
          make: text('make'),
          model: text('model'),
          year,
          maxWeightCapacityKg: weight,
          maxVolumeM3: volume,
          currentMileageKm: mileage,
          transmissionType: values.transmissionType,
        }
        break
      }
      case 'trailer': {
        const year = number('year')
        const weight = number('maxWeightCapacityKg')
        const volume = number('maxVolumeM3')
        if (!text('licensePlate') || text('licensePlate').length > 20
          || !text('make') || text('make').length > 100
          || !text('model') || text('model').length > 100) {
          invalid('Enter a license plate, make, and model within the allowed field lengths.')
          return
        }
        if (!Number.isInteger(year) || year < 1900 || year > 2100
          || !Number.isFinite(weight) || weight <= 0
          || !Number.isFinite(volume) || volume <= 0) {
          invalid('Enter a valid year (1900–2100) and positive finite trailer capacities.')
          return
        }
        path = id === undefined ? '/trailers' : `/trailers/${id}`
        payload = {
          licensePlate: text('licensePlate').toUpperCase(),
          trailerType: values.trailerType,
          compatibleVehicleType: values.compatibleVehicleType,
          make: text('make'),
          model: text('model'),
          year,
          maxWeightCapacityKg: weight,
          maxVolumeM3: volume,
        }
        break
      }
      case 'route': {
        const goodsIdText = text('goodsIds')
        const rawGoodsIds = goodsIdText ? goodsIdText.split(',').map((item) => item.trim()) : []
        const ids = rawGoodsIds.map(Number)
        if (!ids.length || ids.length > 12 || rawGoodsIds.some((goodsId) => !goodsId)
          || ids.some((goodsId) => !Number.isSafeInteger(goodsId) || goodsId <= 0)
          || new Set(ids).size !== ids.length) {
          setError('Select between 1 and 12 unassigned freight items.')
          return
        }
        const routeGoodsIds = new Set(routes.find((route) => route.id === id)?.stops.map((stop) => stop.goodsId) ?? [])
        if (ids.some((goodsId) => !goods.some((item) => item.id === goodsId
          && (item.status === 'UNASSIGNED' || routeGoodsIds.has(goodsId))))) {
          setError('One or more selected freight items are no longer unassigned. Refresh and choose again.')
          return
        }
        const driverId = number('driverId')
        const vehicleId = number('vehicleId')
        const trailerId = values.trailerId ? number('trailerId') : null
        const departure = new Date(values.departureTime ?? '')
        if (!Number.isFinite(departure.getTime()) || departure.getTime() <= Date.now()) {
          invalid('Departure time must be in the future.')
          return
        }
        if (!drivers.some((item) => item.id === driverId && item.status === 'AVAILABLE'
          && !routes.some((route) => route.id !== id && route.driverId === driverId
            && (route.status === 'PLANNED' || route.status === 'ACTIVE')))) {
          setError('Select an available driver.')
          return
        }
        if (!vehicles.some((item) => item.id === vehicleId
          && item.status === 'AVAILABLE' && !item.maintenanceAutoTriggered
          && !routes.some((route) => route.id !== id && route.vehicleId === vehicleId
            && (route.status === 'PLANNED' || route.status === 'ACTIVE')))) {
          setError('Select an available vehicle.')
          return
        }
        const driver = drivers.find((item) => item.id === driverId)
        const vehicle = vehicles.find((item) => item.id === vehicleId)
        if (!driver || !vehicle) {
          invalid('Select an available driver and vehicle.')
          return
        }
        const usesTrailer = trailerId !== null
        const requiredLicense = vehicle.vehicleType === 'VAN'
          ? usesTrailer ? 'BE' : 'B'
          : vehicle.vehicleType === 'RIGID_TRUCK' && !usesTrailer ? 'C' : 'CE'
        if (!driver.licenseCategories.includes(requiredLicense)) {
          invalid(`The selected driver must hold a ${requiredLicense} license for this vehicle and trailer configuration.`)
          return
        }
        if (trailerId !== null && !trailers.some((item) => item.id === trailerId
          && item.status === 'AVAILABLE'
          && !routes.some((route) => route.id !== id && route.trailerId === trailerId
            && (route.status === 'PLANNED' || route.status === 'ACTIVE')))) {
          setError('Select an available trailer or choose No trailer.')
          return
        }
        const cargo = ids.map((goodsId) => goods.find((item) => item.id === goodsId))
        if (cargo.some((item) => item === undefined)) {
          invalid('One or more selected freight items are unavailable. Refresh and choose again.')
          return
        }
        const selectedCargo = cargo.filter((item): item is Goods => item !== undefined)
        const totalWeight = selectedCargo.reduce((total, item) => total + item.weightKg, 0)
        const totalVolume = selectedCargo.reduce((total, item) => total + item.volumeM3, 0)
        const specializedCargo = selectedCargo.some((item) => item.goodsType !== 'GENERAL')
        const vehicleFits = totalWeight <= vehicle.maxWeightCapacityKg && totalVolume <= vehicle.maxVolumeM3
        const trailerRequired = vehicle.vehicleType === 'ARTICULATED_TRUCK' || !vehicleFits || specializedCargo
        if (trailerRequired && trailerId === null) {
          invalid(vehicle.vehicleType === 'ARTICULATED_TRUCK'
            ? 'Select an articulated-truck trailer before planning this route.'
            : 'A compatible trailer is required for this vehicle or freight load.')
          return
        }
        if (trailerId !== null) {
          const trailer = trailers.find((item) => item.id === trailerId)
          if (!trailer || trailer.compatibleVehicleType !== vehicle.vehicleType) {
            invalid('The selected trailer is not compatible with this vehicle.')
            return
          }
          const neededWeight = specializedCargo ? totalWeight : Math.max(0, totalWeight - vehicle.maxWeightCapacityKg)
          const neededVolume = specializedCargo ? totalVolume : Math.max(0, totalVolume - vehicle.maxVolumeM3)
          const supportedTypes: Record<string, GoodsType[]> = {
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
          if (trailer.maxWeightCapacityKg < neededWeight || trailer.maxVolumeM3 < neededVolume
            || selectedCargo.some((item) => !supportedTypes[trailer.trailerType]?.includes(item.goodsType))) {
            invalid('The selected trailer cannot carry the selected freight within its capacity or category limits.')
            return
          }
        }
        path = id === undefined ? '/routes' : `/routes/${id}`
        payload = {
          driverId,
          vehicleId,
          trailerId,
          departureTime: departure.toISOString(),
          stops: [
            ...ids.map((goodsId) => ({ goodsId, type: 'PICKUP' })),
            ...ids.map((goodsId) => ({ goodsId, type: 'DELIVERY' })),
          ],
        }
        break
      }
      case 'maintenance':
        if (!modal.vehicle || !modal.component) {
          invalid('The selected maintenance component is unavailable. Refresh and try again.')
          return
        }
        {
          const performedAt = new Date(values.performedAt ?? '')
          const odometerKm = number('odometerKm')
          const costText = text('costEur')
          const costEur = costText ? Number(costText) : null
          if (!Number.isFinite(performedAt.getTime()) || performedAt.getTime() > Date.now()
            || !Number.isInteger(odometerKm) || odometerKm < 0 || odometerKm > modal.vehicle.currentMileageKm
            || (costEur !== null && (!Number.isFinite(costEur) || costEur < 0
              || costEur > 9_999_999_999.99
              || !/^\d+(\.\d{1,2})?$/.test(costText)))) {
            invalid('Enter a past or current service date and valid odometer, plus an optional cost from €0 to €9,999,999,999.99 with at most two decimals.')
            return
          }
          path = `/vehicles/${modal.vehicle.id}/maintenance`
          method = 'POST'
          payload = {
            componentKey: modal.component.key,
            performedAt: performedAt.toISOString(),
            odometerKm,
            costEur,
          }
        }
        break
      case 'trailerMaintenance':
        if (!modal.trailer || !modal.trailerComponent) {
          invalid('The selected trailer maintenance component is unavailable. Refresh and try again.')
          return
        }
        {
          const performedAt = new Date(values.performedAt ?? '')
          const costText = text('costEur')
          const costEur = costText ? Number(costText) : null
          if (!Number.isFinite(performedAt.getTime()) || performedAt.getTime() > Date.now()
            || (costEur !== null && (!Number.isFinite(costEur) || costEur < 0
              || costEur > 9_999_999_999.99
              || !/^\d+(\.\d{1,2})?$/.test(costText)))) {
            invalid('Enter a past or current service date and an optional cost from €0 to €9,999,999,999.99 with at most two decimals.')
            return
          }
          path = `/trailers/${modal.trailer.id}/maintenance`
          method = 'POST'
          payload = {
            componentKey: modal.trailerComponent.key,
            performedAt: performedAt.toISOString(),
            costEur,
          }
        }
        break
    }

    await mutate(() => send(path, method, payload), `${modal.kind[0]?.toUpperCase()}${modal.kind.slice(1)} ${id === undefined ? 'added' : 'updated'}.`)
  }

  function openMaintenance(vehicle: Vehicle, component: VehicleMaintenanceComponent) {
    openForm('maintenance', {
      performedAt: dateTimeInputValue(new Date()),
      odometerKm: vehicle.currentMileageKm,
      costEur: '',
    }, { vehicle, component })
  }

  function openTrailerMaintenance(trailer: Trailer, component: TrailerMaintenanceComponent) {
    openForm('trailerMaintenance', {
      performedAt: dateTimeInputValue(new Date()),
      costEur: '',
    }, { trailer, trailerComponent: component })
  }

  async function sendAssistantMessage() {
    const content = chatInput.trim()
    if (!content || chatBusy) return
    const history: AssistantMessage[] = [...chat, { role: 'user' as const, content }]
      .slice(-24)
      .map(({ role, content: text }) => ({ role, content: text }))
    setChat([...chat, { role: 'user', content }])
    setChatInput('')
    setChatBusy(true)
    setError('')
    try {
      const context = { drivers, vehicles, trailers, goods, routes }
      if (new TextEncoder().encode(JSON.stringify(context)).byteLength > MAX_OPERATIONAL_CONTEXT_BYTES) {
        throw new Error('Operational context must be a JSON object no larger than 150 KB.')
      }
      const reply = await send<AssistantReply>('/assistant/chat', 'POST', {
        messages: history,
        context,
      })
      setChat((current) => [...current, { role: 'assistant', content: reply.message, ...(reply.action ? { action: reply.action } : {}) }])
      setPendingAction(reply.action)
    } catch (chatError) {
      setError(chatError instanceof Error ? chatError.message : 'The assistant request failed.')
    } finally {
      setChatBusy(false)
    }
  }

  async function confirmAssistantAction() {
    if (!pendingAction) return
    const action = pendingAction
    const payload = action.payload
    const id = action.id
    const requireId = () => {
      if (id === null || !Number.isSafeInteger(id) || id <= 0) throw new Error('The proposed change is missing a valid record ID.')
      return id
    }
    const pathFor = (resource: string) => `/${resource}/${requireId()}`
    setChatBusy(true)
    setError('')
    try {
      switch (action.type) {
        case 'CREATE_DRIVER': await send('/drivers', 'POST', payload); break
        case 'UPDATE_DRIVER': await send(pathFor('drivers'), 'PUT', payload); break
        case 'DELETE_DRIVER': await send(pathFor('drivers'), 'DELETE'); break
        case 'SET_DRIVER_STATUS': await send(`${pathFor('drivers')}/status`, 'PATCH', payload); break
        case 'CREATE_VEHICLE': await send('/vehicles', 'POST', payload); break
        case 'UPDATE_VEHICLE': await send(pathFor('vehicles'), 'PUT', payload); break
        case 'DELETE_VEHICLE': await send(pathFor('vehicles'), 'DELETE'); break
        case 'SET_VEHICLE_STATUS': await send(`${pathFor('vehicles')}/status`, 'PATCH', payload); break
        case 'RECORD_MAINTENANCE': await send(`${pathFor('vehicles')}/maintenance`, 'POST', payload); break
        case 'CREATE_TRAILER': await send('/trailers', 'POST', payload); break
        case 'UPDATE_TRAILER': await send(pathFor('trailers'), 'PUT', payload); break
        case 'DELETE_TRAILER': await send(pathFor('trailers'), 'DELETE'); break
        case 'SET_TRAILER_STATUS': await send(`${pathFor('trailers')}/status`, 'PATCH', payload); break
        case 'CREATE_FREIGHT': await send('/goods', 'POST', payload); break
        case 'UPDATE_FREIGHT': await send(pathFor('goods'), 'PUT', payload); break
        case 'DELETE_FREIGHT': await send(pathFor('goods'), 'DELETE'); break
        case 'CREATE_ROUTE': await send('/routes', 'POST', payload); break
        case 'DELETE_ROUTE': await send(pathFor('routes'), 'DELETE'); break
        case 'UPDATE_ROUTE_STATUS': await send(`${pathFor('routes')}/status`, 'PATCH', payload); break
        case 'DELIVER_FREIGHT': {
          const goodsId = Number(payload.goodsId)
          if (!Number.isSafeInteger(goodsId) || goodsId <= 0) throw new Error('The delivery proposal is missing a valid freight ID.')
          await send(`${pathFor('routes')}/goods/${goodsId}/deliver`, 'PATCH')
          break
        }
        case 'CREATE_FREIGHT_AND_ROUTE': {
          const freight = payload.freight
          const route = payload.route
          if (!freight || typeof freight !== 'object' || !route || typeof route !== 'object') {
            throw new Error('The proposed change is missing its freight or route details.')
          }
          const created = await send<Goods>('/goods', 'POST', freight)
          const routePayload = route as Record<string, unknown>
          if (!Array.isArray(routePayload.stops)) throw new Error('The proposed route must include ordered stops.')
          const stops = routePayload.stops.map((stop) => {
            if (!stop || typeof stop !== 'object') throw new Error('The proposed route contains an invalid stop.')
            const item = stop as { goodsId?: unknown; type?: unknown }
            if ((item.type !== 'PICKUP' && item.type !== 'DELIVERY') || typeof item.goodsId !== 'number') {
              throw new Error('The proposed route contains an invalid stop.')
            }
            return { goodsId: item.goodsId === 0 ? created.id : item.goodsId, type: item.type }
          })
          try {
            await send('/routes', 'POST', { ...routePayload, stops })
          } catch (routeError) {
            await fetchData()
            throw new Error(`Freight ${created.trackingCode} was created, but route planning failed: ${routeError instanceof Error ? routeError.message : 'request failed'}. The freight remains unassigned.`)
          }
          break
        }
        default: throw new Error('This proposed operation is not supported.')
      }
      setPendingAction(null)
      setChat((current) => [...current, { role: 'assistant', content: 'Confirmed change completed successfully.' }])
      await fetchData()
    } catch (actionError) {
      setError(actionError instanceof Error ? actionError.message : 'Could not apply the proposed change.')
    } finally {
      setChatBusy(false)
    }
  }

  const filtered = (text: string) => text.toLowerCase().includes(search.trim().toLowerCase())
  const pageTitle = tab === 'Service' ? 'Maintenance & diagnostics' : tab === 'Home' ? 'Operations overview' : tab

  return (
    <SafeAreaView style={styles.app}>
      <StatusBar style="light" />
      <View style={styles.topbar}>
        <View style={styles.brandRow}>
          <View style={styles.brandMark}><Text style={styles.brandMarkText}>HM</Text></View>
          <View><Text style={styles.brandName}>HAULMASTER</Text><Text style={styles.brandSubtitle}>MOBILE DISPATCH</Text></View>
        </View>
        <Pressable onPress={() => void refresh()} disabled={loading} style={styles.refreshButton} accessibilityLabel="Refresh data">
          <Text style={styles.refreshText}>{loading ? '…' : '↻'}</Text>
        </Pressable>
      </View>

      <View style={styles.pageTitle}><Text style={styles.eyebrow}>OPERATIONS / {tab.toUpperCase()}</Text><Text style={styles.title}>{pageTitle}</Text></View>
      {(error || notice) ? (
        <Pressable style={[styles.message, error ? styles.errorMessage : styles.successMessage]} onPress={() => { setError(''); setNotice('') }}>
          <Text style={styles.messageText}>{error || notice}</Text>
        </Pressable>
      ) : null}

      <ScrollView
        key={tab}
        style={styles.content}
        contentContainerStyle={styles.contentInner}
        refreshControl={<RefreshControl refreshing={loading} onRefresh={() => void refresh()} tintColor={ACCENT} />}
        keyboardShouldPersistTaps="handled"
      >
        {tab === 'Home' && (
          <>
            <View style={styles.statsGrid}>
              <Stat label="Active routes" value={counts.activeRoutes} tone="purple" />
              <Stat label="Drivers ready" value={counts.availableDrivers} tone="green" />
              <Stat label="Vehicles ready" value={counts.availableVehicles} tone="blue" />
              <Stat label="Unassigned freight" value={counts.unassignedGoods} tone="orange" />
            </View>
            <Card title="Recent routes" subtitle="Latest dispatches and delivery progress" action={<LinkButton label="All routes" onPress={() => setTab('Routes')} />}>
              {!routes.length ? <Empty>No routes yet. Plan a route when freight is ready.</Empty> : routes.slice(0, 4).map((route) => <RouteCard key={route.id} route={route} goods={goods} onAction={handleRouteAction} />)}
            </Card>
            <Card title="Quick actions">
              <View style={styles.actionGrid}>
                <Button label="+ Freight" onPress={() => { setTab('Freight'); openForm('freight', { goodsType: 'GENERAL', weightKg: '1', volumeM3: '1' }) }} />
                <Button label="+ Driver" onPress={() => { setTab('Drivers'); openForm('driver', { licenseCategories: 'B' }) }} secondary />
                <Button label="+ Vehicle" onPress={() => { setTab('Fleet'); openForm('vehicle', { vehicleType: 'VAN', transmissionType: 'MANUAL', year: new Date().getFullYear(), maxWeightCapacityKg: 1000, maxVolumeM3: 10, currentMileageKm: 0 }) }} secondary />
              </View>
            </Card>
          </>
        )}

        {tab === 'Routes' && (
          <>
            <View style={styles.headingRow}><Text style={styles.sectionIntro}>Saved routes, stop order, and route-map simulation.</Text><Button label="+ Plan" onPress={() => openForm('route', { departureTime: dateTimeInputValue(new Date(Date.now() + 60_000)), goodsIds: '' })} /></View>
            <Card title="Active route map" subtitle="Positions are simulated from saved road geometry, not GPS.">
              {routes.filter((route) => route.status === 'ACTIVE').length === 0
                ? <Empty>No active routes.</Empty>
                : routes.filter((route) => route.status === 'ACTIVE').map((route) => <RouteMapCard key={route.id} route={route} position={simulationPositions[route.id]} />)}
            </Card>
            {routes.map((route) => <RouteCard key={route.id} route={route} goods={goods} onAction={handleRouteAction} onEdit={editRoute} expanded />)}
          </>
        )}

        {tab === 'Freight' && (
          <>
            <View style={styles.headingRow}><Text style={styles.sectionIntro}>{goods.length} shipment records</Text><Button label="+ Add" onPress={() => openForm('freight', { goodsType: 'GENERAL', weightKg: '1', volumeM3: '1' })} /></View>
            <SearchInput value={search} onChangeText={setSearch} placeholder="Search freight, client, or city" />
            {goods.filter((item) => filtered(`${item.trackingCode} ${item.clientName} ${item.pickupCity} ${item.deliveryCity} ${item.status}`)).map((item) => (
              <Card key={item.id} title={`${item.trackingCode} · ${item.clientName}`} trailing={<Badge value={item.status} />}>
                <Text style={styles.bodyText}>{item.pickupCity} → {item.deliveryCity}</Text>
                <Text style={styles.mutedText}>{displayStatus(item.goodsType)} · {item.weightKg.toLocaleString()} kg · {item.volumeM3} m³ · {money(item.revenueEur)}</Text>
                {item.status === 'UNASSIGNED' && !hasGoodsHistory(item.id) ? (
                  <View style={styles.rowActions}>
                    <LinkButton label="Edit" onPress={() => openForm('freight', item, { editingId: item.id })} />
                    <LinkButton label="Delete" danger onPress={() => confirm('Delete freight?', `Delete ${item.trackingCode}?`, () => void mutate(() => send(`/goods/${item.id}`, 'DELETE'), 'Freight deleted.'))} />
                  </View>
                ) : null}
              </Card>
            ))}
            {!goods.length && <Empty>No freight records yet.</Empty>}
          </>
        )}

        {tab === 'Drivers' && (
          <>
            <View style={styles.headingRow}><Text style={styles.sectionIntro}>{drivers.length} driver records</Text><Button label="+ Add" onPress={() => openForm('driver', { licenseCategories: 'B' })} /></View>
            <SearchInput value={search} onChangeText={setSearch} placeholder="Search drivers" />
            {drivers.filter((driver) => filtered(`${driver.name} ${driver.email} ${driver.status}`)).map((driver) => (
              <Card key={driver.id} title={driver.name} trailing={<Badge value={driver.status} />}>
                <Text style={styles.bodyText}>{driver.email}</Text><Text style={styles.mutedText}>Licenses: {driver.licenseCategories.join(', ') || '—'}</Text>
                <View style={styles.rowActions}>
                  {!hasScheduledDriverRoute(driver.id) && driver.status !== 'IN_TRANSIT' && driver.status !== 'RETURNING'
                    ? <LinkButton label="Edit" onPress={() => openForm('driver', driver, { editingId: driver.id })} />
                    : null}
                  {!hasScheduledDriverRoute(driver.id) && (driver.status === 'AVAILABLE' || driver.status === 'ON_LEAVE')
                    ? <LinkButton label={driver.status === 'ON_LEAVE' ? 'Set available' : 'Set on leave'} onPress={() => void mutate(() => send(`/drivers/${driver.id}/status`, 'PATCH', { status: driver.status === 'ON_LEAVE' ? 'AVAILABLE' : 'ON_LEAVE' }), 'Driver status updated.')} />
                    : null}
                  {!hasDriverHistory(driver.id) && driver.status !== 'IN_TRANSIT' && driver.status !== 'RETURNING'
                    ? <LinkButton label="Delete" danger onPress={() => confirm('Delete driver?', `Delete ${driver.name}?`, () => void mutate(() => send(`/drivers/${driver.id}`, 'DELETE'), 'Driver deleted.'))} />
                    : null}
                </View>
              </Card>
            ))}
            {!drivers.length && <Empty>No drivers yet.</Empty>}
          </>
        )}

        {tab === 'Fleet' && (
          <>
            <View style={styles.actionGrid}>
              <Button label="+ Vehicle" onPress={() => openForm('vehicle', { vehicleType: 'VAN', transmissionType: 'MANUAL', year: new Date().getFullYear(), maxWeightCapacityKg: 1000, maxVolumeM3: 10, currentMileageKm: 0 })} />
              <Button label="+ Trailer" onPress={() => openForm('trailer', { trailerType: 'BOX_TRAILER', compatibleVehicleType: 'VAN', year: new Date().getFullYear(), maxWeightCapacityKg: 1000, maxVolumeM3: 10 })} secondary />
            </View>
            <SearchInput value={search} onChangeText={setSearch} placeholder="Search plates, make, or model" />
            <Text style={styles.subheading}>Vehicles ({vehicles.length})</Text>
            {vehicles.filter((vehicle) => filtered(`${vehicle.licensePlate} ${vehicle.make} ${vehicle.model} ${vehicle.status}`)).map((vehicle) => (
              <Card key={vehicle.id} title={`${vehicle.licensePlate} · ${vehicle.make} ${vehicle.model}`} trailing={<Badge value={vehicle.status} />}>
                <Text style={styles.mutedText}>{displayStatus(vehicle.vehicleType)} · {vehicle.year} · {vehicle.currentMileageKm.toLocaleString()} km · {vehicle.maxWeightCapacityKg.toLocaleString()} kg</Text>
                {vehicle.maintenanceAutoTriggered ? <Text style={styles.warningText}>Automatically held for maintenance.</Text> : null}
                <View style={styles.rowActions}>
                  {!hasScheduledVehicleRoute(vehicle.id) && vehicle.status !== 'IN_TRANSIT'
                    ? <LinkButton label="Edit" onPress={() => openForm('vehicle', vehicle, { editingId: vehicle.id })} />
                    : null}
                  {!hasScheduledVehicleRoute(vehicle.id) && (vehicle.status === 'AVAILABLE' || vehicle.status === 'MAINTENANCE')
                    ? <LinkButton label={vehicle.status === 'MAINTENANCE' ? 'Set available' : 'Maintenance'} onPress={() => {
                      if (vehicle.status === 'MAINTENANCE') {
                        confirm('Mark available?', 'Tracked service health will be reset at the current mileage.', () => void mutate(() => send(`/vehicles/${vehicle.id}/status`, 'PATCH', { status: 'AVAILABLE' }), 'Vehicle marked available.'))
                      } else {
                        void mutate(() => send(`/vehicles/${vehicle.id}/status`, 'PATCH', { status: 'MAINTENANCE' }), 'Vehicle status updated.')
                      }
                    }} />
                    : null}
                  {!hasVehicleHistory(vehicle.id) && vehicle.status !== 'IN_TRANSIT'
                    ? <LinkButton label="Delete" danger onPress={() => confirm('Delete vehicle?', `Delete ${vehicle.licensePlate}?`, () => void mutate(() => send(`/vehicles/${vehicle.id}`, 'DELETE'), 'Vehicle deleted.'))} />
                    : null}
                </View>
              </Card>
            ))}
            <Text style={styles.subheading}>Trailers ({trailers.length})</Text>
            {trailers.filter((trailer) => filtered(`${trailer.licensePlate} ${trailer.make} ${trailer.model} ${trailer.status}`)).map((trailer) => (
              <Card key={trailer.id} title={`${trailer.licensePlate} · ${trailer.make} ${trailer.model}`} trailing={<Badge value={trailer.status} />}>
                <Text style={styles.mutedText}>{displayStatus(trailer.trailerType)} · compatible with {displayStatus(trailer.compatibleVehicleType)}</Text>
                <View style={styles.rowActions}>
                  {!hasScheduledTrailerRoute(trailer.id) && trailer.status !== 'IN_TRANSIT'
                    ? <LinkButton label="Edit" onPress={() => openForm('trailer', trailer, { editingId: trailer.id })} />
                    : null}
                  {!hasScheduledTrailerRoute(trailer.id) && (trailer.status === 'AVAILABLE' || trailer.status === 'MAINTENANCE')
                    ? <LinkButton label={trailer.status === 'MAINTENANCE' ? 'Set available' : 'Maintenance'} onPress={() => void mutate(() => send(`/trailers/${trailer.id}/status`, 'PATCH', { status: trailer.status === 'MAINTENANCE' ? 'AVAILABLE' : 'MAINTENANCE' }), 'Trailer status updated.')} />
                    : null}
                  {!hasTrailerHistory(trailer.id) && trailer.status !== 'IN_TRANSIT'
                    ? <LinkButton label="Delete" danger onPress={() => confirm('Delete trailer?', `Delete ${trailer.licensePlate}?`, () => void mutate(() => send(`/trailers/${trailer.id}`, 'DELETE'), 'Trailer deleted.'))} />
                    : null}
                  <LinkButton label="Service" onPress={() => setTab('Service')} />
                </View>
              </Card>
            ))}
          </>
        )}

        {tab === 'Service' && (
          <>
            <Card title="Workshop queue" subtitle={`${counts.dueVehicles} vehicle(s) currently in maintenance`}>
              {vehicles.filter((vehicle) => vehicle.status === 'MAINTENANCE').length === 0
                ? <Empty>No vehicles currently due.</Empty>
                : vehicles.filter((vehicle) => vehicle.status === 'MAINTENANCE').map((vehicle) => (
                  <Text key={vehicle.id} style={styles.bodyText}>{vehicle.licensePlate} · {vehicle.make} {vehicle.model}</Text>
                ))}
            </Card>
            {vehicles.map((vehicle) => (
              <Card key={vehicle.id} title={`${vehicle.licensePlate} · ${vehicle.make} ${vehicle.model}`} trailing={<Badge value={vehicle.status} />}>
                {vehicle.maintenanceComponents.map((component) => (
                  <View key={component.key} style={styles.componentRow}>
                    <Pressable
                      style={styles.componentHeading}
                      onPress={() => setExpandedHealth((current) => ({ ...current, [`vehicle-${vehicle.id}-${component.key}`]: !current[`vehicle-${vehicle.id}-${component.key}`] }))}
                      accessibilityRole="button"
                      accessibilityState={{ expanded: Boolean(expandedHealth[`vehicle-${vehicle.id}-${component.key}`]) }}
                    >
                      <Text style={styles.bodyText}>{component.label}</Text>
                      <Text style={styles.mutedText}>{expandedHealth[`vehicle-${vehicle.id}-${component.key}`] ? '⌄' : '›'}</Text>
                    </Pressable>
                    {expandedHealth[`vehicle-${vehicle.id}-${component.key}`] ? (
                      <>
                        <Text style={styles.mutedText}>{component.healthPercent == null ? component.inspectionNote ?? 'No fixed interval' : `${Math.round(component.healthPercent)}% health`}</Text>
                        {component.healthPercent != null ? <View style={styles.healthTrack}><View style={[styles.healthBar, { width: `${Math.max(0, Math.min(100, component.healthPercent))}%` }]} /></View> : null}
                        <Text style={styles.mutedText}>{component.distanceRemainingKm == null ? '' : `${component.distanceRemainingKm.toLocaleString()} km remaining`}{component.daysRemaining == null ? '' : ` · ${component.daysRemaining} days`}</Text>
                        {component.lastPerformedAt ? <Text style={styles.mutedText}>Last service: {dateString(component.lastPerformedAt)}{component.lastCostEur == null ? '' : ` · ${money(component.lastCostEur)}`}</Text> : <Text style={styles.mutedText}>No service history recorded.</Text>}
                        <LinkButton label="Record service" disabled={hasScheduledVehicleRoute(vehicle.id)} onPress={() => openMaintenance(vehicle, component)} />
                      </>
                    ) : null}
                  </View>
                ))}
                {vehicle.status === 'MAINTENANCE' && <Button label="Mark vehicle available" secondary onPress={() => confirm('Mark available?', 'Tracked service health will be reset at the current mileage.', () => void mutate(() => send(`/vehicles/${vehicle.id}/status`, 'PATCH', { status: 'AVAILABLE' }), 'Vehicle marked available.'))} />}
              </Card>
            ))}
            <Text style={styles.subheading}>Trailer service ({trailers.length})</Text>
            {trailers.map((trailer) => (
              <Card key={`service-${trailer.id}`} title={`${trailer.licensePlate} · ${trailer.make} ${trailer.model}`} trailing={<Badge value={trailer.status} />}>
                {trailer.maintenanceComponents.map((component) => {
                  const key = `trailer-${trailer.id}-${component.key}`
                  const expanded = Boolean(expandedHealth[key])
                  return (
                    <View key={component.key} style={styles.componentRow}>
                      <Pressable
                        style={styles.componentHeading}
                        onPress={() => setExpandedHealth((current) => ({ ...current, [key]: !current[key] }))}
                        accessibilityRole="button"
                        accessibilityState={{ expanded }}
                      >
                        <Text style={styles.bodyText}>{component.label}</Text>
                        <Text style={styles.mutedText}>{expanded ? '⌄' : '›'}</Text>
                      </Pressable>
                      {expanded ? (
                        <>
                          <Text style={styles.mutedText}>{component.healthPercent == null ? 'No service history' : `${Math.round(component.healthPercent)}% health · ${component.daysRemaining ?? 0} days remaining`}</Text>
                          {component.healthPercent != null ? <View style={styles.healthTrack}><View style={[styles.healthBar, { width: `${Math.max(0, Math.min(100, component.healthPercent))}%` }]} /></View> : null}
                          <Text style={styles.mutedText}>Annual service interval{component.lastPerformedAt ? ` · Last service ${dateString(component.lastPerformedAt)}` : ''}{component.lastCostEur == null ? '' : ` · ${money(component.lastCostEur)}`}</Text>
                          <LinkButton label="Record trailer service" disabled={hasScheduledTrailerRoute(trailer.id)} onPress={() => openTrailerMaintenance(trailer, component)} />
                        </>
                      ) : null}
                    </View>
                  )
                })}
                {trailer.maintenanceHistory.length
                  ? <Text style={styles.mutedText}>Recent service: {trailer.maintenanceHistory.slice(0, 3).map((operation) => `${operation.componentLabel} · ${dateString(operation.performedAt)}`).join(' | ')}</Text>
                  : <Text style={styles.mutedText}>No trailer service history recorded.</Text>}
                {trailer.status === 'MAINTENANCE'
                  ? <Button label="Mark trailer available" secondary onPress={() => confirm('Mark available?', 'Confirm trailer service is complete?', () => void mutate(() => send(`/trailers/${trailer.id}/status`, 'PATCH', { status: 'AVAILABLE' }), 'Trailer marked available.'))} />
                  : null}
              </Card>
            ))}
            {!vehicles.length && <Empty>No vehicle maintenance data.</Empty>}
          </>
        )}

        {tab === 'Finance' && (
          <>
            <Card title="Reporting period" subtitle="Revenue less recorded maintenance costs. Other operating costs are excluded.">
              <View style={styles.dateFields}>
                <DatePickerField label="From" value={fromDate} onChange={setFromDate} />
                <DatePickerField label="To" value={toDate} onChange={setToDate} />
              </View>
              <Button label="Update report" onPress={() => void refresh()} />
            </Card>
            {!finance ? <Empty>Finance report is not available.</Empty> : (
              <>
                <View style={styles.statsGrid}>
                  <Stat label="Freight revenue" value={money(finance.freightRevenueEur)} tone="green" />
                  <Stat label="Maintenance" value={money(finance.maintenanceCostsEur)} tone="orange" />
                  <Stat label="Net result" value={money(finance.netProfitEur)} tone={finance.netProfitEur < 0 ? 'orange' : 'purple'} />
                  <Stat label="Delivered" value={finance.deliveredFreightCount} tone="blue" />
                </View>
                {(finance.unpricedFreightCount > 0 || finance.uncostedMaintenanceCount > 0) ? <Text style={styles.warningText}>{finance.unpricedFreightCount} unpriced freight item(s); {finance.uncostedMaintenanceCount} maintenance operation(s) without a recorded cost.</Text> : null}
                <Card title="Revenue and maintenance trend"><Empty>{finance.trend.length ? '' : 'No trend data in this period.'}</Empty>{finance.trend.map((row) => <DataRow key={row.period} label={row.period} value={`${money(row.revenueEur)} revenue · ${money(row.maintenanceCostsEur)} service`} />)}</Card>
                <Card title="Drivers"><Empty>{finance.drivers.length ? '' : 'No driver results in this period.'}</Empty>{finance.drivers.map((row) => <DataRow key={row.driverId} label={row.driverName} value={`${money(row.grossContributionEur)} · ${row.deliveredFreightCount} delivered`} />)}</Card>
                <Card title="Vehicles"><Empty>{finance.vehicles.length ? '' : 'No vehicle results in this period.'}</Empty>{finance.vehicles.map((row) => <DataRow key={row.vehicleId} label={row.licensePlate} value={`${money(row.netProfitEur)} net`} />)}</Card>
                <Card title="Maintenance by category"><Empty>{finance.maintenanceByCategory.length ? '' : 'No maintenance cost data.'}</Empty>{finance.maintenanceByCategory.map((row) => <DataRow key={row.category} label={row.category} value={`${money(row.costEur)} · ${row.operationCount} operations`} />)}</Card>
                <Card title="Freight by category"><Empty>{finance.freightByCategory.length ? '' : 'No freight revenue data.'}</Empty>{finance.freightByCategory.map((row) => <DataRow key={row.category} label={row.category} value={`${money(row.revenueEur)} · ${row.shipmentCount} shipments`} />)}</Card>
                <Card title="Freight margins"><Empty>{finance.freightMargins.length ? '' : 'No freight margin records.'}</Empty>{finance.freightMargins.map((row) => <DataRow key={row.freightId} label={`${row.trackingCode} · ${row.clientName}`} value={money(row.estimatedMarginEur)} />)}</Card>
              </>
            )}
          </>
        )}

      </ScrollView>

      <View style={styles.tabBar}>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.tabList}>
          {TABS.map((item) => (
            <Pressable key={item} onPress={() => setTab(item)} style={[styles.tabItem, tab === item && styles.selectedTab]}>
              <Text style={[styles.tabText, tab === item && styles.selectedTabText]}>{item}</Text>
              {item === 'Routes' && counts.activeRoutes > 0 ? <Text style={styles.tabCount}>{counts.activeRoutes}</Text> : null}
              {item === 'Service' && counts.dueVehicles > 0 ? <Text style={styles.tabCount}>{counts.dueVehicles}</Text> : null}
            </Pressable>
          ))}
        </ScrollView>
      </View>

      <Pressable
        onPress={() => setAssistantOpen(true)}
        style={styles.assistantFab}
        accessibilityLabel="Open operations assistant"
        accessibilityRole="button"
      >
        <Text style={styles.assistantFabText}>AI</Text>
      </Pressable>

      <Modal visible={assistantOpen} transparent animationType="slide" onRequestClose={() => setAssistantOpen(false)}>
        <View style={styles.assistantScrim}>
          <Pressable style={StyleSheet.absoluteFill} onPress={() => setAssistantOpen(false)} accessibilityLabel="Close assistant" />
          <KeyboardAvoidingView style={styles.assistantKeyboard} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
            <View style={styles.assistantPanel}>
              <View style={styles.assistantHeader}>
                <View style={styles.flex}>
                  <Text style={styles.cardTitle}>AI operations assistant</Text>
                  <Text style={styles.mutedText}>Changes require your confirmation.</Text>
                </View>
                <Pressable onPress={() => setAssistantOpen(false)} accessibilityLabel="Close assistant">
                  <Text style={styles.closeText}>×</Text>
                </Pressable>
              </View>
              <Text style={styles.warningText}>Prompts and operational data are sent to the backend&apos;s configured Ollama service.</Text>
              <ScrollView style={styles.assistantMessages} contentContainerStyle={styles.chatList} keyboardShouldPersistTaps="handled">
                {!chat.length && <Empty>Ask about routes, freight, drivers, vehicles, or maintenance.</Empty>}
                {chat.map((message, index) => (
                  <View key={`${message.role}-${index}`} style={[styles.chatBubble, message.role === 'user' ? styles.userBubble : styles.assistantBubble]}>
                    <Text style={styles.bodyText}>{message.content}</Text>
                    {message.action ? <View style={styles.actionProposal}><Text style={styles.proposalTitle}>{message.action.summary}</Text><Button label="Confirm proposed change" disabled={chatBusy} onPress={() => void confirmAssistantAction()} /></View> : null}
                  </View>
                ))}
              </ScrollView>
              {pendingAction ? <Button label="Confirm latest proposal" disabled={chatBusy} onPress={() => void confirmAssistantAction()} /> : null}
              <View style={styles.chatComposer}>
                <TextInput value={chatInput} onChangeText={setChatInput} placeholder="Ask the operations assistant…" placeholderTextColor="#6b7280" style={styles.chatInput} multiline />
                <Button label={chatBusy ? '…' : 'Send'} disabled={chatBusy || !chatInput.trim()} onPress={() => void sendAssistantMessage()} />
              </View>
            </View>
          </KeyboardAvoidingView>
        </View>
      </Modal>

      {modal ? (
        <FormEditor modal={modal} busy={busy} error={error} onChange={updateField} onClose={() => setModal(null)} onSave={() => void saveForm()} drivers={drivers} vehicles={vehicles} trailers={trailers} goods={goods} routes={routes} />
      ) : null}
    </SafeAreaView>
  )

  function handleRouteAction(route: Route, action: 'start' | 'cancel' | 'delete' | 'deliver', goodsId?: number) {
    if (action === 'start') {
      void mutate(() => send(`/routes/${route.id}/status`, 'PATCH', { status: 'ACTIVE' }), 'Route started.')
    } else if (action === 'cancel') {
      confirm('Cancel route?', `Cancel route #${route.id}?`, () => void mutate(() => send(`/routes/${route.id}/status`, 'PATCH', { status: 'CANCELLED' }), 'Route cancelled.'))
    } else if (action === 'delete') {
      confirm('Delete route?', `Delete route #${route.id}?`, () => void mutate(() => send(`/routes/${route.id}`, 'DELETE'), 'Route deleted.'))
    } else if (goodsId !== undefined) {
      confirm('Confirm delivery?', `Mark freight ${goods.find((item) => item.id === goodsId)?.trackingCode ?? `#${goodsId}`} delivered?`, () => void mutate(() => send(`/routes/${route.id}/goods/${goodsId}/deliver`, 'PATCH'), 'Delivery confirmed.'))
    }
  }
}

export default function App() {
  return <SafeAreaProvider><MobileApp /></SafeAreaProvider>
}

function Stat({ label, value, tone }: { label: string; value: string | number; tone: string }) {
  return <View style={styles.statCard}><Text style={styles.statLabel}>{label}</Text><Text style={[styles.statValue, tone === 'green' ? styles.greenText : tone === 'blue' ? styles.blueText : tone === 'orange' ? styles.orangeText : styles.purpleText]}>{value}</Text></View>
}

function Card({ title, subtitle, trailing, action, children }: { title: string; subtitle?: string; trailing?: React.ReactNode; action?: React.ReactNode; children: React.ReactNode }) {
  return (
    <View style={styles.card}>
      <View style={styles.cardHeading}><View style={styles.flex}><Text style={styles.cardTitle}>{title}</Text>{subtitle ? <Text style={styles.mutedText}>{subtitle}</Text> : null}</View>{trailing ?? action}</View>
      {children}
    </View>
  )
}

function Empty({ children }: { children: string }) {
  return children ? <Text style={styles.emptyText}>{children}</Text> : null
}

function Badge({ value }: { value: string }) {
  return <View style={[styles.badge, value === 'AVAILABLE' || value === 'UNASSIGNED' ? styles.badgeGreen : value === 'ACTIVE' || value === 'IN_TRANSIT' ? styles.badgeBlue : value === 'MAINTENANCE' || value === 'CANCELLED' ? styles.badgeRed : styles.badgeGray]}><Text style={styles.badgeText}>{displayStatus(value)}</Text></View>
}

function Button({ label, onPress, secondary, danger, disabled }: { label: string; onPress: () => void; secondary?: boolean; danger?: boolean; disabled?: boolean }) {
  return <Pressable onPress={onPress} disabled={disabled} style={[styles.button, secondary && styles.secondaryButton, danger && styles.dangerButton, disabled && styles.disabledButton]}><Text style={[styles.buttonText, secondary && styles.secondaryButtonText, danger && styles.dangerButtonText]}>{label}</Text></Pressable>
}

function LinkButton({ label, onPress, danger, disabled }: { label: string; onPress: () => void; danger?: boolean; disabled?: boolean }) {
  return <Pressable onPress={onPress} disabled={disabled} style={[styles.linkButton, disabled && styles.disabledButton]}><Text style={[styles.linkText, danger && styles.dangerLink]}>{label}</Text></Pressable>
}

function SearchInput({ value, onChangeText, placeholder }: { value: string; onChangeText: (value: string) => void; placeholder: string }) {
  return <TextInput value={value} onChangeText={onChangeText} placeholder={placeholder} style={styles.searchInput} autoCapitalize="none" />
}

function Field({ label, value, onChangeText, keyboardType }: { label: string; value: string; onChangeText: (value: string) => void; keyboardType?: 'default' | 'email-address' | 'numeric' | 'decimal-pad' }) {
  return <View style={styles.field}><Text style={styles.fieldLabel}>{label}</Text><TextInput value={value} onChangeText={onChangeText} style={styles.fieldInput} keyboardType={keyboardType ?? 'default'} autoCapitalize={keyboardType === 'email-address' ? 'none' : 'sentences'} /></View>
}

function ChoiceField({
  label,
  options,
  selectedValues,
  onChange,
  multiple = false,
  maxSelections,
  emptyLabel,
}: {
  label: string
  options: SelectOption[]
  selectedValues: string[]
  onChange: (selected: string[]) => void
  multiple?: boolean
  maxSelections?: number
  emptyLabel?: string
}) {
  function toggle(value: string) {
    if (!multiple) {
      onChange([value])
      return
    }
    if (selectedValues.includes(value)) {
      onChange(selectedValues.filter((item) => item !== value))
      return
    }
    if (maxSelections !== undefined && selectedValues.length >= maxSelections) return
    onChange([...selectedValues, value])
  }

  return (
    <View style={styles.choiceField}>
      <Text style={styles.fieldLabel}>{label}</Text>
      {options.map((option) => {
        const selected = selectedValues.includes(option.value)
        return (
          <Pressable
            key={option.value || 'no-trailer'}
            onPress={() => toggle(option.value)}
            style={[styles.resourceChoice, selected && styles.resourceChoiceSelected]}
            accessibilityRole="checkbox"
            accessibilityState={{ checked: selected }}
          >
            <View style={styles.resourceChoiceCopy}>
              <Text style={[styles.resourceChoiceLabel, selected && styles.resourceChoiceLabelSelected]}>{option.label}</Text>
              {option.detail ? <Text style={styles.resourceChoiceDetail}>{option.detail}</Text> : null}
            </View>
            <Text style={[styles.resourceChoiceCheck, selected && styles.resourceChoiceCheckSelected]}>{selected ? '✓' : '+'}</Text>
          </Pressable>
        )
      })}
      {!options.length && emptyLabel ? <Text style={styles.emptyText}>{emptyLabel}</Text> : null}
      {multiple ? <Text style={styles.mutedText}>{selectedValues.length} selected</Text> : null}
    </View>
  )
}

function DataRow({ label, value }: { label: string; value: string }) {
  return <View style={styles.dataRow}><Text style={styles.bodyText}>{label}</Text><Text style={styles.mutedText}>{value}</Text></View>
}

function RouteMapCard({ route, position }: { route: Route; position?: RouteSimulationPosition }) {
  const valid = route.geometry.filter((point) => Number.isFinite(point.latitude) && Number.isFinite(point.longitude))
  if (!valid.length) return null
  return (
    <View style={styles.mapCard}>
      <RouteMap coordinates={valid} position={position} />
      <Text style={styles.mutedText}>Route #{route.id} · {route.vehicleLicensePlate} · {position ? `${Math.round(position.progress * 100)}% complete · ` : ''}simulated, not GPS</Text>
    </View>
  )
}

function RouteCard({ route, goods, onAction, onEdit, expanded }: { route: Route; goods: Goods[]; onAction: (route: Route, action: 'start' | 'cancel' | 'delete' | 'deliver', goodsId?: number) => void; onEdit?: (route: Route) => void; expanded?: boolean }) {
  const pendingDeliveries = route.stops.filter((stop) =>
    stop.type === 'DELIVERY'
    && goods.find((item) => item.id === stop.goodsId)?.status === 'IN_TRANSIT'
    && route.stops
      .filter((earlier) => earlier.type === 'DELIVERY' && earlier.stopOrder < stop.stopOrder)
      .every((earlier) => ['DELIVERED', 'ARCHIVED'].includes(
        goods.find((item) => item.id === earlier.goodsId)?.status ?? '',
      )))
  return (
    <Card title={`Route #${route.id}`} trailing={<Badge value={route.status} />}>
      <Text style={styles.bodyText}>{route.driverName} · {route.vehicleLicensePlate}{route.trailerLicensePlate ? ` · ${route.trailerLicensePlate}` : ''}</Text>
      <Text style={styles.mutedText}>{(route.distanceMeters / 1000).toFixed(1)} km · {Math.round(route.drivingDurationSeconds / 60)} min · departs {dateString(route.departureTime)}</Text>
      {expanded || route.status === 'ACTIVE' ? (
        <View style={styles.stops}>
          {route.stops.map((stop) => (
            <View key={`${stop.goodsId}-${stop.type}`} style={styles.stopRow}>
              <Text style={styles.stopType}>{stop.stopOrder}. {stop.type === 'PICKUP' ? 'Pickup' : 'Delivery'}</Text>
              <Text style={styles.bodyText}>{stop.city}</Text>
              <Text style={styles.mutedText}>{stop.trackingCode}</Text>
            </View>
          ))}
        </View>
      ) : null}
      {route.status === 'PLANNED' ? <View style={styles.rowActions}>{onEdit ? <LinkButton label="Edit route" onPress={() => onEdit(route)} /> : null}<Button label="Start route" onPress={() => onAction(route, 'start')} /><Button label="Cancel" secondary onPress={() => onAction(route, 'cancel')} /><LinkButton label="Delete" danger onPress={() => onAction(route, 'delete')} /></View> : null}
      {route.status === 'CANCELLED' && (!route.estimatedReturnTime || route.returnedAt)
        ? <View style={styles.rowActions}><LinkButton label="Delete" danger onPress={() => onAction(route, 'delete')} /></View>
        : null}
      {route.status === 'ACTIVE' ? pendingDeliveries.map((stop) => <View key={stop.goodsId} style={styles.deliveryRow}><Text style={styles.bodyText}>{stop.trackingCode} · {stop.city}</Text><Button label="Confirm delivery" onPress={() => onAction(route, 'deliver', stop.goodsId)} /></View>) : null}
    </Card>
  )
}

function FormEditor({ modal, busy, error, onChange, onClose, onSave, drivers, vehicles, trailers, goods, routes }: {
  modal: FormModal
  busy: boolean
  error: string
  onChange: (key: string, value: string) => void
  onClose: () => void
  onSave: () => void
  drivers: Driver[]
  vehicles: Vehicle[]
  trailers: Trailer[]
  goods: Goods[]
  routes: Route[]
}) {
  const fields: Record<FormKind, { key: string; label: string; keyboard?: 'default' | 'email-address' | 'numeric' | 'decimal-pad'; options?: string[] }[]> = {
    freight: [
      { key: 'clientName', label: 'Client' }, { key: 'goodsType', label: 'Freight type', options: GOODS_TYPES },
      { key: 'description', label: 'Description' }, { key: 'weightKg', label: 'Weight (kg)', keyboard: 'decimal-pad' },
      { key: 'volumeM3', label: 'Volume (m³)', keyboard: 'decimal-pad' }, { key: 'revenueEur', label: 'Revenue (€)', keyboard: 'decimal-pad' },
      { key: 'pickupCity', label: 'Pickup city' }, { key: 'deliveryCity', label: 'Delivery city' },
    ],
    driver: [
      { key: 'name', label: 'Name' }, { key: 'email', label: 'Email', keyboard: 'email-address' },
      { key: 'licenseCategories', label: 'License categories (comma separated)' },
    ],
    vehicle: [
      { key: 'licensePlate', label: 'License plate' }, { key: 'vehicleType', label: 'Vehicle type', options: VEHICLE_TYPES },
      { key: 'make', label: 'Make' }, { key: 'model', label: 'Model' }, { key: 'year', label: 'Year', keyboard: 'numeric' },
      { key: 'maxWeightCapacityKg', label: 'Max weight capacity (kg)', keyboard: 'decimal-pad' }, { key: 'maxVolumeM3', label: 'Max volume (m³)', keyboard: 'decimal-pad' },
      { key: 'currentMileageKm', label: 'Current mileage (km)', keyboard: 'numeric' }, { key: 'transmissionType', label: 'Transmission', options: TRANSMISSIONS },
    ],
    trailer: [
      { key: 'licensePlate', label: 'License plate' }, { key: 'trailerType', label: 'Trailer type', options: TRAILER_TYPES },
      { key: 'compatibleVehicleType', label: 'Compatible vehicle', options: VEHICLE_TYPES },
      { key: 'make', label: 'Make' }, { key: 'model', label: 'Model' }, { key: 'year', label: 'Year', keyboard: 'numeric' },
      { key: 'maxWeightCapacityKg', label: 'Max weight capacity (kg)', keyboard: 'decimal-pad' }, { key: 'maxVolumeM3', label: 'Max volume (m³)', keyboard: 'decimal-pad' },
    ],
    route: [{ key: 'departureTime', label: 'Departure date and time' }],
    maintenance: [
      { key: 'performedAt', label: 'Service date and time' },
      { key: 'odometerKm', label: 'Odometer (km)', keyboard: 'numeric' },
      { key: 'costEur', label: 'Service cost (€; optional)', keyboard: 'decimal-pad' },
    ],
    trailerMaintenance: [
      { key: 'performedAt', label: 'Service date and time' },
      { key: 'costEur', label: 'Service cost (€; optional)', keyboard: 'decimal-pad' },
    ],
  }
  const title = modal.kind === 'maintenance' || modal.kind === 'trailerMaintenance'
    ? `Record ${modal.component?.label ?? modal.trailerComponent?.label ?? 'service'} · ${modal.vehicle?.licensePlate ?? modal.trailer?.licensePlate ?? ''}`
    : `${modal.editingId === undefined ? 'Add' : 'Edit'} ${modal.kind}`
  const availableDrivers = drivers
    .filter((driver) => driver.status === 'AVAILABLE'
      && !routes.some((route) => route.id !== modal.editingId && route.driverId === driver.id
        && (route.status === 'PLANNED' || route.status === 'ACTIVE')))
    .map((driver) => ({ value: String(driver.id), label: driver.name, detail: `${driver.email} · ${driver.licenseCategories.join(', ') || 'No license categories'}` }))
  const availableVehicles = vehicles
    .filter((vehicle) => vehicle.status === 'AVAILABLE' && !vehicle.maintenanceAutoTriggered
      && !routes.some((route) => route.id !== modal.editingId && route.vehicleId === vehicle.id
        && (route.status === 'PLANNED' || route.status === 'ACTIVE')))
    .map((vehicle) => ({ value: String(vehicle.id), label: vehicle.licensePlate, detail: `${vehicle.make} ${vehicle.model} · ${displayStatus(vehicle.vehicleType)}` }))
  const availableTrailers = trailers
    .filter((trailer) => trailer.status === 'AVAILABLE'
      && trailer.compatibleVehicleType === vehicles.find((vehicle) => vehicle.id === Number(modal.values.vehicleId))?.vehicleType
      && !routes.some((route) => route.id !== modal.editingId && route.trailerId === trailer.id
        && (route.status === 'PLANNED' || route.status === 'ACTIVE')))
    .map((trailer) => ({ value: String(trailer.id), label: trailer.licensePlate, detail: `${trailer.make} ${trailer.model} · ${displayStatus(trailer.trailerType)}` }))
  const unassignedFreight = goods
    .filter((item) => item.status === 'UNASSIGNED'
      || (modal.editingId !== undefined
        && routes.find((route) => route.id === modal.editingId)?.stops.some((stop) => stop.goodsId === item.id)))
    .map((item) => ({ value: String(item.id), label: `${item.trackingCode} · ${item.clientName}`, detail: `${item.pickupCity} → ${item.deliveryCity} · ${displayStatus(item.goodsType)}` }))
  const selectedFreight = (modal.values.goodsIds ?? '').split(',').filter(Boolean)
    .map((id) => goods.find((item) => item.id === Number(id)))
    .filter((item): item is Goods => item !== undefined)
  const selectedVehicle = vehicles.find((vehicle) => vehicle.id === Number(modal.values.vehicleId))
  const routeTrailerRequired = Boolean(selectedVehicle && (
    selectedVehicle.vehicleType === 'ARTICULATED_TRUCK'
    || selectedFreight.reduce((total, item) => total + item.weightKg, 0) > selectedVehicle.maxWeightCapacityKg
    || selectedFreight.reduce((total, item) => total + item.volumeM3, 0) > selectedVehicle.maxVolumeM3
    || selectedFreight.some((item) => item.goodsType !== 'GENERAL')
  ))

  return (
    <View style={styles.modalScrim}>
      <KeyboardAvoidingView style={styles.modalFrame} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <View style={styles.modalHeader}><Text style={styles.cardTitle}>{title}</Text><Pressable onPress={onClose}><Text style={styles.closeText}>×</Text></Pressable></View>
        <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={styles.modalContent}>
          {modal.kind === 'route' ? <Text style={styles.warningText}>The server calculates road distance and arrival time. Pickups are ordered before deliveries; the backend validates vehicle, driver, cargo, and trailer compatibility.</Text> : null}
          {modal.kind === 'maintenance' ? <Text style={styles.warningText}>Odometer must not exceed the vehicle&apos;s current mileage. Use an ISO date/time such as 2026-10-01T08:00:00Z.</Text> : null}
          {modal.kind === 'route' ? (
            <>
              <ChoiceField
                label="Driver"
                options={availableDrivers}
                selectedValues={modal.values.driverId ? [modal.values.driverId] : []}
                onChange={(selected) => onChange('driverId', selected[0] ?? '')}
                emptyLabel="No available drivers."
              />
              <ChoiceField
                label="Vehicle"
                options={availableVehicles}
                selectedValues={modal.values.vehicleId ? [modal.values.vehicleId] : []}
                onChange={(selected) => onChange('vehicleId', selected[0] ?? '')}
                emptyLabel="No available vehicles."
              />
              <ChoiceField
                label={routeTrailerRequired ? 'Trailer (required)' : 'Trailer (optional)'}
                options={[...(routeTrailerRequired ? [] : [{ value: '', label: 'No trailer', detail: 'Continue without a trailer' }]), ...availableTrailers]}
                selectedValues={[modal.values.trailerId ?? '']}
                onChange={(selected) => onChange('trailerId', selected[0] ?? '')}
                emptyLabel={routeTrailerRequired ? 'No compatible trailer available. Add one in Fleet.' : undefined}
              />
              <ChoiceField
                label="Freight (select 1–12)"
                options={unassignedFreight}
                selectedValues={(modal.values.goodsIds ?? '').split(',').filter(Boolean)}
                onChange={(selected) => onChange('goodsIds', selected.join(','))}
                multiple
                maxSelections={12}
                emptyLabel="No unassigned freight available."
              />
            </>
          ) : null}
          {fields[modal.kind].map((field) => (
            <View key={field.key}>
              {field.options ? (
                <View style={styles.field}>
                  <Text style={styles.fieldLabel}>{field.label}</Text>
                  <ScrollView horizontal showsHorizontalScrollIndicator={false}>
                    <View style={styles.choiceRow}>
                      {field.options.map((option) => <Pressable key={option} onPress={() => onChange(field.key, option)} style={[styles.choice, modal.values[field.key] === option && styles.selectedChoice]}><Text style={[styles.choiceText, modal.values[field.key] === option && styles.selectedChoiceText]}>{displayStatus(option)}</Text></Pressable>)}
                    </View>
                  </ScrollView>
                </View>
              ) : field.key === 'departureTime' || field.key === 'performedAt'
                ? <DateTimePickerField label={field.label} value={modal.values[field.key] ?? ''} onChange={(value) => onChange(field.key, value)} />
                : <Field label={field.label} value={modal.values[field.key] ?? ''} onChangeText={(value) => onChange(field.key, value)} keyboardType={field.keyboard} />}
            </View>
          ))}
          {error ? <Text style={styles.formError}>{error}</Text> : null}
          <View style={styles.formActions}><Button label={busy ? 'Saving…' : 'Save'} disabled={busy} onPress={onSave} /><Button label="Cancel" secondary onPress={onClose} /></View>
        </ScrollView>
      </KeyboardAvoidingView>
    </View>
  )
}

const styles = StyleSheet.create({
  app: { flex: 1, backgroundColor: '#0d0f11' },
  topbar: { minHeight: 62, paddingHorizontal: 18, backgroundColor: NAVY, borderBottomWidth: 1, borderBottomColor: '#2d333b', flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  brandRow: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  brandMark: { width: 36, height: 36, backgroundColor: ACCENT, justifyContent: 'center', alignItems: 'center' },
  brandMarkText: { color: '#0d0f11', fontWeight: '800', fontSize: 13, fontFamily: 'monospace' },
  brandName: { color: '#f3f4f6', fontWeight: '800', fontSize: 13, letterSpacing: 1, fontFamily: 'sans-serif' },
  brandSubtitle: { color: '#9ca3af', fontWeight: '600', fontSize: 8, marginTop: 2, letterSpacing: 1, fontFamily: 'monospace' },
  refreshButton: { width: 36, height: 36, borderWidth: 1, borderColor: '#2d333b', backgroundColor: '#161a1d', alignItems: 'center', justifyContent: 'center' },
  refreshText: { color: ACCENT, fontSize: 22 },
  pageTitle: { paddingHorizontal: 18, paddingTop: 18, paddingBottom: 12 },
  eyebrow: { color: ACCENT, fontSize: 9, fontWeight: '800', letterSpacing: 1.4, fontFamily: 'monospace' },
  title: { color: '#f3f4f6', fontSize: 23, fontWeight: '700', marginTop: 5, fontFamily: 'sans-serif' },
  content: { flex: 1 },
  contentInner: { padding: 14, paddingBottom: 28, gap: 12 },
  message: { marginHorizontal: 14, marginBottom: 4, padding: 11, borderWidth: 1 },
  errorMessage: { borderColor: '#7f1d1d', backgroundColor: '#261314' },
  successMessage: { borderColor: '#166b4e', backgroundColor: '#10231d' },
  messageText: { color: '#f3f4f6', fontSize: 12 },
  statsGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 9 },
  statCard: { flexBasis: '48%', flexGrow: 1, minHeight: 88, backgroundColor: '#161a1d', borderWidth: 1, borderColor: '#2d333b', padding: 13 },
  statLabel: { color: '#9ca3af', fontSize: 10, fontWeight: '600', textTransform: 'uppercase', fontFamily: 'monospace' },
  statValue: { fontSize: 23, fontWeight: '700', marginTop: 8, fontFamily: 'monospace' },
  purpleText: { color: ACCENT }, greenText: { color: ACCENT }, blueText: { color: ACCENT }, orangeText: { color: ACCENT },
  card: { backgroundColor: '#161a1d', borderWidth: 1, borderColor: '#2d333b', padding: 14, gap: 10 },
  cardHeading: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 8, paddingBottom: 9, borderBottomWidth: 1, borderBottomColor: '#2d333b' },
  cardTitle: { color: '#f3f4f6', fontSize: 14, fontWeight: '700', fontFamily: 'sans-serif' },
  mutedText: { color: '#9ca3af', fontSize: 10, lineHeight: 16 },
  bodyText: { color: '#d1d5db', fontSize: 12, lineHeight: 18 },
  emptyText: { paddingVertical: 8, color: '#9ca3af', fontSize: 12, lineHeight: 18 },
  flex: { flex: 1 },
  badge: { borderWidth: 1, paddingHorizontal: 8, paddingVertical: 4, alignSelf: 'flex-start' },
  badgeGreen: { borderColor: '#166b4e', backgroundColor: '#10231d' }, badgeBlue: { borderColor: '#075985', backgroundColor: '#0d2029' }, badgeRed: { borderColor: '#7f1d1d', backgroundColor: '#261314' }, badgeGray: { borderColor: '#374151', backgroundColor: '#161a1d' },
  badgeText: { color: '#c4c8ce', fontSize: 8, fontWeight: '700', textTransform: 'uppercase', fontFamily: 'monospace' },
  button: { minHeight: 38, borderWidth: 1, borderColor: ACCENT, paddingHorizontal: 13, backgroundColor: ACCENT, alignItems: 'center', justifyContent: 'center' },
  buttonText: { color: '#0d0f11', fontSize: 10, fontWeight: '700', fontFamily: 'monospace', textTransform: 'uppercase' },
  secondaryButton: { borderWidth: 1, borderColor: '#374151', backgroundColor: '#1f2428' },
  secondaryButtonText: { color: '#f3f4f6' },
  dangerButton: { backgroundColor: '#261314', borderWidth: 1, borderColor: '#7f1d1d' },
  dangerButtonText: { color: '#f87171' },
  disabledButton: { opacity: 0.5 },
  linkButton: { minHeight: 34, justifyContent: 'center', paddingHorizontal: 5 },
  linkText: { color: ACCENT, fontSize: 10, fontWeight: '700', fontFamily: 'monospace', textTransform: 'uppercase' },
  dangerLink: { color: '#f87171' },
  headingRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 },
  sectionIntro: { flex: 1, color: '#9ca3af', fontSize: 10 },
  actionGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  rowActions: { flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', gap: 6, marginTop: 2 },
  searchInput: { minHeight: 42, borderWidth: 1, borderColor: '#374151', backgroundColor: '#111416', paddingHorizontal: 12, color: '#f3f4f6', fontSize: 12 },
  subheading: { color: '#f3f4f6', fontSize: 13, fontWeight: '700', marginTop: 4, fontFamily: 'sans-serif' },
  warningText: { color: '#fbbf24', backgroundColor: '#241d10', borderWidth: 1, borderColor: '#80520e', padding: 9, fontSize: 10, lineHeight: 16 },
  componentRow: { gap: 5, borderTopWidth: 1, borderTopColor: '#2d333b', paddingTop: 9 },
  componentHeading: { flexDirection: 'row', justifyContent: 'space-between', gap: 8 },
  healthTrack: { height: 6, backgroundColor: '#252b30', overflow: 'hidden' },
  healthBar: { height: 6, backgroundColor: '#10b981' },
  dateFields: { flexDirection: 'row', gap: 8 },
  field: { gap: 5, marginBottom: 3 },
  fieldLabel: { color: '#c1c7d0', fontSize: 10, fontWeight: '600' },
  fieldInput: { minHeight: 40, borderWidth: 1, borderColor: '#374151', paddingHorizontal: 10, color: '#f3f4f6', backgroundColor: '#111416', fontSize: 12 },
  dataRow: { paddingVertical: 7, borderTopWidth: 1, borderTopColor: '#2d333b', gap: 2 },
  mapCard: { gap: 7 },
  stops: { borderTopWidth: 1, borderTopColor: '#2d333b', paddingTop: 5, gap: 8 },
  stopRow: { borderLeftWidth: 2, borderLeftColor: ACCENT, paddingLeft: 9 },
  stopType: { color: ACCENT, fontSize: 9, fontWeight: '700', textTransform: 'uppercase', fontFamily: 'monospace' },
  deliveryRow: { gap: 6, paddingTop: 8, borderTopWidth: 1, borderTopColor: '#2d333b' },
  tabBar: { minHeight: 58, borderTopWidth: 1, borderTopColor: '#2d333b', backgroundColor: '#111416' },
  tabList: { alignItems: 'center', paddingHorizontal: 6, gap: 3 },
  tabItem: { flexDirection: 'row', alignItems: 'center', gap: 4, minHeight: 40, paddingHorizontal: 10 },
  selectedTab: { borderTopWidth: 2, borderTopColor: ACCENT, backgroundColor: '#1a1c1e' },
  tabText: { color: '#9ca3af', fontSize: 9, fontWeight: '600', fontFamily: 'monospace', textTransform: 'uppercase' },
  selectedTabText: { color: ACCENT },
  tabCount: { color: '#0d0f11', backgroundColor: ACCENT, fontSize: 8, fontWeight: '700', overflow: 'hidden', paddingHorizontal: 4, fontFamily: 'monospace' },
  assistantFab: { position: 'absolute', right: 16, bottom: 72, width: 52, height: 52, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: '#fbbf24', backgroundColor: ACCENT, elevation: 8, shadowColor: '#000', shadowOpacity: 0.35, shadowRadius: 8, shadowOffset: { width: 0, height: 3 } },
  assistantFabText: { color: '#0d0f11', fontFamily: 'monospace', fontSize: 13, fontWeight: '800' },
  modalScrim: { ...StyleSheet.absoluteFill, zIndex: 5, backgroundColor: 'rgba(0, 0, 0, .72)', justifyContent: 'flex-end' },
  modalFrame: { maxHeight: '90%', backgroundColor: '#161a1d', borderTopWidth: 1, borderColor: '#2d333b', paddingBottom: Platform.OS === 'ios' ? 26 : 10 },
  modalHeader: { minHeight: 54, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 17, backgroundColor: '#111416', borderBottomWidth: 1, borderColor: '#2d333b' },
  closeText: { color: '#9ca3af', fontSize: 26, paddingHorizontal: 5 },
  modalContent: { gap: 10, padding: 15, paddingBottom: 24 },
  choiceField: { gap: 6 },
  resourceChoice: { minHeight: 48, flexDirection: 'row', alignItems: 'center', gap: 10, borderWidth: 1, borderColor: '#374151', paddingHorizontal: 10, paddingVertical: 7, backgroundColor: '#111416' },
  resourceChoiceSelected: { borderColor: ACCENT, backgroundColor: '#241d10' },
  resourceChoiceCopy: { flex: 1, gap: 3 },
  resourceChoiceLabel: { color: '#f3f4f6', fontSize: 11, fontWeight: '600' },
  resourceChoiceLabelSelected: { color: ACCENT },
  resourceChoiceDetail: { color: '#9ca3af', fontSize: 9 },
  resourceChoiceCheck: { color: '#6b7280', fontFamily: 'monospace', fontSize: 14, fontWeight: '700' },
  resourceChoiceCheckSelected: { color: ACCENT },
  choiceRow: { flexDirection: 'row', gap: 6, paddingVertical: 2 },
  choice: { borderWidth: 1, borderColor: '#374151', backgroundColor: '#111416', paddingHorizontal: 9, paddingVertical: 8 },
  selectedChoice: { borderColor: ACCENT, backgroundColor: '#241d10' },
  choiceText: { color: '#c4c8ce', fontSize: 10, textTransform: 'capitalize' },
  selectedChoiceText: { color: ACCENT, fontWeight: '700' },
  formError: { color: '#f87171', fontSize: 11 },
  formActions: { flexDirection: 'row', gap: 8, marginTop: 5 },
  chatList: { gap: 9 },
  chatBubble: { padding: 12, maxWidth: '94%' },
  userBubble: { alignSelf: 'flex-end', backgroundColor: '#241d10', borderWidth: 1, borderColor: '#574116' },
  assistantBubble: { alignSelf: 'flex-start', backgroundColor: '#161a1d', borderWidth: 1, borderColor: '#2d333b' },
  assistantScrim: { flex: 1, justifyContent: 'flex-end', backgroundColor: 'rgba(0, 0, 0, .72)' },
  assistantKeyboard: { flex: 1, justifyContent: 'flex-end' },
  assistantPanel: { height: '82%', maxHeight: 700, minHeight: 360, gap: 12, borderWidth: 1, borderColor: '#2d333b', padding: 14, backgroundColor: '#161a1d' },
  assistantHeader: { minHeight: 42, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', borderBottomWidth: 1, borderBottomColor: '#2d333b', paddingBottom: 8 },
  assistantMessages: { flex: 1 },
  actionProposal: { marginTop: 9, gap: 8 },
  proposalTitle: { color: ACCENT, fontSize: 11, fontWeight: '700' },
  chatComposer: { flexDirection: 'row', alignItems: 'flex-end', gap: 8 },
  chatInput: { flex: 1, maxHeight: 120, minHeight: 42, borderWidth: 1, borderColor: '#374151', backgroundColor: '#111416', padding: 10, color: '#f3f4f6', fontSize: 12 },
})
