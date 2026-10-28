# Smart Logistics Hub

## Backend database

Create the SQL Server database itself before starting the backend. Flyway applies
`backend/src/main/resources/db/migration` at startup and creates or updates the
driver, vehicle, trailer, goods, route, and route-stop tables.
No table creation is needed in SQL Server Management Studio.
Set `DB_PASSWORD` in the backend process environment before running the
application; `DB_USERNAME` can override the default `sa` login. Keep database
credentials out of `application.properties` and source control. In PowerShell,
set `$env:DB_PASSWORD` in the same terminal before starting the backend, or add
`DB_PASSWORD` to the IntelliJ Spring Boot run configuration's environment
variables.

The backend uses `spring.jpa.hibernate.ddl-auto=validate`: Flyway owns schema
changes, and Hibernate checks that the entity mappings match the migrated schema.
Migration V5 adds component-level vehicle service history and loads starter data:
six vehicles (two per vehicle class), six drivers, nine freight items, and three
planned routes, including Cluj-Napoca to Brasov. Six freight items remain
unassigned at the warehouse. The starter route distances and travel times are
illustrative estimates.
Migration V6 stores route activation timestamps for the backend-computed route
simulation; live positions are derived from saved geometry and are not written
to the database on every update.
Migration V7 adds five demo drivers, five vehicles, an articulated trailer,
five freight items, and five example routes covering active, planned, and
completed states. These additive sample records are applied at backend startup.
Migration V8 adds service-history records for the new vehicles so they remain
eligible for dispatch instead of being incorrectly flagged for immediate
maintenance.
Migration V9 adds EUR freight revenue and maintenance costs, additional freight
and trailer categories, and explicit trailer-to-vehicle compatibility. Existing
financial values remain unset rather than being fabricated.

The Vue app uses `VITE_API_BASE_URL` when set and otherwise calls
`http://localhost:8080/api`. The backend exposes `/api/config` so the frontend
uses the same configured warehouse/depot location as route planning.

## Route lifecycle

The warehouse and route-planning depot default to `Cluj-Napoca, Romania`. Set
`WAREHOUSE_CITY` in the backend environment to override it; `DEPOT_CITY` remains
supported as a fallback. No API key is required. Set
`NOMINATIM_USER_AGENT` to `SmartLogisticsHub/1.0 (contact: you@example.com)`.
Pickup and delivery fields accept city names. The backend geocodes city locations
with OpenStreetMap's Nominatim service, caches
successful geocoding results in SQL Server, and requests ordered road routes
from OSRM. The configured depot is used as both the route origin and return
destination. The default public Nominatim and OSRM services are community
services without availability guarantees; use them for low-volume personal
testing, not production dispatch. Nominatim is throttled to at most one request
per second and does not support autocomplete here. Set `NOMINATIM_USER_AGENT`
to a descriptive application identifier and contact address in accordance with
the service usage policy. `NOMINATIM_BASE_URL` and `OSRM_BASE_URL` can point to
self-hosted instances.

City-level routing estimates represent travel between geocoded city locations,
not exact pickup or delivery points, so distances and ETAs are approximate.
The returned route geometry is stored with the route and shown on a Leaflet map
using OpenStreetMap tiles with attribution. Driving estimates do not include
loading, delivery dwell, breaks, or live traffic. Planning returns `503` when
geocoding or routing is unavailable and rejects cities that Nominatim cannot
find. `POST /api/routes/{id}/directions` recalculates and saves road geometry
for an existing route, including demo routes with illustrative straight-line
geometry; the frontend calls this when opening a route map.

Create routes with `POST /api/routes`, selecting a driver, vehicle, timezone-aware
departure time, and ordered stops containing one pickup and one later delivery
for every good. The server calculates the estimated depot arrival and route
distance; clients must not submit their own arrival estimate. Route creation
sets goods to `ASSIGNED`; activating through `PATCH /api/routes/{id}/status`
sets the driver, vehicle, optional trailer, and goods to `IN_TRANSIT`. Confirm
deliveries through `PATCH /api/routes/{routeId}/goods/{goodsId}/deliver`. When
all goods are delivered, the route becomes `COMPLETED`, goods are marked
`ARCHIVED` (not physically deleted) to preserve the route history, and the
driver changes to `RETURNING`. The vehicle and trailer remain `IN_TRANSIT`.
The return ETA is recalculated from delivery confirmation using the final
OSRM leg. A background check (once a minute by default) treats that ETA
as the depot-arrival proxy and changes returning resources to `AVAILABLE`.
There is no GPS integration, so this is a time-based estimate, not confirmation
of the driver's physical location. The route planner shows a backend-computed
simulation for active routes: a combined driver/vehicle marker follows the
saved OSRM geometry from activation at the estimated route duration, with its
heading and route stops shown on the map. This illustrative position is held
in the backend from the persisted activation timestamp and is available to
clients through `GET /api/routes/simulations`; the browser refreshes positions
in a batch every five seconds. No per-update location records are written.
Cancelling a planned route releases its assigned goods immediately; cancelling
an active route releases undelivered goods but keeps the driver, vehicle, and
trailer unavailable until the estimated return time.

## Vehicle maintenance

Every maintenance operation is stored with its component, performed date, and
vehicle odometer reading, and recorded cost in EUR. Component intervals use the conservative lower bound
from the supplied interval ranges. When both distance and time limits apply,
the lower remaining health is shown. Entries that are conditional (for example,
“as needed” or “engine overhaul”) retain service history but do not display an
invented percentage. Engine-hour limits are shown as notes because engine hours
are not currently tracked.

Vehicles at or below 10% on any healthbar are automatically marked
`MAINTENANCE`, removed from route selection, and rejected by the route API. A
vehicle already in transit is flagged for maintenance after its return instead
of interrupting an active route. Record completed work with
`POST /api/vehicles/{id}/maintenance` and body
`{"componentKey":"engine-oil-filter","performedAt":"2026-10-01T08:00:00Z","odometerKm":123456,"costEur":250.00}`.
The date and odometer are the values from when the operation actually took
place. Vehicles without service history for a fixed-interval component remain
unavailable until that operation is recorded.
When a dispatcher changes a vehicle from maintenance to available, the system
records a completed service operation for every interval-tracked component at
the current odometer, resetting those health bars. No cost is invented; costs
remain unset until explicitly recorded.

`DELETE /api/routes/{id}` is limited to `PLANNED` and `CANCELLED` routes.
Deleting a planned route releases its assigned freight back to `UNASSIGNED`.
Active and completed routes remain available as operational history; a
cancelled route cannot be deleted while its assigned resources are still
returning. Drivers, vehicles, and freight also have delete endpoints; records
still referenced by route history cannot be deleted. Freight can be created
manually in the Vue frontend; AI-assisted entry is intentionally deferred.

Example create request:

```json
{
  "driverId": 1,
  "vehicleId": 2,
  "departureTime": "2026-10-15T08:00:00Z",
  "stops": [
    { "goodsId": 10, "type": "PICKUP" },
    { "goodsId": 10, "type": "DELIVERY" }
  ]
}
```

Vans, rigid trucks, and articulated trucks can use only their matching trailer
compatibility class. An articulated truck always requires a trailer. Vans and
rigid trucks require one when the load exceeds the vehicle's capacity or the
cargo needs specialist equipment. Trailer categories include general, chilled,
hazardous, tanker, flatbed, low-loader, container, bulk, and livestock equipment.
The route planner offers compatible available trailers; the backend validates
the vehicle class, cargo category, capacity, license, and schedule.

## Finance

The Finance page reports delivered-freight revenue less recorded vehicle
maintenance costs in EUR, with date filtering, revenue and maintenance trends,
driver gross contribution, vehicle profitability, cost/category breakdowns, and
estimated freight margins. Revenue is recorded on each freight item; service
cost is recorded with each maintenance operation. Historical records without
those amounts remain explicitly unpriced/uncosted. Fuel, wages, tolls, insurance,
and other unrecorded costs are excluded from net profit. Driver figures are gross
freight contribution, not driver profit. Freight-level maintenance is an
estimate allocated to completed routes by distance and then split equally among
the route's freight. Route revenue is dated by departure; maintenance is dated
by service date.
