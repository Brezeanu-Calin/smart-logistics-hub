import { useRef } from 'react'
import MapView, { Marker, Polyline } from 'react-native-maps'
import type { RouteCoordinate, RouteSimulationPosition } from '../api'

export default function RouteMap({
  coordinates,
  position,
}: {
  coordinates: RouteCoordinate[]
  position?: RouteSimulationPosition
}) {
  const mapRef = useRef<MapView>(null)
  const first = coordinates[0]!

  return (
    <MapView
      ref={mapRef}
      style={{ height: 210, width: '100%' }}
      initialRegion={{ latitude: first.latitude, longitude: first.longitude, latitudeDelta: 5, longitudeDelta: 5 }}
      onMapReady={() => {
        if (coordinates.length > 1) {
          mapRef.current?.fitToCoordinates(coordinates, {
            edgePadding: { top: 24, right: 24, bottom: 24, left: 24 },
            animated: false,
          })
        }
      }}
      scrollEnabled={false}
      zoomEnabled={false}
    >
      <Polyline coordinates={coordinates} strokeColor="#f59e0b" strokeWidth={4} />
      <Marker coordinate={first} title="Route start" />
      {coordinates.length > 1 ? <Marker coordinate={coordinates[coordinates.length - 1]!} title="Route destination" pinColor="#10b981" /> : null}
      {position ? <Marker coordinate={position} title={`Simulated position · ${Math.round(position.progress * 100)}%`} pinColor="#1ca376" /> : null}
    </MapView>
  )
}
