DECLARE @now DATETIMEOFFSET = TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00');
DECLARE @drivers TABLE (email NVARCHAR(254) NOT NULL, id BIGINT NOT NULL);
DECLARE @vehicles TABLE (license_plate NVARCHAR(20) NOT NULL, id BIGINT NOT NULL);
DECLARE @routes TABLE (tracking_code NVARCHAR(13) NOT NULL, id BIGINT NOT NULL);

INSERT INTO drivers (name, email, status)
SELECT seed.name, seed.email, 'AVAILABLE'
FROM (VALUES
    ('Stefan Pop', 'stefan.pop@demo.smartlogistics.local'),
    ('Larisa Ionescu', 'larisa.ionescu@demo.smartlogistics.local'),
    ('Victor Rusu', 'victor.rusu@demo.smartlogistics.local'),
    ('Maria Pavel', 'maria.pavel@demo.smartlogistics.local'),
    ('Darius Matei', 'darius.matei@demo.smartlogistics.local')
) AS seed(name, email)
WHERE NOT EXISTS (SELECT 1 FROM drivers d WHERE d.email = seed.email);

INSERT INTO @drivers (email, id)
SELECT email, id
FROM drivers
WHERE email IN (
    'stefan.pop@demo.smartlogistics.local',
    'larisa.ionescu@demo.smartlogistics.local',
    'victor.rusu@demo.smartlogistics.local',
    'maria.pavel@demo.smartlogistics.local',
    'darius.matei@demo.smartlogistics.local'
);

INSERT INTO driver_license_categories (driver_id, license_category)
SELECT d.id, seed.license_category
FROM @drivers d
JOIN (VALUES
    ('stefan.pop@demo.smartlogistics.local', 'B'),
    ('larisa.ionescu@demo.smartlogistics.local', 'C'),
    ('victor.rusu@demo.smartlogistics.local', 'CE'),
    ('maria.pavel@demo.smartlogistics.local', 'BE'),
    ('darius.matei@demo.smartlogistics.local', 'C')
) AS seed(email, license_category) ON seed.email = d.email
WHERE NOT EXISTS (
    SELECT 1 FROM driver_license_categories existing
    WHERE existing.driver_id = d.id AND existing.license_category = seed.license_category
);

INSERT INTO vehicles (
    license_plate, vehicle_type, make, model, year, max_weight_capacity_kg,
    max_volume_m3, status, current_mileage_km, transmission_type, maintenance_auto_triggered
)
SELECT seed.license_plate, seed.vehicle_type, seed.make, seed.model, seed.year,
       seed.max_weight_capacity_kg, seed.max_volume_m3, 'AVAILABLE',
       seed.current_mileage_km, seed.transmission_type, 0
FROM (VALUES
    ('CJ 07 SLH', 'VAN', 'Mercedes-Benz', 'Sprinter', 2024, 1800, 14, 42000, 'AUTOMATIC'),
    ('CJ 08 SLH', 'RIGID_TRUCK', 'Iveco', 'Eurocargo', 2022, 8500, 42, 188000, 'AMT'),
    ('CJ 09 SLH', 'ARTICULATED_TRUCK', 'Scania', 'R500', 2023, 24000, 92, 267000, 'AMT'),
    ('CJ 10 SLH', 'VAN', 'Volkswagen', 'Crafter', 2021, 1500, 13, 156000, 'MANUAL'),
    ('CJ 11 SLH', 'RIGID_TRUCK', 'Mercedes-Benz', 'Atego', 2024, 9500, 48, 63000, 'AMT')
) AS seed(license_plate, vehicle_type, make, model, year, max_weight_capacity_kg,
          max_volume_m3, current_mileage_km, transmission_type)
WHERE NOT EXISTS (
    SELECT 1 FROM vehicles v WHERE v.license_plate = seed.license_plate
);

INSERT INTO @vehicles (license_plate, id)
SELECT license_plate, id
FROM vehicles
WHERE license_plate IN ('CJ 07 SLH', 'CJ 08 SLH', 'CJ 09 SLH', 'CJ 10 SLH', 'CJ 11 SLH');

INSERT INTO trailers (
    license_plate, trailer_type, make, model, year, max_weight_capacity_kg, max_volume_m3, status
)
SELECT 'CJ-TR-07', 'ARTICULATED_TRUCK_TRAILER', 'Schmitz Cargobull', 'S.CS', 2022, 24000, 92, 'AVAILABLE'
WHERE NOT EXISTS (SELECT 1 FROM trailers WHERE license_plate = 'CJ-TR-07');

INSERT INTO goods (
    tracking_code, client_name, goods_type, description, weight_kg, volume_m3,
    pickup_city, delivery_city, status
)
SELECT seed.tracking_code, seed.client_name, seed.goods_type, seed.description,
       seed.weight_kg, seed.volume_m3, seed.pickup_city, seed.delivery_city, seed.status
FROM (VALUES
    ('SLH-000010', 'Carpathia Medical', 'REFRIGERATED', 'Temperature-controlled medical supplies', 950, 7.5, 'Cluj-Napoca, Romania', 'Brasov, Romania', 'IN_TRANSIT'),
    ('SLH-000011', 'Crisana Industrial', 'GENERAL', 'Machine parts on secured pallets', 4200, 24.0, 'Cluj-Napoca, Romania', 'Oradea, Romania', 'ASSIGNED'),
    ('SLH-000012', 'Danube Retail', 'GENERAL', 'Retail replenishment pallets', 16800, 62.0, 'Cluj-Napoca, Romania', 'Timisoara, Romania', 'ASSIGNED'),
    ('SLH-000013', 'Alba Farm Cooperative', 'GENERAL', 'Packaged farm equipment', 780, 5.0, 'Cluj-Napoca, Romania', 'Alba Iulia, Romania', 'DELIVERED'),
    ('SLH-000014', 'Moldova Electronics', 'HAZARDOUS', 'Lithium battery shipment', 2100, 16.0, 'Cluj-Napoca, Romania', 'Iasi, Romania', 'ASSIGNED')
) AS seed(tracking_code, client_name, goods_type, description, weight_kg, volume_m3,
          pickup_city, delivery_city, status)
WHERE NOT EXISTS (SELECT 1 FROM goods g WHERE g.tracking_code = seed.tracking_code);

DECLARE @routeSeeds TABLE (
    tracking_code NVARCHAR(13) NOT NULL,
    driver_email NVARCHAR(254) NOT NULL,
    vehicle_plate NVARCHAR(20) NOT NULL,
    trailer_plate NVARCHAR(20) NULL,
    status NVARCHAR(20) NOT NULL,
    departure_day INT NOT NULL,
    arrival_hours INT NOT NULL,
    return_hours INT NOT NULL,
    distance_meters BIGINT NOT NULL,
    delivery_latitude FLOAT NOT NULL,
    delivery_longitude FLOAT NOT NULL
);

INSERT INTO @routeSeeds VALUES
    ('SLH-000010', 'stefan.pop@demo.smartlogistics.local', 'CJ 07 SLH', NULL, 'ACTIVE', 0, 4, 8, 550000, 45.6579, 25.6012),
    ('SLH-000011', 'larisa.ionescu@demo.smartlogistics.local', 'CJ 08 SLH', NULL, 'PLANNED', 2, 3, 6, 306000, 47.0465, 21.9189),
    ('SLH-000012', 'victor.rusu@demo.smartlogistics.local', 'CJ 09 SLH', 'CJ-TR-07', 'PLANNED', 3, 6, 12, 650000, 45.7489, 21.2087),
    ('SLH-000013', 'maria.pavel@demo.smartlogistics.local', 'CJ 10 SLH', NULL, 'COMPLETED', -5, 2, 4, 105000, 46.0667, 23.5833),
    ('SLH-000014', 'darius.matei@demo.smartlogistics.local', 'CJ 11 SLH', NULL, 'PLANNED', 4, 7, 14, 390000, 47.1585, 27.6014);

INSERT INTO routes (
    driver_id, vehicle_id, trailer_id, departure_time, estimated_arrival_time,
    estimated_return_time, returned_at, distance_meters, driving_duration_seconds,
    return_duration_seconds, status, activated_at
)
SELECT d.id, v.id, t.id,
       DATEADD(day, seed.departure_day, @now),
       DATEADD(hour, seed.arrival_hours, DATEADD(day, seed.departure_day, @now)),
       DATEADD(hour, seed.return_hours, DATEADD(day, seed.departure_day, @now)),
       CASE WHEN seed.status = 'COMPLETED'
            THEN DATEADD(hour, seed.return_hours, DATEADD(day, seed.departure_day, @now))
            ELSE NULL END,
       seed.distance_meters, seed.arrival_hours * 3600,
       (seed.return_hours - seed.arrival_hours) * 3600, seed.status,
       CASE WHEN seed.status = 'ACTIVE' THEN @now ELSE NULL END
FROM @routeSeeds seed
JOIN @drivers d ON d.email = seed.driver_email
JOIN @vehicles v ON v.license_plate = seed.vehicle_plate
LEFT JOIN trailers t ON t.license_plate = seed.trailer_plate
WHERE NOT EXISTS (
    SELECT 1 FROM routes existing
    WHERE existing.driver_id = d.id AND existing.vehicle_id = v.id
);

INSERT INTO @routes (tracking_code, id)
SELECT seed.tracking_code, r.id
FROM @routeSeeds seed
JOIN @drivers d ON d.email = seed.driver_email
JOIN @vehicles v ON v.license_plate = seed.vehicle_plate
JOIN routes r ON r.driver_id = d.id AND r.vehicle_id = v.id;

INSERT INTO route_stops (route_id, goods_id, stop_type, stop_order, city, latitude, longitude)
SELECT r.id, g.id, stop.stop_type, stop.stop_order, stop.city, stop.latitude, stop.longitude
FROM @routes r
JOIN goods g ON g.tracking_code = r.tracking_code
JOIN (VALUES
    ('SLH-000010', 'PICKUP', 1, 'Cluj-Napoca, Romania', 46.7712, 23.6236),
    ('SLH-000010', 'DELIVERY', 2, 'Brasov, Romania', 45.6579, 25.6012),
    ('SLH-000011', 'PICKUP', 1, 'Cluj-Napoca, Romania', 46.7712, 23.6236),
    ('SLH-000011', 'DELIVERY', 2, 'Oradea, Romania', 47.0465, 21.9189),
    ('SLH-000012', 'PICKUP', 1, 'Cluj-Napoca, Romania', 46.7712, 23.6236),
    ('SLH-000012', 'DELIVERY', 2, 'Timisoara, Romania', 45.7489, 21.2087),
    ('SLH-000013', 'PICKUP', 1, 'Cluj-Napoca, Romania', 46.7712, 23.6236),
    ('SLH-000013', 'DELIVERY', 2, 'Alba Iulia, Romania', 46.0667, 23.5833),
    ('SLH-000014', 'PICKUP', 1, 'Cluj-Napoca, Romania', 46.7712, 23.6236),
    ('SLH-000014', 'DELIVERY', 2, 'Iasi, Romania', 47.1585, 27.6014)
) AS stop(tracking_code, stop_type, stop_order, city, latitude, longitude)
    ON stop.tracking_code = r.tracking_code
WHERE NOT EXISTS (
    SELECT 1 FROM route_stops existing
    WHERE existing.route_id = r.id AND existing.stop_order = stop.stop_order
);

INSERT INTO route_geometry_points (route_id, point_order, latitude, longitude)
SELECT r.id, point.point_order, point.latitude, point.longitude
FROM @routes r
JOIN (VALUES
    ('SLH-000010', 1, 46.7712, 23.6236), ('SLH-000010', 2, 45.6579, 25.6012), ('SLH-000010', 3, 46.7712, 23.6236),
    ('SLH-000011', 1, 46.7712, 23.6236), ('SLH-000011', 2, 47.0465, 21.9189), ('SLH-000011', 3, 46.7712, 23.6236),
    ('SLH-000012', 1, 46.7712, 23.6236), ('SLH-000012', 2, 45.7489, 21.2087), ('SLH-000012', 3, 46.7712, 23.6236),
    ('SLH-000013', 1, 46.7712, 23.6236), ('SLH-000013', 2, 46.0667, 23.5833), ('SLH-000013', 3, 46.7712, 23.6236),
    ('SLH-000014', 1, 46.7712, 23.6236), ('SLH-000014', 2, 47.1585, 27.6014), ('SLH-000014', 3, 46.7712, 23.6236)
) AS point(tracking_code, point_order, latitude, longitude)
    ON point.tracking_code = r.tracking_code
WHERE NOT EXISTS (
    SELECT 1 FROM route_geometry_points existing
    WHERE existing.route_id = r.id AND existing.point_order = point.point_order
);

UPDATE drivers
SET status = 'IN_TRANSIT'
WHERE email = 'stefan.pop@demo.smartlogistics.local';

UPDATE vehicles
SET status = 'IN_TRANSIT'
WHERE license_plate = 'CJ 07 SLH';
