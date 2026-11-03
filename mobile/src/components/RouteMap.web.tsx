import { StyleSheet, View } from 'react-native'
import type { RouteCoordinate, RouteSimulationPosition } from '../api'

const SIZE = 210
const PADDING = 22

export default function RouteMap({
  coordinates,
  position,
}: {
  coordinates: RouteCoordinate[]
  position?: RouteSimulationPosition
}) {
  const latitudes = coordinates.map((point) => point.latitude)
  const longitudes = coordinates.map((point) => point.longitude)
  const minLatitude = Math.min(...latitudes)
  const maxLatitude = Math.max(...latitudes)
  const minLongitude = Math.min(...longitudes)
  const maxLongitude = Math.max(...longitudes)
  const latitudeSpan = maxLatitude - minLatitude || 1
  const longitudeSpan = maxLongitude - minLongitude || 1
  const pointOnMap = (point: RouteCoordinate) => ({
    x: PADDING + ((point.longitude - minLongitude) / longitudeSpan) * (SIZE - PADDING * 2),
    y: SIZE - PADDING - ((point.latitude - minLatitude) / latitudeSpan) * (SIZE - PADDING * 2),
  })
  const points = coordinates.map(pointOnMap)
  const markers = [
    { point: points[0]!, color: '#635bdb' },
    ...(points.length > 1 ? [{ point: points[points.length - 1]!, color: '#d28b2b' }] : []),
    ...(position ? [{ point: pointOnMap(position), color: '#1ca376' }] : []),
  ]

  return (
    <View accessibilityLabel="Route map preview" style={styles.map}>
      {[0, 1, 2, 3].map((index) => (
        <View
          key={`route-segment-${index}`}
          style={[styles.road, { top: `${20 + index * 20}%`, transform: [{ rotate: `${index % 2 ? -8 : 8}deg` }] }]}
        />
      ))}
      {points.slice(1).map((point, index) => {
        const previous = points[index]!
        const dx = point.x - previous.x
        const dy = point.y - previous.y
        const length = Math.sqrt(dx * dx + dy * dy)
        const angle = Math.atan2(dy, dx) * (180 / Math.PI)
        return (
          <View
            key={`route-line-${index}`}
            style={[
              styles.routeLine,
              {
                left: previous.x,
                top: previous.y,
                width: length,
                transform: [{ rotate: `${angle}deg` }],
              },
            ]}
          />
        )
      })}
      {markers.map(({ point, color }, index) => (
        <View
          key={`route-marker-${index}`}
          style={[styles.marker, { left: point.x - 6, top: point.y - 6, backgroundColor: color }]}
        />
      ))}
    </View>
  )
}

const styles = StyleSheet.create({
  map: {
    height: SIZE,
    width: '100%',
    borderRadius: 8,
    overflow: 'hidden',
    backgroundColor: '#eef2f5',
    position: 'relative',
  },
  road: {
    position: 'absolute',
    left: '-10%',
    width: '120%',
    height: 8,
    backgroundColor: '#fff',
    borderTopWidth: 1,
    borderBottomWidth: 1,
    borderColor: '#e1e6eb',
  },
  routeLine: {
    position: 'absolute',
    height: 4,
    backgroundColor: '#635bdb',
    transformOrigin: 'left center',
  },
  marker: {
    position: 'absolute',
    width: 12,
    height: 12,
    borderRadius: 6,
    borderWidth: 2,
    borderColor: '#fff',
  },
})
