# Smart Logistics Hub

## Backend database

Create the SQL Server database itself before starting the backend. Flyway applies
`backend/src/main/resources/db/migration` at startup and creates or updates the
driver, vehicle, trailer, goods, route, and route-stop tables.
No table creation is needed in SQL Server Management Studio.

The backend uses `spring.jpa.hibernate.ddl-auto=validate`: Flyway owns schema
changes, and Hibernate checks that the entity mappings match the migrated schema.
Add future changes as new versioned scripts; do not edit a migration after it has
been applied to a database.

## Route lifecycle

Set `GOOGLE_ROUTES_API_KEY` and `DEPOT_ADDRESS` in the backend environment to
enable route planning. The configured depot is used as both the route origin and
return destination. Google Routes calculates the ordered road distance and
driving duration; estimates use traffic-unaware driving times and do not include
loading, delivery dwell, breaks, or live traffic. Planning returns `503` when
Google Routes is not configured or cannot provide a valid estimate.

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
Google Routes leg. A background check (once a minute by default) treats that ETA
as the depot-arrival proxy and changes returning resources to `AVAILABLE`.
There is no GPS integration, so this is a time-based estimate, not confirmation
of the driver's physical location. Cancelling a planned route releases its
assigned goods immediately; cancelling an active route releases undelivered
goods but keeps the driver, vehicle, and trailer unavailable until the estimated
return time.

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
category. An articulated truck always requires a trailer. Vans and rigid trucks
use a trailer only when their own weight or volume capacity is insufficient. The
backend selects a compatible trailer that fits the remaining load, preferring
the smallest weight-capacity surplus and then the smallest volume-capacity
surplus. Driver license categories are checked against the vehicle/trailer
combination.
