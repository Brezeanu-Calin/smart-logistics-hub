# Smart Logistics Hub

## Backend database

Create the SQL Server database itself before starting the backend. Flyway applies
`backend/src/main/resources/db/migration/V1__initial_schema.sql` at startup and
creates the initial driver, vehicle, trailer, goods, route, and route-stop tables.
No table creation is needed in SQL Server Management Studio.

The backend uses `spring.jpa.hibernate.ddl-auto=validate`: Flyway owns schema
changes, and Hibernate checks that the entity mappings match the migrated schema.
Add future changes as new versioned scripts; do not edit a migration after it has
been applied to a database.

## Route lifecycle

Create routes with `POST /api/routes`, selecting a driver, vehicle, timezone-aware
departure/arrival times, and an ordered list containing one pickup and one later
delivery stop for every good. Route creation sets goods to `ASSIGNED`; activating
the route through `PATCH /api/routes/{id}/status` sets its driver, vehicle,
optional trailer, and goods to `IN_TRANSIT`. Confirm deliveries through
`PATCH /api/routes/{routeId}/goods/{goodsId}/deliver`. A route can be completed
only after every good is delivered. Cancelling a route releases undelivered
goods to `UNASSIGNED` and preserves goods already delivered.

Example create request:

```json
{
  "driverId": 1,
  "vehicleId": 2,
  "departureTime": "2026-10-15T08:00:00Z",
  "estimatedArrivalTime": "2026-10-15T14:00:00Z",
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
