import { useCallback, useEffect, useMemo, useState } from 'react'
import { StatusBar } from 'expo-status-bar'
import { router, useFocusEffect, useLocalSearchParams } from 'expo-router'
import {
  Alert,
  KeyboardAvoidingView,
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
  type Vehicle,
  type VehicleMaintenanceComponent,
  send,
  request,
} from '../api'

type Tab = 'Home' | 'Routes' | 'Freight' | 'Drivers' | 'Fleet' | 'Service' | 'Finance' | 'Assistant'
type FormKind = 'freight' | 'driver' | 'vehicle' | 'trailer' | 'route' | 'maintenance'
type FormModal = {
  kind: FormKind
  values: Record<string, string>
  editingId?: number
  vehicle?: Vehicle
  component?: VehicleMaintenanceComponent
}
type ChatLine = AssistantMessage & { action?: AssistantAction }

const TABS: Tab[] = ['Home', 'Routes', 'Freight', 'Drivers', 'Fleet', 'Service', 'Finance', 'Assistant']
const PURPLE = '#635bdb'
const NAVY = '#13213c'
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
  const [fromDate, setFromDate] = useState(() => new Date(new Date().getFullYear(), new Date().getMonth(), 1).toISOString().slice(0, 10))
  const [toDate, setToDate] = useState(() => new Date().toISOString().slice(0, 10))
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

  const counts = useMemo(() => ({
    activeRoutes: routes.filter((route) => route.status === 'ACTIVE').length,
    availableDrivers: drivers.filter((driver) => driver.status === 'AVAILABLE').length,
    availableVehicles: vehicles.filter((vehicle) => vehicle.status === 'AVAILABLE' && !vehicle.maintenanceAutoTriggered).length,
    unassignedGoods: goods.filter((item) => item.status === 'UNASSIGNED').length,
    dueVehicles: vehicles.filter((vehicle) => vehicle.status === 'MAINTENANCE').length,
  }), [drivers, goods, routes, vehicles])

  function openForm(kind: FormKind, value: object = {}, extra: Partial<FormModal> = {}) {
    setError('')
    setModal({ kind, values: toFormValues(value), ...extra })
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
    setModal((current) => current ? { ...current, values: { ...current.values, [key]: value } } : current)
  }

  async function saveForm() {
    if (!modal) return
    const values = modal.values
    const id = modal.editingId
    const number = (key: string) => Number(values[key])
    let path = ''
    let method = id === undefined ? 'POST' : 'PUT'
    let payload: Record<string, unknown> = {}

    switch (modal.kind) {
      case 'freight':
        path = id === undefined ? '/goods' : `/goods/${id}`
        payload = {
          clientName: values.clientName?.trim(),
          goodsType: values.goodsType,
          description: values.description?.trim() || null,
          weightKg: number('weightKg'),
          revenueEur: number('revenueEur'),
          volumeM3: number('volumeM3'),
          pickupCity: values.pickupCity?.trim(),
          deliveryCity: values.deliveryCity?.trim(),
        }
        break
      case 'driver':
        path = id === undefined ? '/drivers' : `/drivers/${id}`
        payload = {
          name: values.name?.trim(),
          email: values.email?.trim(),
          licenseCategories: (values.licenseCategories ?? '').split(',').map((item) => item.trim()).filter(Boolean),
        }
        break
      case 'vehicle':
        path = id === undefined ? '/vehicles' : `/vehicles/${id}`
        payload = {
          licensePlate: values.licensePlate?.trim().toUpperCase(),
          vehicleType: values.vehicleType,
          make: values.make?.trim(),
          model: values.model?.trim(),
          year: number('year'),
          maxWeightCapacityKg: number('maxWeightCapacityKg'),
          maxVolumeM3: number('maxVolumeM3'),
          currentMileageKm: number('currentMileageKm'),
          transmissionType: values.transmissionType,
        }
        break
      case 'trailer':
        path = id === undefined ? '/trailers' : `/trailers/${id}`
        payload = {
          licensePlate: values.licensePlate?.trim().toUpperCase(),
          trailerType: values.trailerType,
          compatibleVehicleType: values.compatibleVehicleType,
          make: values.make?.trim(),
          model: values.model?.trim(),
          year: number('year'),
          maxWeightCapacityKg: number('maxWeightCapacityKg'),
          maxVolumeM3: number('maxVolumeM3'),
        }
        break
      case 'route': {
        const ids = (values.goodsIds ?? '').split(',').map((item) => Number(item.trim())).filter((item) => Number.isSafeInteger(item) && item > 0)
        if (!ids.length || ids.length > 12) {
          setError('Enter between 1 and 12 unassigned freight IDs, separated by commas.')
          return
        }
        if (ids.some((goodsId) => !goods.some((item) => item.id === goodsId && item.status === 'UNASSIGNED'))) {
          setError('Every freight ID must belong to an unassigned freight item.')
          return
        }
        path = '/routes'
        payload = {
          driverId: number('driverId'),
          vehicleId: number('vehicleId'),
          trailerId: values.trailerId ? number('trailerId') : null,
          departureTime: new Date(values.departureTime).toISOString(),
          stops: [
            ...ids.map((goodsId) => ({ goodsId, type: 'PICKUP' })),
            ...ids.map((goodsId) => ({ goodsId, type: 'DELIVERY' })),
          ],
        }
        break
      }
      case 'maintenance':
        if (!modal.vehicle || !modal.component) return
        path = `/vehicles/${modal.vehicle.id}/maintenance`
        method = 'POST'
        payload = {
          componentKey: modal.component.key,
          performedAt: new Date(values.performedAt).toISOString(),
          odometerKm: number('odometerKm'),
          costEur: number('costEur'),
        }
        break
    }

    await mutate(() => send(path, method, payload), `${modal.kind[0]?.toUpperCase()}${modal.kind.slice(1)} ${id === undefined ? 'added' : 'updated'}.`)
  }

  function openMaintenance(vehicle: Vehicle, component: VehicleMaintenanceComponent) {
    openForm('maintenance', {
      performedAt: new Date().toISOString(),
      odometerKm: vehicle.currentMileageKm,
      costEur: '',
    }, { vehicle, component })
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
      const reply = await send<AssistantReply>('/assistant/chat', 'POST', {
        messages: history,
        context: { drivers, vehicles, trailers, goods, routes },
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
        refreshControl={<RefreshControl refreshing={loading} onRefresh={() => void refresh()} tintColor={PURPLE} />}
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
                <Button label="+ Freight" onPress={() => { setTab('Freight'); openForm('freight', { goodsType: 'GENERAL', weightKg: '1', volumeM3: '1', revenueEur: '0' }) }} />
                <Button label="+ Driver" onPress={() => { setTab('Drivers'); openForm('driver', { licenseCategories: 'B' }) }} secondary />
                <Button label="+ Vehicle" onPress={() => { setTab('Fleet'); openForm('vehicle', { vehicleType: 'VAN', transmissionType: 'MANUAL', year: new Date().getFullYear(), maxWeightCapacityKg: 1000, maxVolumeM3: 10, currentMileageKm: 0 }) }} secondary />
              </View>
            </Card>
          </>
        )}

        {tab === 'Routes' && (
          <>
            <View style={styles.headingRow}><Text style={styles.sectionIntro}>Saved routes, stop order, and route-map simulation.</Text><Button label="+ Plan" onPress={() => openForm('route', { departureTime: new Date(Date.now() + 60_000).toISOString(), goodsIds: '' })} /></View>
            <Card title="Active route map" subtitle="Positions are simulated from saved road geometry, not GPS.">
              {routes.filter((route) => route.status === 'ACTIVE').length === 0
                ? <Empty>No active routes.</Empty>
                : routes.filter((route) => route.status === 'ACTIVE').map((route) => <RouteMapCard key={route.id} route={route} position={simulationPositions[route.id]} />)}
            </Card>
            {routes.map((route) => <RouteCard key={route.id} route={route} goods={goods} onAction={handleRouteAction} expanded />)}
          </>
        )}

        {tab === 'Freight' && (
          <>
            <View style={styles.headingRow}><Text style={styles.sectionIntro}>{goods.length} shipment records</Text><Button label="+ Add" onPress={() => openForm('freight', { goodsType: 'GENERAL', weightKg: '1', volumeM3: '1', revenueEur: '0' })} /></View>
            <SearchInput value={search} onChangeText={setSearch} placeholder="Search freight, client, or city" />
            {goods.filter((item) => filtered(`${item.trackingCode} ${item.clientName} ${item.pickupCity} ${item.deliveryCity} ${item.status}`)).map((item) => (
              <Card key={item.id} title={`${item.trackingCode} · ${item.clientName}`} trailing={<Badge value={item.status} />}>
                <Text style={styles.bodyText}>{item.pickupCity} → {item.deliveryCity}</Text>
                <Text style={styles.mutedText}>{displayStatus(item.goodsType)} · {item.weightKg.toLocaleString()} kg · {item.volumeM3} m³ · {money(item.revenueEur)}</Text>
                <View style={styles.rowActions}>
                  <LinkButton label="Edit" onPress={() => openForm('freight', item, { editingId: item.id })} />
                  <LinkButton label="Delete" danger onPress={() => confirm('Delete freight?', `Delete ${item.trackingCode}?`, () => void mutate(() => send(`/goods/${item.id}`, 'DELETE'), 'Freight deleted.'))} />
                </View>
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
                  <LinkButton label="Edit" onPress={() => openForm('driver', driver, { editingId: driver.id })} />
                  <LinkButton label={driver.status === 'ON_LEAVE' ? 'Set available' : 'Set on leave'} onPress={() => void mutate(() => send(`/drivers/${driver.id}/status`, 'PATCH', { status: driver.status === 'ON_LEAVE' ? 'AVAILABLE' : 'ON_LEAVE' }), 'Driver status updated.')} />
                  <LinkButton label="Delete" danger onPress={() => confirm('Delete driver?', `Delete ${driver.name}?`, () => void mutate(() => send(`/drivers/${driver.id}`, 'DELETE'), 'Driver deleted.'))} />
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
                  <LinkButton label="Edit" onPress={() => openForm('vehicle', vehicle, { editingId: vehicle.id })} />
                  <LinkButton label={vehicle.status === 'MAINTENANCE' ? 'Set available' : 'Maintenance'} onPress={() => {
                    if (vehicle.status === 'MAINTENANCE') {
                      confirm('Mark available?', 'Tracked service health will be reset at the current mileage.', () => void mutate(() => send(`/vehicles/${vehicle.id}/status`, 'PATCH', { status: 'AVAILABLE' }), 'Vehicle marked available.'))
                    } else {
                      void mutate(() => send(`/vehicles/${vehicle.id}/status`, 'PATCH', { status: 'MAINTENANCE' }), 'Vehicle status updated.')
                    }
                  }} />
                  <LinkButton label="Delete" danger onPress={() => confirm('Delete vehicle?', `Delete ${vehicle.licensePlate}?`, () => void mutate(() => send(`/vehicles/${vehicle.id}`, 'DELETE'), 'Vehicle deleted.'))} />
                </View>
              </Card>
            ))}
            <Text style={styles.subheading}>Trailers ({trailers.length})</Text>
            {trailers.filter((trailer) => filtered(`${trailer.licensePlate} ${trailer.make} ${trailer.model} ${trailer.status}`)).map((trailer) => (
              <Card key={trailer.id} title={`${trailer.licensePlate} · ${trailer.make} ${trailer.model}`} trailing={<Badge value={trailer.status} />}>
                <Text style={styles.mutedText}>{displayStatus(trailer.trailerType)} · compatible with {displayStatus(trailer.compatibleVehicleType)}</Text>
                <View style={styles.rowActions}>
                  <LinkButton label="Edit" onPress={() => openForm('trailer', trailer, { editingId: trailer.id })} />
                  <LinkButton label={trailer.status === 'MAINTENANCE' ? 'Set available' : 'Maintenance'} onPress={() => void mutate(() => send(`/trailers/${trailer.id}/status`, 'PATCH', { status: trailer.status === 'MAINTENANCE' ? 'AVAILABLE' : 'MAINTENANCE' }), 'Trailer status updated.')} />
                  <LinkButton label="Delete" danger onPress={() => confirm('Delete trailer?', `Delete ${trailer.licensePlate}?`, () => void mutate(() => send(`/trailers/${trailer.id}`, 'DELETE'), 'Trailer deleted.'))} />
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
                    <View style={styles.componentHeading}><Text style={styles.bodyText}>{component.label}</Text><Text style={styles.mutedText}>{component.healthPercent == null ? component.inspectionNote ?? 'No fixed interval' : `${Math.round(component.healthPercent)}%`}</Text></View>
                    {component.healthPercent != null ? <View style={styles.healthTrack}><View style={[styles.healthBar, { width: `${Math.max(0, Math.min(100, component.healthPercent))}%` }]} /></View> : null}
                    <Text style={styles.mutedText}>{component.distanceRemainingKm == null ? '' : `${component.distanceRemainingKm.toLocaleString()} km remaining`}{component.daysRemaining == null ? '' : ` · ${component.daysRemaining} days`}</Text>
                    <LinkButton label="Record service" onPress={() => openMaintenance(vehicle, component)} />
                  </View>
                ))}
                {vehicle.status === 'MAINTENANCE' && <Button label="Mark vehicle available" secondary onPress={() => confirm('Mark available?', 'Tracked service health will be reset at the current mileage.', () => void mutate(() => send(`/vehicles/${vehicle.id}/status`, 'PATCH', { status: 'AVAILABLE' }), 'Vehicle marked available.'))} />}
              </Card>
            ))}
            {!vehicles.length && <Empty>No vehicle maintenance data.</Empty>}
          </>
        )}

        {tab === 'Finance' && (
          <>
            <Card title="Reporting period" subtitle="Revenue less recorded maintenance costs. Other operating costs are excluded.">
              <View style={styles.dateFields}>
                <Field label="From (YYYY-MM-DD)" value={fromDate} onChangeText={setFromDate} />
                <Field label="To (YYYY-MM-DD)" value={toDate} onChangeText={setToDate} />
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

        {tab === 'Assistant' && (
          <>
            <Card title="AI operations assistant" subtitle="Proposals require your confirmation before changes are sent to the backend.">
              <Text style={styles.warningText}>Assistant prompts and the operational snapshot are sent to the backend&apos;s configured Ollama service.</Text>
            </Card>
            <View style={styles.chatList}>
              {!chat.length && <Empty>Ask about routes, freight, drivers, vehicles, or maintenance.</Empty>}
              {chat.map((message, index) => (
                <View key={`${message.role}-${index}`} style={[styles.chatBubble, message.role === 'user' ? styles.userBubble : styles.assistantBubble]}>
                  <Text style={styles.bodyText}>{message.content}</Text>
                  {message.action ? <View style={styles.actionProposal}><Text style={styles.proposalTitle}>{message.action.summary}</Text><Button label="Confirm proposed change" disabled={chatBusy} onPress={() => void confirmAssistantAction()} /></View> : null}
                </View>
              ))}
            </View>
            {pendingAction ? <Button label="Confirm latest proposal" disabled={chatBusy} onPress={() => void confirmAssistantAction()} /> : null}
            <View style={styles.chatComposer}>
              <TextInput value={chatInput} onChangeText={setChatInput} placeholder="Ask the operations assistant…" style={styles.chatInput} multiline />
              <Button label={chatBusy ? '…' : 'Send'} disabled={chatBusy || !chatInput.trim()} onPress={() => void sendAssistantMessage()} />
            </View>
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

      {modal ? (
        <FormEditor modal={modal} busy={busy} error={error} onChange={updateField} onClose={() => setModal(null)} onSave={() => void saveForm()} drivers={drivers} vehicles={vehicles} trailers={trailers} goods={goods} />
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

function LinkButton({ label, onPress, danger }: { label: string; onPress: () => void; danger?: boolean }) {
  return <Pressable onPress={onPress} style={styles.linkButton}><Text style={[styles.linkText, danger && styles.dangerLink]}>{label}</Text></Pressable>
}

function SearchInput({ value, onChangeText, placeholder }: { value: string; onChangeText: (value: string) => void; placeholder: string }) {
  return <TextInput value={value} onChangeText={onChangeText} placeholder={placeholder} style={styles.searchInput} autoCapitalize="none" />
}

function Field({ label, value, onChangeText, keyboardType }: { label: string; value: string; onChangeText: (value: string) => void; keyboardType?: 'default' | 'email-address' | 'numeric' | 'decimal-pad' }) {
  return <View style={styles.field}><Text style={styles.fieldLabel}>{label}</Text><TextInput value={value} onChangeText={onChangeText} style={styles.fieldInput} keyboardType={keyboardType ?? 'default'} autoCapitalize={keyboardType === 'email-address' ? 'none' : 'sentences'} /></View>
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

function RouteCard({ route, goods, onAction, expanded }: { route: Route; goods: Goods[]; onAction: (route: Route, action: 'start' | 'cancel' | 'delete' | 'deliver', goodsId?: number) => void; expanded?: boolean }) {
  const pendingDeliveries = route.stops.filter((stop) => stop.type === 'DELIVERY' && goods.find((item) => item.id === stop.goodsId)?.status === 'IN_TRANSIT')
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
      {route.status === 'PLANNED' ? <View style={styles.rowActions}><Button label="Start route" onPress={() => onAction(route, 'start')} /><Button label="Cancel" secondary onPress={() => onAction(route, 'cancel')} /><LinkButton label="Delete" danger onPress={() => onAction(route, 'delete')} /></View> : null}
      {route.status === 'ACTIVE' ? pendingDeliveries.map((stop) => <View key={stop.goodsId} style={styles.deliveryRow}><Text style={styles.bodyText}>{stop.trackingCode} · {stop.city}</Text><Button label="Confirm delivery" onPress={() => onAction(route, 'deliver', stop.goodsId)} /></View>) : null}
    </Card>
  )
}

function FormEditor({ modal, busy, error, onChange, onClose, onSave, drivers, vehicles, trailers, goods }: {
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
    route: [
      { key: 'driverId', label: `Driver ID (${drivers.filter((item) => item.status === 'AVAILABLE').map((item) => `${item.id} ${item.name}`).join('; ') || 'none available'})`, keyboard: 'numeric' },
      { key: 'vehicleId', label: `Vehicle ID (${vehicles.filter((item) => item.status === 'AVAILABLE').map((item) => `${item.id} ${item.licensePlate}`).join('; ') || 'none available'})`, keyboard: 'numeric' },
      { key: 'trailerId', label: `Trailer ID, if required (${trailers.filter((item) => item.status === 'AVAILABLE').map((item) => `${item.id} ${item.licensePlate}`).join('; ') || 'none'})`, keyboard: 'numeric' },
      { key: 'departureTime', label: 'Departure date/time (ISO 8601)' },
      { key: 'goodsIds', label: `Unassigned freight IDs, comma separated (${goods.filter((item) => item.status === 'UNASSIGNED').map((item) => `${item.id} ${item.trackingCode}`).join('; ') || 'none'})` },
    ],
    maintenance: [
      { key: 'performedAt', label: 'Service date/time (ISO 8601)' },
      { key: 'odometerKm', label: 'Odometer (km)', keyboard: 'numeric' },
      { key: 'costEur', label: 'Service cost (€)', keyboard: 'decimal-pad' },
    ],
  }
  const title = modal.kind === 'maintenance'
    ? `Record ${modal.component?.label ?? 'service'} · ${modal.vehicle?.licensePlate ?? ''}`
    : `${modal.editingId === undefined ? 'Add' : 'Edit'} ${modal.kind}`

  return (
    <View style={styles.modalScrim}>
      <KeyboardAvoidingView style={styles.modalFrame} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <View style={styles.modalHeader}><Text style={styles.cardTitle}>{title}</Text><Pressable onPress={onClose}><Text style={styles.closeText}>×</Text></Pressable></View>
        <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={styles.modalContent}>
          {modal.kind === 'route' ? <Text style={styles.warningText}>The server calculates road distance and arrival time. Pickups are ordered before deliveries; the backend validates vehicle, driver, cargo, and trailer compatibility.</Text> : null}
          {modal.kind === 'maintenance' ? <Text style={styles.warningText}>Odometer must not exceed the vehicle&apos;s current mileage. Use an ISO date/time such as 2026-10-01T08:00:00Z.</Text> : null}
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
              ) : <Field label={field.label} value={modal.values[field.key] ?? ''} onChangeText={(value) => onChange(field.key, value)} keyboardType={field.keyboard} />}
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
  app: { flex: 1, backgroundColor: '#f5f7fb' },
  topbar: { minHeight: 66, paddingHorizontal: 18, backgroundColor: NAVY, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  brandRow: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  brandMark: { width: 36, height: 36, backgroundColor: PURPLE, borderRadius: 10, justifyContent: 'center', alignItems: 'center' },
  brandMarkText: { color: '#fff', fontWeight: '800', fontSize: 13 },
  brandName: { color: '#fff', fontWeight: '800', fontSize: 13, letterSpacing: 1 },
  brandSubtitle: { color: '#9da9bf', fontWeight: '600', fontSize: 9, marginTop: 2, letterSpacing: 1 },
  refreshButton: { width: 36, height: 36, borderRadius: 8, backgroundColor: '#263653', alignItems: 'center', justifyContent: 'center' },
  refreshText: { color: '#fff', fontSize: 22 },
  pageTitle: { paddingHorizontal: 18, paddingTop: 18, paddingBottom: 12 },
  eyebrow: { color: '#8a83e5', fontSize: 9, fontWeight: '800', letterSpacing: 1.4 },
  title: { color: '#19243b', fontSize: 23, fontWeight: '700', marginTop: 5 },
  content: { flex: 1 },
  contentInner: { padding: 14, paddingBottom: 28, gap: 12 },
  message: { marginHorizontal: 14, marginBottom: 4, padding: 11, borderWidth: 1, borderRadius: 8 },
  errorMessage: { borderColor: '#efb8bf', backgroundColor: '#fff0f1' },
  successMessage: { borderColor: '#b6e4d1', backgroundColor: '#e9f8f1' },
  messageText: { color: '#465168', fontSize: 12 },
  statsGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 9 },
  statCard: { flexBasis: '48%', flexGrow: 1, minHeight: 88, backgroundColor: '#fff', borderRadius: 10, borderWidth: 1, borderColor: '#e9edf3', padding: 13 },
  statLabel: { color: '#7f899e', fontSize: 11, fontWeight: '600' },
  statValue: { fontSize: 23, fontWeight: '700', marginTop: 8 },
  purpleText: { color: PURPLE }, greenText: { color: '#1ca376' }, blueText: { color: '#3381ca' }, orangeText: { color: '#d28b2b' },
  card: { backgroundColor: '#fff', borderWidth: 1, borderColor: '#e9edf3', borderRadius: 10, padding: 14, gap: 10, shadowColor: '#162341', shadowOpacity: 0.025, shadowRadius: 5 },
  cardHeading: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 8, paddingBottom: 9, borderBottomWidth: 1, borderBottomColor: '#eff1f5' },
  cardTitle: { color: '#202a41', fontSize: 14, fontWeight: '700' },
  mutedText: { color: '#8c95a6', fontSize: 11, lineHeight: 16 },
  bodyText: { color: '#3c4960', fontSize: 12, lineHeight: 18 },
  emptyText: { paddingVertical: 8, color: '#929bad', fontSize: 12, lineHeight: 18 },
  flex: { flex: 1 },
  badge: { borderRadius: 14, paddingHorizontal: 9, paddingVertical: 5, alignSelf: 'flex-start' },
  badgeGreen: { backgroundColor: '#e8f7ef' }, badgeBlue: { backgroundColor: '#ebf3ff' }, badgeRed: { backgroundColor: '#fff0f0' }, badgeGray: { backgroundColor: '#f0f2f5' },
  badgeText: { color: '#58647a', fontSize: 9, fontWeight: '700', textTransform: 'capitalize' },
  button: { minHeight: 38, paddingHorizontal: 13, borderRadius: 7, backgroundColor: PURPLE, alignItems: 'center', justifyContent: 'center' },
  buttonText: { color: '#fff', fontSize: 11, fontWeight: '700' },
  secondaryButton: { borderWidth: 1, borderColor: '#e4e7f0', backgroundColor: '#fff' },
  secondaryButtonText: { color: '#49536b' },
  dangerButton: { backgroundColor: '#fff0f1', borderWidth: 1, borderColor: '#efb8bf' },
  dangerButtonText: { color: '#a84352' },
  disabledButton: { opacity: 0.5 },
  linkButton: { minHeight: 34, justifyContent: 'center', paddingHorizontal: 5 },
  linkText: { color: PURPLE, fontSize: 11, fontWeight: '700' },
  dangerLink: { color: '#bd5b66' },
  headingRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 },
  sectionIntro: { flex: 1, color: '#7f899e', fontSize: 11 },
  actionGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  rowActions: { flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', gap: 6, marginTop: 2 },
  searchInput: { minHeight: 42, borderRadius: 8, borderWidth: 1, borderColor: '#e5e8ef', backgroundColor: '#fff', paddingHorizontal: 12, color: '#29344b', fontSize: 12 },
  subheading: { color: '#34405a', fontSize: 13, fontWeight: '700', marginTop: 4 },
  warningText: { color: '#9a6a22', backgroundColor: '#fff7e9', padding: 9, borderRadius: 7, fontSize: 11, lineHeight: 16 },
  componentRow: { gap: 5, borderTopWidth: 1, borderTopColor: '#f0f2f6', paddingTop: 9 },
  componentHeading: { flexDirection: 'row', justifyContent: 'space-between', gap: 8 },
  healthTrack: { height: 6, borderRadius: 3, backgroundColor: '#eef1f5', overflow: 'hidden' },
  healthBar: { height: 6, borderRadius: 3, backgroundColor: '#1ca376' },
  dateFields: { flexDirection: 'row', gap: 8 },
  field: { gap: 5, marginBottom: 3 },
  fieldLabel: { color: '#5d6880', fontSize: 10, fontWeight: '600' },
  fieldInput: { minHeight: 40, borderWidth: 1, borderColor: '#e5e8ef', borderRadius: 7, paddingHorizontal: 10, color: '#29344b', backgroundColor: '#fff', fontSize: 12 },
  dataRow: { paddingVertical: 7, borderTopWidth: 1, borderTopColor: '#f0f2f6', gap: 2 },
  mapCard: { gap: 7 },
  stops: { borderTopWidth: 1, borderTopColor: '#f0f2f6', paddingTop: 5, gap: 8 },
  stopRow: { borderLeftWidth: 2, borderLeftColor: '#a7a3ff', paddingLeft: 9 },
  stopType: { color: '#7770d8', fontSize: 9, fontWeight: '700', textTransform: 'uppercase' },
  deliveryRow: { gap: 6, paddingTop: 8, borderTopWidth: 1, borderTopColor: '#f0f2f6' },
  tabBar: { minHeight: 58, borderTopWidth: 1, borderTopColor: '#e9edf3', backgroundColor: '#fff' },
  tabList: { alignItems: 'center', paddingHorizontal: 6, gap: 3 },
  tabItem: { flexDirection: 'row', alignItems: 'center', gap: 4, minHeight: 40, paddingHorizontal: 10, borderRadius: 8 },
  selectedTab: { backgroundColor: '#f0efff' },
  tabText: { color: '#7b8498', fontSize: 10, fontWeight: '600' },
  selectedTabText: { color: PURPLE },
  tabCount: { color: '#fff', backgroundColor: '#635bdb', fontSize: 8, fontWeight: '700', overflow: 'hidden', borderRadius: 9, paddingHorizontal: 4 },
  modalScrim: { ...StyleSheet.absoluteFill, zIndex: 5, backgroundColor: 'rgba(10, 19, 37, .5)', justifyContent: 'flex-end' },
  modalFrame: { maxHeight: '90%', backgroundColor: '#f5f7fb', borderTopLeftRadius: 16, borderTopRightRadius: 16, paddingBottom: Platform.OS === 'ios' ? 26 : 10 },
  modalHeader: { minHeight: 54, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 17, backgroundColor: '#fff', borderBottomWidth: 1, borderColor: '#e9edf3', borderTopLeftRadius: 16, borderTopRightRadius: 16 },
  closeText: { color: '#677287', fontSize: 26, paddingHorizontal: 5 },
  modalContent: { gap: 10, padding: 15, paddingBottom: 24 },
  choiceRow: { flexDirection: 'row', gap: 6, paddingVertical: 2 },
  choice: { borderWidth: 1, borderColor: '#e5e8ef', borderRadius: 7, backgroundColor: '#fff', paddingHorizontal: 9, paddingVertical: 8 },
  selectedChoice: { borderColor: PURPLE, backgroundColor: '#f0efff' },
  choiceText: { color: '#59647a', fontSize: 10, textTransform: 'capitalize' },
  selectedChoiceText: { color: PURPLE, fontWeight: '700' },
  formError: { color: '#a84352', fontSize: 11 },
  formActions: { flexDirection: 'row', gap: 8, marginTop: 5 },
  chatList: { gap: 9 },
  chatBubble: { borderRadius: 10, padding: 12, maxWidth: '94%' },
  userBubble: { alignSelf: 'flex-end', backgroundColor: '#f0efff' },
  assistantBubble: { alignSelf: 'flex-start', backgroundColor: '#fff', borderWidth: 1, borderColor: '#e9edf3' },
  actionProposal: { marginTop: 9, gap: 8 },
  proposalTitle: { color: '#635bdb', fontSize: 11, fontWeight: '700' },
  chatComposer: { flexDirection: 'row', alignItems: 'flex-end', gap: 8 },
  chatInput: { flex: 1, maxHeight: 120, minHeight: 42, borderWidth: 1, borderColor: '#e5e8ef', borderRadius: 8, backgroundColor: '#fff', padding: 10, color: '#29344b', fontSize: 12 },
})
