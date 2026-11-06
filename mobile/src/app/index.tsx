import { useCallback, useEffect, useMemo, useState } from 'react'
import Constants from 'expo-constants'
import * as Device from 'expo-device'
import * as Notifications from 'expo-notifications'
import { AndroidImportance } from 'expo-notifications'
import * as SecureStore from 'expo-secure-store'
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

Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowBanner: true,
    shouldShowList: true,
    shouldPlaySound: true,
    shouldSetBadge: false,
  }),
})

const TABS: Tab[] = ['Home', 'Routes', 'Freight', 'Drivers', 'Fleet', 'Service', 'Finance']
const ACCENT = '#f59e0b'
const NAVY = '#111416'
const MAX_OPERATIONAL_CONTEXT_BYTES = 150_000
const OPERATIONAL_TOPIC = /\b(?:haulmaster|logistics|operations?|dispatch)\b/i
const ROUTE_REQUEST = /\b(?:ship|send|move|haul|transport|pick up)\b/i
const CRUD_REQUEST = /\b(?:create|add|update|change|delete|remove)\b/i
const OPERATIONAL_ENTITY = /\b(?:drivers?|vehicles?|trucks?|fleet|trailers?|freight|shipments?|goods|routes?|deliver(?:y|ies|ed)?|maintenance|finance|revenue|profit|clients?|cargo|metal|earnings?|income|costs?|margins?)\b/i
const OPERATIONAL_INTENT = /\b(?:our|my|we|how many|which|who|show|list|status|available|in service|due|assigned|unassigned|current|currently|planned|active|overdue|create|add|update|change|delete|remove|record|assign|plan|deliver|set|mark|have)\b/i
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
const contextDomains = (messages: AssistantMessage[]) => {
  const recentUserMessages = messages
    .filter((message) => message.role === 'user')
    .slice(-3)
    .map((message) => message.content)
    .join('\n')
  const isOperational = OPERATIONAL_TOPIC.test(recentUserMessages)
    || ROUTE_REQUEST.test(recentUserMessages)
    || CRUD_REQUEST.test(recentUserMessages)
    || (OPERATIONAL_ENTITY.test(recentUserMessages) && OPERATIONAL_INTENT.test(recentUserMessages))
  if (!isOperational) return null

  const requested = {
    drivers: /\bdrivers?\b/i.test(recentUserMessages),
    vehicles: /\b(?:vehicles?|trucks?|fleet)\b/i.test(recentUserMessages),
    trailers: /\btrailers?\b/i.test(recentUserMessages),
    freight: /\b(?:freight|shipments?|goods|clients?|cargo|metal)\b/i.test(recentUserMessages) || ROUTE_REQUEST.test(recentUserMessages),
    routes: /\b(?:routes?|deliver(?:y|ies|ed)?|dispatch)\b/i.test(recentUserMessages) || ROUTE_REQUEST.test(recentUserMessages),
    maintenance: /\bmaintenance\b/i.test(recentUserMessages),
    finance: /\b(?:finance|revenue|profit|earnings?|income|costs?|margins?)\b/i.test(recentUserMessages),
    includeMargins: /\b(?:margins?|profitability)\b/i.test(recentUserMessages),
  }
  if (requested.routes) {
    requested.drivers = true
    requested.vehicles = true
    requested.trailers = true
    requested.freight = true
  }
  if (requested.maintenance) {
    requested.vehicles = true
    requested.trailers = true
  }
  if (!Object.values(requested).some(Boolean)) {
    return {
      drivers: true, vehicles: true, trailers: true, freight: true, routes: true,
      maintenance: true, finance: true, includeMargins: requested.includeMargins,
    }
  }
  return requested
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

function assistantFinanceContext(report: FinanceReport | null, includeMargins: boolean) {
  if (!report) return null
  const { freightMargins, ...summary } = report
  return { ...summary, ...(includeMargins ? { freightMargins } : {}) }
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
  const [archivedDrivers, setArchivedDrivers] = useState<Driver[]>([])
  const [vehicles, setVehicles] = useState<Vehicle[]>([])
  const [archivedVehicles, setArchivedVehicles] = useState<Vehicle[]>([])
  const [trailers, setTrailers] = useState<Trailer[]>([])
  const [goods, setGoods] = useState<Goods[]>([])
  const [archivedGoods, setArchivedGoods] = useState<Goods[]>([])
  const [routes, setRoutes] = useState<Route[]>([])
  const [archivedRoutes, setArchivedRoutes] = useState<Route[]>([])
  const [simulationPositions, setSimulationPositions] = useState<Record<number, RouteSimulationPosition>>({})
  const [finance, setFinance] = useState<FinanceReport | null>(null)
  const [fromDate, setFromDate] = useState(() => dateInputValue(new Date(new Date().getFullYear(), new Date().getMonth(), 1)))
  const [toDate, setToDate] = useState(() => dateInputValue(new Date()))
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [mobilePushToken, setMobilePushToken] = useState<string | null>(null)
  const [pushBusy, setPushBusy] = useState(false)
  const [showArchived, setShowArchived] = useState(false)
  const [search, setSearch] = useState('')
  const [modal, setModal] = useState<FormModal | null>(null)
  const [selectedDriverId, setSelectedDriverId] = useState<number | null>(null)
  const [selectedFleetVehicleId, setSelectedFleetVehicleId] = useState<number | null>(null)
  const [fleetDetailTab, setFleetDetailTab] = useState<'Shipments' | 'Maintenance' | 'Profitability'>('Shipments')
  const [fleetPage, setFleetPage] = useState(1)
  const [fleetSort, setFleetSort] = useState<'PLATE' | 'MILEAGE' | 'STATUS'>('PLATE')
  const [chat, setChat] = useState<ChatLine[]>([])
  const [chatInput, setChatInput] = useState('')
  const [chatBusy, setChatBusy] = useState(false)
  const [pendingAction, setPendingAction] = useState<AssistantAction | null>(null)
  const [assistantOpen, setAssistantOpen] = useState(false)
  const [expandedHealth, setExpandedHealth] = useState<Record<string, boolean>>({})
  const selectedDriver = [...drivers, ...archivedDrivers].find((driver) => driver.id === selectedDriverId) ?? null
  const allRoutes = [...routes, ...archivedRoutes]
  const allGoods = [...goods, ...archivedGoods]
  const driverRoutes = selectedDriver ? allRoutes.filter((route) => route.driverId === selectedDriver.id) : []
  const selectedFleetVehicle = [...vehicles, ...archivedVehicles].find((vehicle) => vehicle.id === selectedFleetVehicleId) ?? null
  const selectedFleetRoutes = allRoutes
    .filter((route) => route.vehicleId === selectedFleetVehicleId)
    .sort((first, second) => second.departureTime.localeCompare(first.departureTime))
  const selectedVehicleProfitability = finance?.vehicles.find((vehicle) => vehicle.vehicleId === selectedFleetVehicleId) ?? null

  const driverForVehicle = useCallback((vehicleId: number) => [...routes, ...archivedRoutes].find((route) =>
    route.vehicleId === vehicleId
    && (route.status === 'PLANNED' || route.status === 'ACTIVE'
      || (route.status === 'COMPLETED' && route.estimatedReturnTime && !route.returnedAt)),
  ), [archivedRoutes, routes])

  const setTab = (nextTab: Tab) => {
    router.setParams({ screen: nextTab.toLowerCase() })
    setSearch('')
    setSelectedFleetVehicleId(null)
    setShowArchived(false)
  }

  const fetchData = useCallback(async () => {
    const [nextDrivers, nextArchivedDrivers, nextVehicles, nextArchivedVehicles, nextTrailers, nextGoods, nextArchivedGoods, nextRoutes, nextArchivedRoutes, nextFinance, positions] = await Promise.all([
      request<Driver[]>('/drivers'),
      request<Driver[]>('/drivers/archive'),
      request<Vehicle[]>('/vehicles'),
      request<Vehicle[]>('/vehicles/archive'),
      request<Trailer[]>('/trailers'),
      request<Goods[]>('/goods'),
      request<Goods[]>('/goods/archive'),
      request<Route[]>('/routes'),
      request<Route[]>('/routes/archive'),
      request<FinanceReport>(`/finance?from=${encodeURIComponent(fromDate)}&to=${encodeURIComponent(toDate)}`),
      request<RouteSimulationPosition[]>('/routes/simulations'),
    ])
    setDrivers(nextDrivers)
    setArchivedDrivers(nextArchivedDrivers)
    setVehicles(nextVehicles)
    setArchivedVehicles(nextArchivedVehicles)
    setFleetPage(1)
    setTrailers(nextTrailers)
    setGoods(nextGoods)
    setArchivedGoods(nextArchivedGoods)
    setRoutes(nextRoutes)
    setArchivedRoutes(nextArchivedRoutes)
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

  useEffect(() => {
    const received = Notifications.addNotificationReceivedListener((notification) => {
      const { title, body } = notification.request.content
      setNotice([title, body].filter(Boolean).join(': ') || 'A logistics update arrived.')
    })
    const response = Notifications.addNotificationResponseReceivedListener(() => {
      router.setParams({ screen: 'routes' })
    })
    return () => {
      received.remove()
      response.remove()
    }
  }, [])

  useEffect(() => {
    if (Platform.OS === 'web') return
    let active = true
    void SecureStore.getItemAsync('haulmaster-mobile-push-token')
      .then(async (token) => {
        if (!active || !token) return
        const permission = await Notifications.getPermissionsAsync()
        if (!permission.granted) {
          await send('/notifications/mobile-subscription', 'DELETE', { token })
          await SecureStore.deleteItemAsync('haulmaster-mobile-push-token')
          return
        }
        setMobilePushToken(token)
      })
      .catch((storageError: unknown) => {
        if (active) setError(storageError instanceof Error ? storageError.message : 'Could not load mobile notification settings.')
      })
    return () => {
      active = false
    }
  }, [])

  async function toggleMobilePush() {
    if (pushBusy) return
    setPushBusy(true)
    setError('')
    try {
      if (mobilePushToken) {
        await send('/notifications/mobile-subscription', 'DELETE', { token: mobilePushToken })
        setMobilePushToken(null)
        await SecureStore.deleteItemAsync('haulmaster-mobile-push-token')
        setNotice('Mobile push notifications disabled on this device.')
        return
      }
      if (Platform.OS === 'web' || !Device.isDevice) {
        throw new Error('Push notifications require a physical Android or iOS device.')
      }
      const projectId = Constants.easConfig?.projectId
        ?? Constants.expoConfig?.extra?.eas?.projectId
        ?? process.env.EXPO_PUBLIC_EAS_PROJECT_ID
      if (!projectId) {
        throw new Error('Set the Expo EAS project ID before enabling mobile push notifications.')
      }
      if (Platform.OS === 'android') {
        await Notifications.setNotificationChannelAsync('default', {
          name: 'Logistics updates',
          importance: AndroidImportance.HIGH,
        })
      }
      const currentPermission = await Notifications.getPermissionsAsync()
      const permission = currentPermission.granted
        ? currentPermission
        : await Notifications.requestPermissionsAsync()
      if (!permission.granted) {
        throw new Error('Notification permission was not granted.')
      }
      const token = (await Notifications.getExpoPushTokenAsync({ projectId })).data
      await SecureStore.setItemAsync('haulmaster-mobile-push-token', token)
      try {
        await send('/notifications/mobile-subscription', 'POST', { token })
      } catch (subscriptionError) {
        await SecureStore.deleteItemAsync('haulmaster-mobile-push-token')
        throw subscriptionError
      }
      setMobilePushToken(token)
      setNotice('Mobile push notifications enabled.')
    } catch (pushError) {
      setError(pushError instanceof Error ? pushError.message : 'Could not configure mobile push notifications.')
    } finally {
      setPushBusy(false)
    }
  }

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
    dueTrailers: trailers.filter((trailer) => trailer.status === 'MAINTENANCE').length,
  }), [drivers, goods, routes, trailers, vehicles])
  const filteredFleetVehicles = useMemo(() => {
    const query = search.trim().toLowerCase()
    return (showArchived ? archivedVehicles : vehicles)
      .filter((vehicle) => `${vehicle.licensePlate} ${vehicle.make} ${vehicle.model} ${vehicle.vehicleType} ${vehicle.status} ${driverForVehicle(vehicle.id)?.driverName ?? ''}`.toLowerCase().includes(query))
      .sort((first, second) => {
        if (fleetSort === 'MILEAGE') return second.currentMileageKm - first.currentMileageKm
        if (fleetSort === 'STATUS') return first.status.localeCompare(second.status) || first.licensePlate.localeCompare(second.licensePlate)
        return first.licensePlate.localeCompare(second.licensePlate)
      })
  }, [archivedVehicles, driverForVehicle, fleetSort, search, showArchived, vehicles])
  const fleetPageCount = Math.max(1, Math.ceil(filteredFleetVehicles.length / 10))
  const paginatedFleetVehicles = filteredFleetVehicles.slice((fleetPage - 1) * 10, fleetPage * 10)

  const hasScheduledDriverRoute = (id: number) => routes.some((route) =>
    route.driverId === id && (route.status === 'PLANNED' || route.status === 'ACTIVE'))
  const hasScheduledVehicleRoute = (id: number) => routes.some((route) =>
    route.vehicleId === id && (route.status === 'PLANNED' || route.status === 'ACTIVE'))
  const hasScheduledTrailerRoute = (id: number) => routes.some((route) =>
    route.trailerId === id && (route.status === 'PLANNED' || route.status === 'ACTIVE'))
  const hasTrailerHistory = (id: number) => routes.some((route) => route.trailerId === id)
  const hasGoodsHistory = (id: number) => allRoutes.some((route) =>
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
        const revenueEur = revenueText ? Number(revenueText) : null
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
        if (revenueText && (revenueEur === null || !Number.isFinite(revenueEur) || revenueEur < 0
          || revenueEur > 9_999_999_999.99 || !/^\d+(\.\d{1,2})?$/.test(revenueText))) {
          invalid('Freight revenue must be between €0 and €9,999,999,999.99 with at most two decimals.')
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
          && !item.maintenanceAutoTriggered
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
      const domains = contextDomains(history)
      const warehouse = domains?.freight ? await request<{ warehouseCity: string }>('/config') : null
      const context = domains ? {
        ...(domains.drivers ? { drivers } : {}),
        ...(domains.vehicles ? {
          vehicles: vehicles.map(({ maintenanceComponents, ...vehicle }) => ({
            ...vehicle,
            ...(Array.isArray(maintenanceComponents) ? {
              maintenanceComponents: maintenanceComponents.map(({ key, label, healthPercent, daysRemaining, lastPerformedAt, lastOdometerKm, lastCostEur }) =>
                ({ key, label, healthPercent, daysRemaining, lastPerformedAt, lastOdometerKm, lastCostEur })),
            } : {}),
          })),
        } : {}),
        ...(domains.trailers ? {
          trailers: trailers.map(({ maintenanceHistory: _maintenanceHistory, maintenanceComponents, ...trailer }) => ({
            ...trailer,
            ...(Array.isArray(maintenanceComponents) ? {
              maintenanceComponents: maintenanceComponents.map(({ key, label, healthPercent, daysRemaining, lastPerformedAt, lastCostEur }) =>
                ({ key, label, healthPercent, daysRemaining, lastPerformedAt, lastCostEur })),
            } : {}),
          })),
        } : {}),
        ...(domains.freight ? { goods } : {}),
        ...(warehouse ? { warehouseCity: warehouse.warehouseCity } : {}),
        ...(domains.routes ? {
          routes: routes.map(({ geometry: _geometry, stops, ...route }) => ({
            ...route,
            ...(Array.isArray(stops) ? {
              stops: stops.map(({ latitude: _latitude, longitude: _longitude, ...stop }) => stop),
            } : {}),
          })),
        } : {}),
        ...(domains.finance ? { finance: assistantFinanceContext(finance, domains.includeMargins) } : {}),
      } : {}
      if (new TextEncoder().encode(JSON.stringify(context)).byteLength > MAX_OPERATIONAL_CONTEXT_BYTES) {
        throw new Error('The live operations snapshot is too large to send. Narrow the question or reduce the operational data set.')
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
            <Card title="Mobile notifications" subtitle="Receive route planning, cancellation, and delivery updates on this device.">
              <Button
                label={pushBusy ? 'Working…' : mobilePushToken ? 'Disable push notifications' : 'Enable push notifications'}
                onPress={() => void toggleMobilePush()}
                secondary={Boolean(mobilePushToken)}
                disabled={pushBusy}
              />
            </Card>
            <View style={styles.statsGrid}>
              <Stat label="Active routes" value={counts.activeRoutes} tone="purple" />
              <Stat label="Drivers ready" value={counts.availableDrivers} tone="green" />
              <Stat label="Vehicles ready" value={counts.availableVehicles} tone="blue" />
              <Stat label="Unassigned freight" value={counts.unassignedGoods} tone="orange" />
            </View>
            <Card title="Recent routes" subtitle="Latest dispatches and delivery progress" action={<LinkButton label="All routes" onPress={() => setTab('Routes')} />}>
              {!routes.length ? <Empty>No routes yet. Plan a route when freight is ready.</Empty> : routes.slice(0, 4).map((route) => <RouteCard key={route.id} route={route} goods={allGoods} onAction={handleRouteAction} onDriverPress={setSelectedDriverId} />)}
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
            <View style={styles.headingRow}><Text style={styles.sectionIntro}>{showArchived ? 'Archived route history; completed routes are retained for finance analysis.' : 'Saved routes, stop order, and route-map simulation.'}</Text><View style={styles.rowActions}><LinkButton label={showArchived ? 'Show operational routes' : 'View route history'} onPress={() => setShowArchived((value) => !value)} />{!showArchived ? <Button label="+ Plan" onPress={() => openForm('route', { departureTime: dateTimeInputValue(new Date(Date.now() + 60_000)), goodsIds: '' })} /> : null}</View></View>
            <Card title="Active route map" subtitle="Positions are simulated from saved road geometry, not GPS.">
              {routes.filter((route) => route.status === 'ACTIVE').length === 0
                ? <Empty>No active routes.</Empty>
                : routes.filter((route) => route.status === 'ACTIVE').map((route) => <ActiveRouteCard key={route.id} route={route} position={simulationPositions[route.id]} onDriverPress={setSelectedDriverId} />)}
            </Card>
            {(showArchived ? archivedRoutes : routes).map((route) => <RouteCard key={route.id} route={route} goods={allGoods} onAction={handleRouteAction} onEdit={showArchived ? undefined : editRoute} onDriverPress={setSelectedDriverId} expanded />)}
            {!(showArchived ? archivedRoutes : routes).length ? <Empty>{showArchived ? 'No archived routes.' : 'No operational routes yet.'}</Empty> : null}
          </>
        )}

        {tab === 'Freight' && (
          <>
            <View style={styles.headingRow}><Text style={styles.sectionIntro}>{showArchived ? archivedGoods.length : goods.length} {showArchived ? 'archived' : 'active'} shipment records; retained for analysis</Text><View style={styles.rowActions}><LinkButton label={showArchived ? 'Show active freight' : 'View archived freight'} onPress={() => setShowArchived((value) => !value)} />{!showArchived ? <Button label="+ Add" onPress={() => openForm('freight', { goodsType: 'GENERAL', weightKg: '1', volumeM3: '1' })} /> : null}</View></View>
            <SearchInput value={search} onChangeText={setSearch} placeholder="Search freight, client, or city" />
            {(showArchived ? archivedGoods : goods).filter((item) => filtered(`${item.trackingCode} ${item.clientName} ${item.pickupCity} ${item.deliveryCity} ${item.status}`)).map((item) => (
              <Card key={item.id} title={`${item.trackingCode} · ${item.clientName}`} trailing={<Badge value={item.status} />}>
                <Text style={styles.bodyText}>{item.pickupCity} → {item.deliveryCity}</Text>
                <Text style={styles.mutedText}>{displayStatus(item.goodsType)} · {item.weightKg.toLocaleString()} kg · {item.volumeM3} m³ · {money(item.revenueEur)}</Text>
                {showArchived ? <Text style={styles.mutedText}>Archived; retained for route and financial history.</Text> : null}
                {!showArchived && item.status === 'UNASSIGNED' && !hasGoodsHistory(item.id) ? (
                  <View style={styles.rowActions}>
                    <LinkButton label="Edit" onPress={() => openForm('freight', item, { editingId: item.id })} />
                  </View>
                ) : null}
                {!showArchived && item.status !== 'ASSIGNED' && item.status !== 'IN_TRANSIT' ? <LinkButton label="Archive" danger onPress={() => confirm('Archive freight?', `Archive ${item.trackingCode}? Its financial and route records will be retained.`, () => void mutate(() => send(`/goods/${item.id}`, 'DELETE'), 'Freight archived; historical records retained.'))} /> : null}
              </Card>
            ))}
            {!(showArchived ? archivedGoods : goods).length && <Empty>{showArchived ? 'No archived freight.' : 'No freight records yet.'}</Empty>}
          </>
        )}

        {tab === 'Drivers' && (
          <>
            <View style={styles.headingRow}><Text style={styles.sectionIntro}>{showArchived ? archivedDrivers.length : drivers.length} {showArchived ? 'archived' : 'active'} driver records</Text><View style={styles.rowActions}><LinkButton label={showArchived ? 'Show active drivers' : 'View archived drivers'} onPress={() => setShowArchived((value) => !value)} />{!showArchived ? <Button label="+ Add" onPress={() => openForm('driver', { licenseCategories: 'B' })} /> : null}</View></View>
            <SearchInput value={search} onChangeText={setSearch} placeholder="Search drivers" />
            {(showArchived ? archivedDrivers : drivers).filter((driver) => filtered(`${driver.name} ${driver.email} ${driver.status}`)).map((driver) => (
              <Card key={driver.id} title={driver.name} trailing={<Badge value={driver.status} />}>
                <Text style={styles.bodyText}>{driver.email}</Text><Text style={styles.mutedText}>Licenses: {driver.licenseCategories.join(', ') || '—'}</Text>
                <View style={styles.rowActions}>
                  {!showArchived ? <LinkButton label="Driver details" onPress={() => setSelectedDriverId(driver.id)} /> : <Text style={styles.mutedText}>Archived; historical route and financial records retained.</Text>}
                  {!showArchived && !hasScheduledDriverRoute(driver.id) && driver.status !== 'IN_TRANSIT' && driver.status !== 'RETURNING'
                    ? <LinkButton label="Edit" onPress={() => openForm('driver', driver, { editingId: driver.id })} />
                    : null}
                  {!showArchived && !hasScheduledDriverRoute(driver.id) && (driver.status === 'AVAILABLE' || driver.status === 'ON_LEAVE')
                    ? <LinkButton label={driver.status === 'ON_LEAVE' ? 'Set available' : 'Set on leave'} onPress={() => void mutate(() => send(`/drivers/${driver.id}/status`, 'PATCH', { status: driver.status === 'ON_LEAVE' ? 'AVAILABLE' : 'ON_LEAVE' }), 'Driver status updated.')} />
                    : null}
                  {!showArchived && !hasScheduledDriverRoute(driver.id) && driver.status !== 'IN_TRANSIT' && driver.status !== 'RETURNING'
                    ? <LinkButton label="Archive" danger onPress={() => confirm('Archive driver?', `Archive ${driver.name}? Route and financial history will be retained.`, () => void mutate(() => send(`/drivers/${driver.id}`, 'DELETE'), 'Driver archived; historical records retained.'))} />
                    : null}
                </View>
              </Card>
            ))}
            {!(showArchived ? archivedDrivers : drivers).length && <Empty>{showArchived ? 'No archived drivers.' : 'No drivers yet.'}</Empty>}
          </>
        )}

        {tab === 'Fleet' && (
          <>
            <View style={styles.actionGrid}>
              <LinkButton label={showArchived ? 'Show active vehicles' : 'View archived vehicles'} onPress={() => setShowArchived((value) => !value)} />
              {!showArchived ? (
                <>
                  <Button label="+ Vehicle" onPress={() => openForm('vehicle', { vehicleType: 'VAN', transmissionType: 'MANUAL', year: new Date().getFullYear(), maxWeightCapacityKg: 1000, maxVolumeM3: 10, currentMileageKm: 0 })} />
                  <Button label="+ Trailer" onPress={() => openForm('trailer', { trailerType: 'BOX_TRAILER', compatibleVehicleType: 'VAN', year: new Date().getFullYear(), maxWeightCapacityKg: 1000, maxVolumeM3: 10 })} secondary />
                </>
              ) : null}
            </View>
            <SearchInput value={search} onChangeText={(value) => { setSearch(value); setFleetPage(1) }} placeholder="Search plate, make, model, class, status…" />
            <View style={styles.headingRow}>
              <Text style={styles.subheading}>{showArchived ? 'Archived vehicles' : 'Vehicles'} ({filteredFleetVehicles.length} of {showArchived ? archivedVehicles.length : vehicles.length})</Text>
              <View style={styles.fleetSortOptions}>
                {(['PLATE', 'MILEAGE', 'STATUS'] as const).map((sort) => (
                  <Pressable key={sort} onPress={() => { setFleetSort(sort); setFleetPage(1) }} style={[styles.fleetSortButton, fleetSort === sort && styles.fleetSortButtonSelected]}>
                    <Text style={[styles.fleetSortText, fleetSort === sort && styles.fleetSortTextSelected]}>{sort}</Text>
                  </Pressable>
                ))}
              </View>
            </View>
            {paginatedFleetVehicles.map((vehicle) => (
              <Card key={vehicle.id} title={`${vehicle.licensePlate} · ${vehicle.make} ${vehicle.model}`} trailing={<Badge value={vehicle.status} />}>
                <Text style={styles.mutedText}>{displayStatus(vehicle.vehicleType)} · {vehicle.year} · {vehicle.currentMileageKm.toLocaleString()} km · {vehicle.maxWeightCapacityKg.toLocaleString()} kg</Text>
                {vehicle.maintenanceAutoTriggered ? <Text style={styles.warningText}>Automatically held for maintenance.</Text> : null}
                {driverForVehicle(vehicle.id) ? <LinkButton label={`Driver details · ${driverForVehicle(vehicle.id)?.driverName}`} onPress={() => setSelectedDriverId(driverForVehicle(vehicle.id)?.driverId ?? null)} /> : null}
                <View style={styles.rowActions}>
                  <LinkButton label="Vehicle details" onPress={() => { setSelectedFleetVehicleId(vehicle.id); setFleetDetailTab('Shipments') }} />
                  {!showArchived && !hasScheduledVehicleRoute(vehicle.id) && vehicle.status !== 'IN_TRANSIT'
                    ? <LinkButton label="Edit" onPress={() => openForm('vehicle', vehicle, { editingId: vehicle.id })} />
                    : null}
                  {!showArchived && !hasScheduledVehicleRoute(vehicle.id) && (vehicle.status === 'AVAILABLE' || vehicle.status === 'MAINTENANCE')
                    ? <LinkButton label={vehicle.status === 'MAINTENANCE' ? 'Set available' : 'Maintenance'} onPress={() => {
                      if (vehicle.status === 'MAINTENANCE') {
                        confirm('Mark available?', 'Tracked service health will be reset at the current mileage.', () => void mutate(() => send(`/vehicles/${vehicle.id}/status`, 'PATCH', { status: 'AVAILABLE' }), 'Vehicle marked available.'))
                      } else {
                        void mutate(() => send(`/vehicles/${vehicle.id}/status`, 'PATCH', { status: 'MAINTENANCE' }), 'Vehicle status updated.')
                      }
                    }} />
                    : null}
                  {!showArchived && !hasScheduledVehicleRoute(vehicle.id) && vehicle.status !== 'IN_TRANSIT'
                    ? <LinkButton label="Archive" danger onPress={() => confirm('Archive vehicle?', `Archive ${vehicle.licensePlate}? Route and financial history will be retained.`, () => void mutate(() => send(`/vehicles/${vehicle.id}`, 'DELETE'), 'Vehicle archived; historical records retained.'))} />
                    : null}
                </View>
              </Card>
            ))}
            {filteredFleetVehicles.length > 10 ? (
              <View style={styles.fleetPagination}>
                <Text style={styles.mutedText}>Showing {(fleetPage - 1) * 10 + 1}–{Math.min(fleetPage * 10, filteredFleetVehicles.length)} of {filteredFleetVehicles.length}</Text>
                <View style={styles.rowActions}>
                  <LinkButton label="← Previous" disabled={fleetPage <= 1} onPress={() => setFleetPage((page) => Math.max(1, page - 1))} />
                  <Text style={styles.fleetPageLabel}>{fleetPage} / {fleetPageCount}</Text>
                  <LinkButton label="Next →" disabled={fleetPage >= fleetPageCount} onPress={() => setFleetPage((page) => Math.min(fleetPageCount, page + 1))} />
                </View>
              </View>
            ) : null}
            {!filteredFleetVehicles.length ? <Empty>{vehicles.length ? 'No vehicles match this search.' : 'No vehicles registered.'}</Empty> : null}
            <Text style={styles.subheading}>Trailers ({trailers.length})</Text>
            {trailers.filter((trailer) => filtered(`${trailer.licensePlate} ${trailer.make} ${trailer.model} ${trailer.status}`)).map((trailer) => (
              <Card key={trailer.id} title={`${trailer.licensePlate} · ${trailer.make} ${trailer.model}`} trailing={<Badge value={trailer.status} />}>
                <Text style={styles.mutedText}>{displayStatus(trailer.trailerType)} · compatible with {displayStatus(trailer.compatibleVehicleType)}</Text>
                {trailer.maintenanceAutoTriggered ? <Text style={styles.warningText}>Automatically held for maintenance until all overdue components are serviced.</Text> : null}
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
            <Card title="Workshop queue" subtitle={`${counts.dueVehicles} vehicle(s) and ${counts.dueTrailers} trailer(s) currently in maintenance`}>
              {!counts.dueVehicles && !counts.dueTrailers ? <Empty>No equipment currently due.</Empty> : null}
              {vehicles.filter((vehicle) => vehicle.status === 'MAINTENANCE').map((vehicle) => (
                <Text key={`vehicle-${vehicle.id}`} style={styles.bodyText}>{vehicle.licensePlate} · {vehicle.make} {vehicle.model} · vehicle</Text>
              ))}
              {trailers.filter((trailer) => trailer.status === 'MAINTENANCE').map((trailer) => (
                <Text key={`trailer-${trailer.id}`} style={styles.bodyText}>{trailer.licensePlate} · {trailer.make} {trailer.model} · trailer</Text>
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
                <TextInput value={chatInput} onChangeText={setChatInput} placeholder="Ask a general or logistics question…" placeholderTextColor="#6b7280" style={styles.chatInput} multiline />
                <Button label={chatBusy ? '…' : 'Send'} disabled={chatBusy || !chatInput.trim()} onPress={() => void sendAssistantMessage()} />
              </View>
            </View>
          </KeyboardAvoidingView>
        </View>
      </Modal>

      <Modal visible={selectedDriver !== null} transparent animationType="slide" onRequestClose={() => setSelectedDriverId(null)}>
        <View style={styles.modalScrim}>
          <View style={styles.modalFrame}>
            <View style={styles.modalHeader}>
              <Text style={styles.cardTitle}>{selectedDriver?.name ?? 'Driver details'}</Text>
              <Pressable onPress={() => setSelectedDriverId(null)} accessibilityLabel="Close driver details"><Text style={styles.closeText}>×</Text></Pressable>
            </View>
            {selectedDriver ? (
              <ScrollView contentContainerStyle={styles.modalContent}>
                <Badge value={selectedDriver.status} />
                <DataRow label="Email" value={selectedDriver.email} />
                <DataRow label="License categories" value={selectedDriver.licenseCategories.join(', ') || '—'} />
                <Text style={styles.subheading}>Route history ({driverRoutes.length})</Text>
                {driverRoutes.length
                  ? driverRoutes.map((route) => <DataRow key={route.id} label={`Route #${route.id} · ${displayStatus(route.status)}`} value={`${route.vehicleLicensePlate} · ${dateString(route.departureTime)}`} />)
                  : <Empty>No route history recorded.</Empty>}
              </ScrollView>
            ) : null}
          </View>
        </View>
      </Modal>
      <Modal visible={selectedFleetVehicle !== null} transparent animationType="slide" onRequestClose={() => setSelectedFleetVehicleId(null)}>
        <View style={styles.modalScrim}>
          <View style={styles.modalFrame}>
            <View style={styles.modalHeader}>
              <View style={styles.flex}>
                <Text style={styles.cardTitle}>{selectedFleetVehicle?.licensePlate} · {selectedFleetVehicle?.make} {selectedFleetVehicle?.model}</Text>
                <Text style={styles.mutedText}>{selectedFleetVehicle ? `${displayStatus(selectedFleetVehicle.vehicleType)} · ${selectedFleetVehicle.year} · ${selectedFleetVehicle.currentMileageKm.toLocaleString()} km` : ''}</Text>
              </View>
              <Pressable onPress={() => setSelectedFleetVehicleId(null)} accessibilityLabel="Close vehicle details"><Text style={styles.closeText}>×</Text></Pressable>
            </View>
            <View style={styles.detailTabs}>
              {(['Shipments', 'Maintenance', 'Profitability'] as const).map((item) => (
                <Pressable key={item} onPress={() => setFleetDetailTab(item)} style={[styles.detailTab, fleetDetailTab === item && styles.detailTabSelected]}>
                  <Text style={[styles.detailTabText, fleetDetailTab === item && styles.detailTabTextSelected]}>{item}</Text>
                </Pressable>
              ))}
            </View>
            {selectedFleetVehicle ? (
              <ScrollView contentContainerStyle={styles.modalContent}>
                {fleetDetailTab === 'Shipments' ? (
                  selectedFleetRoutes.length ? selectedFleetRoutes.map((route) => {
                    const deliveries = route.stops.filter((stop) => stop.type === 'DELIVERY')
                    return (
                      <View key={route.id} style={styles.vehicleHistoryCard}>
                        <View style={styles.headingRow}>
                          <Text style={styles.bodyText}>ROUTE #{route.id}</Text>
                          <Badge value={route.status} />
                        </View>
                        <Text style={styles.mutedText}>{dateString(route.departureTime)} · {(route.distanceMeters / 1000).toFixed(1)} km · {route.driverName}</Text>
                        {deliveries.map((stop) => {
                          const shipment = goods.find((item) => item.id === stop.goodsId)
                          return (
                            <View key={`${route.id}-${stop.goodsId}`} style={styles.vehicleShipmentRow}>
                              <Text style={styles.stopType}>{stop.trackingCode}</Text>
                              <Text style={styles.bodyText}>{shipment?.clientName ?? 'Shipment'} · {stop.city}</Text>
                              <Text style={styles.mutedText}>{shipment ? `${displayStatus(shipment.status)} · ${shipment.pickupCity} → ${shipment.deliveryCity}` : 'Shipment details unavailable'}</Text>
                            </View>
                          )
                        })}
                        {!deliveries.length ? <Text style={styles.mutedText}>No shipment stops recorded for this route.</Text> : null}
                      </View>
                    )
                  }) : <Empty>No shipment history recorded for this vehicle.</Empty>
                ) : null}
                {fleetDetailTab === 'Maintenance' ? (
                  <>
                    {selectedFleetVehicle.maintenanceComponents.length ? selectedFleetVehicle.maintenanceComponents.map((component) => (
                      <View key={component.key} style={styles.vehicleHistoryCard}>
                        <View style={styles.headingRow}>
                          <Text style={styles.bodyText}>{component.label}</Text>
                          <Text style={styles.mutedText}>{component.healthPercent == null ? 'HISTORY ONLY' : `${Math.round(component.healthPercent)}% HEALTH`}</Text>
                        </View>
                        {component.healthPercent != null ? <View style={styles.healthTrack}><View style={[styles.healthBar, { width: `${Math.max(0, Math.min(100, component.healthPercent))}%` }]} /></View> : null}
                        <Text style={styles.mutedText}>{component.lastPerformedAt ? `Last service: ${dateString(component.lastPerformedAt)}` : 'No service history recorded'}{component.lastOdometerKm == null ? '' : ` · ${component.lastOdometerKm.toLocaleString()} km`}{component.lastCostEur == null ? '' : ` · ${money(component.lastCostEur)}`}</Text>
                        <Text style={styles.mutedText}>{component.distanceRemainingKm == null ? component.inspectionNote ?? 'No interval data' : `${component.distanceRemainingKm.toLocaleString()} km remaining`}{component.daysRemaining == null ? '' : ` · ${component.daysRemaining} days`}</Text>
                        {!selectedFleetVehicle.archived ? <LinkButton label="Record service" disabled={hasScheduledVehicleRoute(selectedFleetVehicle.id)} onPress={() => { setSelectedFleetVehicleId(null); openMaintenance(selectedFleetVehicle, component) }} /> : null}
                      </View>
                    )) : <Empty>No maintenance components recorded.</Empty>}
                  </>
                ) : null}
                {fleetDetailTab === 'Profitability' ? (
                  <View style={styles.vehicleHistoryCard}>
                    <Text style={styles.stopType}>REPORTING PERIOD · {finance?.from ?? fromDate} — {finance?.to ?? toDate}</Text>
                    {selectedVehicleProfitability ? (
                      <>
                        <DataRow label="Freight revenue" value={money(selectedVehicleProfitability.freightRevenueEur)} />
                        <DataRow label="Maintenance costs" value={money(selectedVehicleProfitability.maintenanceCostsEur)} />
                        <DataRow label="Net profit" value={money(selectedVehicleProfitability.netProfitEur)} />
                        <Text style={styles.mutedText}>Net profit is delivered freight revenue less recorded maintenance. Fuel, wages, tolls, insurance, and other costs are excluded.</Text>
                      </>
                    ) : <Empty>{finance ? 'No financial activity recorded for this vehicle in the selected period.' : 'Profitability report is not available.'}</Empty>}
                  </View>
                ) : null}
              </ScrollView>
            ) : null}
          </View>
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
      confirm('Archive route?', `Archive route #${route.id}? It will remain in route history and financial analysis.`, () => void mutate(() => send(`/routes/${route.id}`, 'DELETE'), 'Route archived; history retained.'))
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

function ActiveRouteCard({
  route,
  position,
  onDriverPress,
}: {
  route: Route
  position?: RouteSimulationPosition
  onDriverPress: (driverId: number) => void
}) {
  const [mapVisible, setMapVisible] = useState(true)
  const [detailsVisible, setDetailsVisible] = useState(false)
  const valid = route.geometry.filter((point) => Number.isFinite(point.latitude) && Number.isFinite(point.longitude))
  return (
    <View style={styles.activeRouteCard}>
      <View style={styles.activeRouteHeading}>
        <View style={styles.flex}>
          <Pressable onPress={() => onDriverPress(route.driverId)} accessibilityRole="button">
            <Text style={styles.cardTitle}>Route #{route.id} · {route.vehicleLicensePlate}</Text>
          </Pressable>
          <Text style={styles.mutedText}>{position ? `${Math.round(position.progress * 100)}% complete · ` : ''}simulated, not GPS</Text>
        </View>
        <View style={styles.activeRouteActions}>
          <LinkButton label={detailsVisible ? 'Hide details' : 'Details'} onPress={() => setDetailsVisible((current) => !current)} />
          <LinkButton label={mapVisible ? 'Hide map' : 'Show map'} onPress={() => setMapVisible((current) => !current)} />
        </View>
      </View>
      {detailsVisible ? (
        <View style={styles.stops}>
          <LinkButton label={`Driver · ${route.driverName}`} onPress={() => onDriverPress(route.driverId)} />
          <Text style={styles.mutedText}>{route.vehicleLicensePlate}{route.trailerLicensePlate ? ` · ${route.trailerLicensePlate}` : ''} · departs {dateString(route.departureTime)}</Text>
          {route.stops.map((stop) => (
            <View key={`${stop.goodsId}-${stop.type}`} style={styles.stopRow}>
              <Text style={styles.stopType}>{stop.stopOrder}. {stop.type === 'PICKUP' ? 'Pickup' : 'Delivery'}</Text>
              <Text style={styles.bodyText}>{stop.city}</Text>
              <Text style={styles.mutedText}>{stop.trackingCode}</Text>
            </View>
          ))}
        </View>
      ) : null}
      {mapVisible && valid.length
        ? <View style={styles.mapCard}><RouteMap coordinates={valid} position={position} /></View>
        : mapVisible ? <Empty>No saved route geometry is available.</Empty> : null}
    </View>
  )
}

function RouteCard({ route, goods, onAction, onEdit, onDriverPress, expanded }: {
  route: Route
  goods: Goods[]
  onAction: (route: Route, action: 'start' | 'cancel' | 'delete' | 'deliver', goodsId?: number) => void
  onEdit?: (route: Route) => void
  onDriverPress: (driverId: number) => void
  expanded?: boolean
}) {
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
      {route.archived ? <Text style={styles.mutedText}>Archived route history · retained for financial analysis</Text> : null}
      <View style={styles.routeCrewRow}>
        <LinkButton label={`Driver · ${route.driverName}`} onPress={() => onDriverPress(route.driverId)} />
        <Text style={styles.bodyText}>{route.vehicleLicensePlate}{route.trailerLicensePlate ? ` · ${route.trailerLicensePlate}` : ''}</Text>
      </View>
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
      {route.status === 'PLANNED' && !route.archived ? <View style={styles.rowActions}>{onEdit ? <LinkButton label="Edit route" onPress={() => onEdit(route)} /> : null}<Button label="Start route" onPress={() => onAction(route, 'start')} /><Button label="Cancel" secondary onPress={() => onAction(route, 'cancel')} /><LinkButton label="Archive" danger onPress={() => onAction(route, 'delete')} /></View> : null}
      {route.status === 'CANCELLED' && !route.archived
        ? <View style={styles.rowActions}><LinkButton label="Archive" danger onPress={() => onAction(route, 'delete')} /></View>
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
      { key: 'volumeM3', label: 'Volume (m³)', keyboard: 'decimal-pad' }, { key: 'revenueEur', label: 'Revenue (€), optional', keyboard: 'decimal-pad' },
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
      && !trailer.maintenanceAutoTriggered
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
  activeRouteCard: { gap: 8, borderTopWidth: 1, borderTopColor: '#2d333b', paddingTop: 10 },
  activeRouteHeading: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 },
  activeRouteActions: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'flex-end' },
  routeCrewRow: { flexDirection: 'row', alignItems: 'center', flexWrap: 'wrap', gap: 6 },
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
  fleetSortOptions: { flexDirection: 'row', flexWrap: 'wrap', gap: 4 },
  fleetSortButton: { minHeight: 28, justifyContent: 'center', borderWidth: 1, borderColor: '#374151', paddingHorizontal: 7, backgroundColor: '#111416' },
  fleetSortButtonSelected: { borderColor: ACCENT, backgroundColor: '#241d10' },
  fleetSortText: { color: '#9ca3af', fontSize: 8, fontWeight: '700', fontFamily: 'monospace' },
  fleetSortTextSelected: { color: ACCENT },
  fleetPagination: { flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between', gap: 8, borderTopWidth: 1, borderTopColor: '#2d333b', paddingTop: 9 },
  fleetPageLabel: { color: '#f3f4f6', fontSize: 9, fontWeight: '700', fontFamily: 'monospace' },
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
  detailTabs: { flexDirection: 'row', borderBottomWidth: 1, borderBottomColor: '#2d333b', backgroundColor: '#111416' },
  detailTab: { minHeight: 42, flex: 1, alignItems: 'center', justifyContent: 'center', borderBottomWidth: 2, borderBottomColor: 'transparent', paddingHorizontal: 4 },
  detailTabSelected: { borderBottomColor: ACCENT },
  detailTabText: { color: '#9ca3af', fontSize: 8, fontWeight: '700', fontFamily: 'monospace', textTransform: 'uppercase' },
  detailTabTextSelected: { color: ACCENT },
  vehicleHistoryCard: { gap: 8, borderWidth: 1, borderColor: '#2d333b', padding: 11, backgroundColor: '#111416' },
  vehicleShipmentRow: { gap: 4, borderTopWidth: 1, borderTopColor: '#2d333b', paddingTop: 8 },
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
