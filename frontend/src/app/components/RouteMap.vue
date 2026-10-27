<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import L, { type Map as LeafletMap, type LayerGroup } from 'leaflet'
import type { Route, RouteCoordinate, RouteSimulationPosition, RouteStop } from '../api'

const props = defineProps<{
  geometry: RouteCoordinate[]
  stops: RouteStop[]
  routeId: number
  status: Route['status']
  simulationPosition: RouteSimulationPosition | null
  driverName: string
  vehicleLicensePlate: string
}>()

const mapElement = ref<HTMLDivElement | null>(null)
const mapError = ref('')
let map: LeafletMap | null = null
let routeLayers: LayerGroup | null = null
let vehicleMarker: L.Marker | null = null

function popupContent(title: string, detail: string) {
  const content = document.createElement('div')
  const heading = document.createElement('strong')
  const description = document.createElement('div')
  heading.textContent = title
  description.textContent = detail
  content.append(heading, description)
  return content
}

function routeMarker(label: string, className: string, anchorX: number) {
  return L.divIcon({
    className,
    html: `<span>${label}</span>`,
    iconSize: [112, 26],
    iconAnchor: [anchorX, 13],
  })
}

function vehicleIcon(bearing: number) {
  return L.divIcon({
    className: 'route-map-vehicle-icon',
    html: `<span style="transform: rotate(${bearing}deg)">▲</span>`,
    iconSize: [36, 36],
    iconAnchor: [18, 18],
  })
}

function updateVehicleMarker() {
  const position = props.simulationPosition
  if (props.status !== 'ACTIVE' || !position || !routeLayers) {
    vehicleMarker?.remove()
    vehicleMarker = null
    return
  }

  if (!vehicleMarker) {
    vehicleMarker = L.marker([position.latitude, position.longitude], {
      icon: vehicleIcon(position.headingDegrees),
      title: `Vehicle ${props.vehicleLicensePlate} with driver ${props.driverName}`,
      alt: `Vehicle ${props.vehicleLicensePlate}, driver ${props.driverName}, heading ${position.heading}`,
      zIndexOffset: 1000,
    })
      .bindPopup(popupContent(
        `Vehicle ${props.vehicleLicensePlate}`,
        `Driver ${props.driverName} · heading ${position.heading}`,
      ))
      .addTo(routeLayers)
  } else {
    vehicleMarker.setLatLng([position.latitude, position.longitude])
    vehicleMarker.setIcon(vehicleIcon(position.headingDegrees))
    vehicleMarker.setPopupContent(popupContent(
      `Vehicle ${props.vehicleLicensePlate}`,
      `Driver ${props.driverName} · heading ${position.heading}`,
    ))
  }
}

function renderRoute() {
  const layers = routeLayers
  if (!map || !layers) return
  layers.clearLayers()
  vehicleMarker = null
  if (props.geometry.length < 2) {
    mapError.value = 'No saved route geometry is available for this route.'
    return
  }

  mapError.value = ''
  const coordinates = props.geometry.map((point) => [point.latitude, point.longitude] as L.LatLngTuple)
  L.polyline(coordinates, {
    color: '#0b1321',
    weight: 10,
    opacity: 0.9,
    lineCap: 'round',
    lineJoin: 'round',
  }).addTo(layers)
  const line = L.polyline(coordinates, {
    color: '#38bdf8',
    weight: 5,
    opacity: 1,
    lineCap: 'round',
    lineJoin: 'round',
  }).addTo(layers)

  const first = props.geometry[0]
  const last = props.geometry.at(-1)
  if (first && last) {
    L.marker([first.latitude, first.longitude], {
      icon: routeMarker('START · DEPOT', 'route-map-label route-map-start', 0),
      title: 'Route departure · depot',
    }).bindPopup(popupContent('Route departure', 'Starting point · depot')).addTo(layers)
  }

  const finalDelivery = props.stops
    .filter((stop) => stop.type === 'DELIVERY' && stop.latitude !== null && stop.longitude !== null)
    .reduce<RouteStop | null>(
      (latest, stop) => latest === null || stop.stopOrder > latest.stopOrder ? stop : latest,
      null,
    )
  props.stops.forEach((stop) => {
    if (stop.latitude === null || stop.longitude === null) return
    const isFinalDelivery = stop === finalDelivery
    const marker = isFinalDelivery
      ? L.marker([stop.latitude, stop.longitude], {
        icon: routeMarker('FINAL DELIVERY', 'route-map-label route-map-destination', 0),
        title: `Final delivery · ${stop.city}`,
      })
      : L.circleMarker([stop.latitude, stop.longitude], {
        radius: 6,
        color: '#fff',
        weight: 2,
        fillColor: stop.type === 'PICKUP' ? '#8b7cff' : '#f59e0b',
        fillOpacity: 1,
      })
    marker
      .bindPopup(popupContent(`${stop.type} ${stop.stopOrder}`, stop.city))
      .addTo(layers)
  })

  map.fitBounds(line.getBounds(), { padding: [24, 24], maxZoom: 14 })
  updateVehicleMarker()
}

onMounted(() => {
  if (!mapElement.value) return
  map = L.map(mapElement.value, { scrollWheelZoom: false }).setView([20, 0], 2)
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
  }).addTo(map)
  routeLayers = L.layerGroup().addTo(map)
  renderRoute()
  window.setTimeout(() => map?.invalidateSize(), 0)
})

watch(
  () => [props.geometry, props.stops, props.status],
  () => renderRoute(),
  { deep: true },
)

watch(
  () => props.simulationPosition,
  () => updateVehicleMarker(),
  { deep: true },
)

onUnmounted(() => {
  map?.remove()
  map = null
  routeLayers = null
  vehicleMarker = null
})
</script>

<template>
  <section class="route-map-panel" :aria-label="`Map for route ${routeId}`">
    <p v-if="mapError" class="route-map-error">{{ mapError }}</p>
    <div v-if="status === 'ACTIVE'" class="route-map-live" role="group" aria-label="Backend simulated route position">
      <span class="route-map-live-dot"></span>
      <span class="route-map-live-details">
        <strong>SIMULATED POSITION · {{ vehicleLicensePlate }}</strong>
        <small>Driver {{ driverName }} · vehicle and driver share this position</small>
      </span>
      <strong v-if="simulationPosition" class="route-map-heading">
        ▲ {{ simulationPosition.heading }} · {{ Math.round(simulationPosition.progress * 100) }}%
      </strong>
      <strong v-else class="route-map-heading">Waiting for backend position</strong>
    </div>
    <div ref="mapElement" class="route-map"></div>
    <div class="route-map-legend" aria-label="Map legend">
      <span><i class="legend-route"></i> Planned road route</span>
      <span><i class="legend-start"></i> Start depot</span>
      <span><i class="legend-pickup"></i> Pickup</span>
      <span><i class="legend-delivery"></i> Delivery</span>
      <span><i class="legend-vehicle">▲</i> Vehicle + driver direction</span>
    </div>
  </section>
</template>

<style scoped>
.route-map-panel {
  position: relative;
  border-top: 1px solid #2d333b;
  background: #111416;
}
.route-map-live {
  display: flex;
  min-height: 48px;
  align-items: center;
  gap: 9px;
  border-bottom: 1px solid #2d333b;
  padding: 7px 12px;
  color: #d7dee8;
  font-size: 10px;
}
.route-map-live-dot {
  width: 8px;
  height: 8px;
  flex: 0 0 8px;
  border-radius: 50%;
  background: #34d399;
  box-shadow: 0 0 0 3px #34d39926;
}
.route-map-live-details {
  min-width: 0;
  flex: 1;
}
.route-map-live-details strong,
.route-map-live-details small {
  display: block;
}
.route-map-live-details strong {
  overflow: hidden;
  color: #e8edf5;
  font-size: 9px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.route-map-live-details small {
  overflow: hidden;
  margin-top: 2px;
  color: #9da8b7;
  font-size: 9px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.route-map-heading {
  color: #a7f3d0;
  font-size: 10px;
  white-space: nowrap;
}
.route-map {
  height: 300px;
  width: 100%;
  background: #111416;
}
.route-map :deep(.leaflet-tile) {
  filter: invert(1) hue-rotate(180deg) brightness(.75) contrast(.9);
}
.route-map-error {
  position: absolute;
  z-index: 500;
  top: 10px;
  left: 10px;
  border-radius: 0;
  padding: 7px 9px;
  color: #fbbf24;
  background: #19150e;
  font-size: 11px;
}
.route-map :deep(.route-map-label) {
  display: flex;
  align-items: center;
  overflow: visible;
  border: 0;
  background: transparent;
}
.route-map :deep(.route-map-label span) {
  border: 1px solid currentColor;
  border-radius: 4px;
  padding: 4px 6px;
  background: #111416eF;
  font: 700 9px/1.2 Inter, sans-serif;
  white-space: nowrap;
}
.route-map :deep(.route-map-start) { color: #34d399; }
.route-map :deep(.route-map-destination) { color: #fb7185; }
.route-map :deep(.route-map-vehicle-icon) {
  display: grid;
  place-items: center;
  border: 2px solid #fff;
  border-radius: 50%;
  color: #fff;
  background: #2563eb;
  box-shadow: 0 2px 8px #0009;
  font-size: 18px;
}
.route-map :deep(.route-map-vehicle-icon span) {
  display: block;
  line-height: 1;
}
.route-map-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 7px 16px;
  border-top: 1px solid #2d333b;
  padding: 8px 12px;
  color: #aab4c1;
  font-size: 9px;
}
.route-map-legend span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}
.legend-route {
  width: 14px !important;
  height: 4px !important;
  border-radius: 2px !important;
  color: #38bdf8;
}
.route-map-legend i {
  display: inline-grid;
  width: 9px;
  height: 9px;
  place-items: center;
  border-radius: 50%;
  background: currentColor;
  font-size: 8px;
  font-style: normal;
}
.legend-start { color: #34d399; }
.legend-pickup { color: #8b7cff; }
.legend-delivery { color: #f59e0b; }
.route-map-legend .legend-vehicle {
  color: #93c5fd;
  background: transparent;
}
@media (max-width: 480px) {
  .route-map-live { flex-wrap: wrap; }
  .route-map-heading { margin-left: 17px; }
}
</style>
