ALTER TABLE drivers ADD archived BIT NOT NULL CONSTRAINT df_drivers_archived DEFAULT 0;
ALTER TABLE vehicles ADD archived BIT NOT NULL CONSTRAINT df_vehicles_archived DEFAULT 0;
ALTER TABLE goods ADD archived BIT NOT NULL CONSTRAINT df_goods_archived DEFAULT 0;
ALTER TABLE routes ADD archived BIT NOT NULL CONSTRAINT df_routes_archived DEFAULT 0;

EXEC sp_executesql N'
    UPDATE goods
    SET archived = 1
    WHERE status IN (''DELIVERED'', ''ARCHIVED'');
';

EXEC sp_executesql N'
    UPDATE routes
    SET archived = 1
    WHERE status = ''COMPLETED'';
';

CREATE TABLE route_archive_records (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    route_id BIGINT NOT NULL,
    archived_at DATETIMEOFFSET NOT NULL,
    snapshot_json NVARCHAR(MAX) NOT NULL
);

CREATE UNIQUE INDEX ux_route_archive_records_route_id
    ON route_archive_records (route_id);

INSERT INTO route_archive_records (route_id, archived_at, snapshot_json)
SELECT r.id,
       COALESCE(r.returned_at, r.estimated_return_time, r.departure_time),
       (
           SELECT r.id AS id,
                  r.driver_id AS driverId,
                  d.name AS driverName,
                  r.vehicle_id AS vehicleId,
                  v.license_plate AS vehicleLicensePlate,
                  r.trailer_id AS trailerId,
                  t.license_plate AS trailerLicensePlate,
                  r.departure_time AS departureTime,
                  r.estimated_arrival_time AS estimatedArrivalTime,
                  r.estimated_return_time AS estimatedReturnTime,
                  r.returned_at AS returnedAt,
                  r.distance_meters AS distanceMeters,
                  r.driving_duration_seconds AS drivingDurationSeconds,
                  r.status AS status,
                  CAST(1 AS BIT) AS archived,
                  JSON_QUERY((
                      SELECT s.goods_id AS goodsId,
                             g.tracking_code AS trackingCode,
                             s.stop_type AS type,
                             s.stop_order AS stopOrder,
                             s.city AS city,
                             s.latitude AS latitude,
                             s.longitude AS longitude
                      FROM route_stops s
                      JOIN goods g ON g.id = s.goods_id
                      WHERE s.route_id = r.id
                      ORDER BY s.stop_order
                      FOR JSON PATH
                  )) AS stops,
                  JSON_QUERY('[]') AS geometry
           FOR JSON PATH, WITHOUT_ARRAY_WRAPPER
       )
FROM routes r
JOIN drivers d ON d.id = r.driver_id
JOIN vehicles v ON v.id = r.vehicle_id
LEFT JOIN trailers t ON t.id = r.trailer_id
WHERE r.status = 'COMPLETED';

CREATE TABLE mobile_push_subscriptions (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    expo_push_token NVARCHAR(256) NOT NULL
);

CREATE UNIQUE INDEX ux_mobile_push_subscriptions_token
    ON mobile_push_subscriptions (expo_push_token);
