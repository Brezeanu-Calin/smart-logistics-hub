<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import L, { type Map as LeafletMap, type LayerGroup } from 'leaflet'
import type { RouteCoordinate, RouteStop } from '../api'

const props = defineProps<{
  geometry: RouteCoordinate[]
  stops: RouteStop[]
  routeId: number
}>()

const mapElement = ref<HTMLDivElement | null>(null)
const mapError = ref('')
let map: LeafletMap | null = null
let routeLayers: LayerGroup | null = null

function renderRoute() {
  const layers = routeLayers
  if (!map || !layers) return
  layers.clearLayers()
  if (props.geometry.length < 2) {
    mapError.value = 'No saved route geometry is available for this route.'
    return
  }

  mapError.value = ''
  const line = L.polyline(props.geometry.map((point) => [point.latitude, point.longitude]), {
    color: '#f59e0b',
    weight: 4,
    opacity: 0.95,
  }).addTo(layers)

  const first = props.geometry[0]
  const last = props.geometry.at(-1)
  if (first && last) {
    L.circleMarker([first.latitude, first.longitude], {
      radius: 7,
      color: '#fff',
      weight: 2,
      fillColor: '#10b981',
      fillOpacity: 1,
    }).bindPopup('Depot departure').addTo(layers)
    L.circleMarker([last.latitude, last.longitude], {
      radius: 7,
      color: '#fff',
      weight: 2,
      fillColor: '#f59e0b',
      fillOpacity: 1,
    }).bindPopup('Depot return').addTo(layers)
  }

  props.stops.forEach((stop) => {
    if (stop.latitude === null || stop.longitude === null) return
    L.circleMarker([stop.latitude, stop.longitude], {
      radius: 6,
      color: '#fff',
      weight: 2,
      fillColor: stop.type === 'PICKUP' ? '#f59e0b' : '#10b981',
      fillOpacity: 1,
    })
      .bindPopup(`${stop.type} ${stop.stopOrder}: ${stop.city}`)
      .addTo(layers)
  })

  map.fitBounds(line.getBounds(), { padding: [24, 24], maxZoom: 14 })
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
  () => [props.geometry, props.stops],
  () => renderRoute(),
  { deep: true },
)

onUnmounted(() => {
  map?.remove()
  map = null
  routeLayers = null
})
</script>

<template>
  <section class="route-map-panel" :aria-label="`Map for route ${routeId}`">
    <p v-if="mapError" class="route-map-error">{{ mapError }}</p>
    <div ref="mapElement" class="route-map"></div>
  </section>
</template>

<style scoped>
.route-map-panel {
  position: relative;
  border-top: 1px solid #2d333b;
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
</style>
