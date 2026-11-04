DECLARE @now DATETIMEOFFSET = TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00');
DECLARE @drivers TABLE (email NVARCHAR(254) NOT NULL PRIMARY KEY, id BIGINT NOT NULL);
DECLARE @fleet TABLE (
    driver_email NVARCHAR(254) NOT NULL,
    license_plate NVARCHAR(20) NOT NULL,
    vehicle_type NVARCHAR(30) NOT NULL,
    transmission_type NVARCHAR(20) NOT NULL,
    current_mileage_km INT NOT NULL,
    vehicle_id BIGINT NULL
);
DECLARE @trailers TABLE (license_plate NVARCHAR(20) NOT NULL PRIMARY KEY, id BIGINT NOT NULL);
DECLARE @routeSeeds TABLE (
    tracking_code NVARCHAR(13) NOT NULL,
    driver_email NVARCHAR(254) NOT NULL,
    vehicle_plate NVARCHAR(20) NOT NULL,
    trailer_plate NVARCHAR(20) NULL,
    departure_day INT NOT NULL,
    travel_hours INT NOT NULL,
    return_hours INT NOT NULL,
    distance_meters BIGINT NOT NULL,
    pickup_city NVARCHAR(500) NOT NULL,
    delivery_city NVARCHAR(500) NOT NULL,
    delivery_latitude FLOAT NOT NULL,
    delivery_longitude FLOAT NOT NULL
);
DECLARE @routes TABLE (tracking_code NVARCHAR(13) NOT NULL PRIMARY KEY, id BIGINT NOT NULL);

INSERT INTO drivers (name, email, status)
SELECT seed.name, seed.email, 'AVAILABLE'
FROM (VALUES
    ('Mara Pop', 'mara.pop@haulmaster.example', 'B'),
    ('Radu Ilie', 'radu.ilie@haulmaster.example', 'C'),
    ('Sorin Dobre', 'sorin.dobre@haulmaster.example', 'CE'),
    ('Diana Enache', 'diana.enache@haulmaster.example', 'C'),
    ('Cosmin Pavel', 'cosmin.pavel@haulmaster.example', 'CE'),
    ('Elena Barbu', 'elena.barbu@haulmaster.example', 'C'),
    ('Tudor Neagu', 'tudor.neagu@haulmaster.example', 'B'),
    ('Bianca Muresan', 'bianca.muresan@haulmaster.example', 'C')
) AS seed(name, email, license_category)
WHERE NOT EXISTS (SELECT 1 FROM drivers existing WHERE existing.email = seed.email);

INSERT INTO @drivers (email, id)
SELECT email, id
FROM drivers
WHERE email IN (
    'mara.pop@haulmaster.example',
    'radu.ilie@haulmaster.example',
    'sorin.dobre@haulmaster.example',
    'diana.enache@haulmaster.example',
    'cosmin.pavel@haulmaster.example',
    'elena.barbu@haulmaster.example',
    'tudor.neagu@haulmaster.example',
    'bianca.muresan@haulmaster.example'
);

INSERT INTO driver_license_categories (driver_id, license_category)
SELECT d.id, seed.license_category
FROM @drivers d
JOIN (VALUES
    ('mara.pop@haulmaster.example', 'B'),
    ('radu.ilie@haulmaster.example', 'C'),
    ('sorin.dobre@haulmaster.example', 'CE'),
    ('diana.enache@haulmaster.example', 'C'),
    ('cosmin.pavel@haulmaster.example', 'CE'),
    ('elena.barbu@haulmaster.example', 'C'),
    ('tudor.neagu@haulmaster.example', 'B'),
    ('bianca.muresan@haulmaster.example', 'C')
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
    ('CJ 12 SLH', 'VAN', 'Ford', 'Transit Custom', 2024, 1400, 10, 68000, 'AUTOMATIC'),
    ('CJ 13 SLH', 'RIGID_TRUCK', 'DAF', 'LF 260', 2023, 9000, 45, 136000, 'AMT'),
    ('CJ 14 SLH', 'ARTICULATED_TRUCK', 'Volvo', 'FH 500', 2024, 24000, 92, 178000, 'AMT'),
    ('CJ 15 SLH', 'RIGID_TRUCK', 'MAN', 'TGM 18.320', 2022, 10000, 48, 221000, 'AMT'),
    ('CJ 16 SLH', 'ARTICULATED_TRUCK', 'Scania', 'R450', 2023, 24000, 90, 246000, 'AMT'),
    ('CJ 17 SLH', 'RIGID_TRUCK', 'Iveco', 'Eurocargo ML', 2024, 9500, 46, 89000, 'AMT'),
    ('CJ 18 SLH', 'VAN', 'Mercedes-Benz', 'Sprinter', 2022, 1600, 13, 118000, 'MANUAL'),
    ('CJ 19 SLH', 'RIGID_TRUCK', 'Renault', 'D Wide', 2023, 10500, 50, 154000, 'AMT')
) AS seed(license_plate, vehicle_type, make, model, year, max_weight_capacity_kg,
          max_volume_m3, current_mileage_km, transmission_type)
WHERE NOT EXISTS (SELECT 1 FROM vehicles v WHERE v.license_plate = seed.license_plate);

INSERT INTO @fleet (driver_email, license_plate, vehicle_type, transmission_type, current_mileage_km, vehicle_id)
SELECT seed.driver_email, seed.license_plate, seed.vehicle_type, seed.transmission_type,
       seed.current_mileage_km, v.id
FROM (VALUES
    ('mara.pop@haulmaster.example', 'CJ 12 SLH', 'VAN', 'AUTOMATIC', 68000),
    ('radu.ilie@haulmaster.example', 'CJ 13 SLH', 'RIGID_TRUCK', 'AMT', 136000),
    ('sorin.dobre@haulmaster.example', 'CJ 14 SLH', 'ARTICULATED_TRUCK', 'AMT', 178000),
    ('diana.enache@haulmaster.example', 'CJ 15 SLH', 'RIGID_TRUCK', 'AMT', 221000),
    ('cosmin.pavel@haulmaster.example', 'CJ 16 SLH', 'ARTICULATED_TRUCK', 'AMT', 246000),
    ('elena.barbu@haulmaster.example', 'CJ 17 SLH', 'RIGID_TRUCK', 'AMT', 89000),
    ('tudor.neagu@haulmaster.example', 'CJ 18 SLH', 'VAN', 'MANUAL', 118000),
    ('bianca.muresan@haulmaster.example', 'CJ 19 SLH', 'RIGID_TRUCK', 'AMT', 154000)
) AS seed(driver_email, license_plate, vehicle_type, transmission_type, current_mileage_km)
JOIN vehicles v ON v.license_plate = seed.license_plate;

INSERT INTO vehicle_maintenance_operations (vehicle_id, component_key, performed_at, odometer_km, cost_eur)
SELECT fleet.vehicle_id, component.component_key, DATEADD(day, -2, @now),
       fleet.current_mileage_km - 2000,
       component.cost_eur
FROM @fleet fleet
JOIN (VALUES
    ('VAN', 'engine-oil-filter', 180.00), ('VAN', 'coolant-flush', 245.00),
    ('VAN', 'brake-pads-rotors', 390.00), ('VAN', 'tire-replacement', 520.00),
    ('VAN', 'transmission-manual', 360.00), ('VAN', 'transmission-automatic', 430.00),
    ('VAN', 'timing-belt-water-pump', 680.00), ('VAN', 'timing-chain-tensioners', 120.00),
    ('VAN', 'clutch-assembly', 750.00), ('VAN', 'fuel-pump-module', 410.00),
    ('VAN', 'water-pump', 280.00), ('VAN', 'engine-oil-pump', 340.00),
    ('RIGID_TRUCK', 'engine-oil-filter', 440.00), ('ARTICULATED_TRUCK', 'engine-oil-filter', 510.00),
    ('RIGID_TRUCK', 'coolant-flush', 620.00), ('ARTICULATED_TRUCK', 'coolant-flush', 710.00),
    ('RIGID_TRUCK', 'brake-pads-shoes-discs', 1450.00), ('ARTICULATED_TRUCK', 'brake-pads-shoes-discs', 1860.00),
    ('RIGID_TRUCK', 'steer-axle-tires', 1120.00), ('ARTICULATED_TRUCK', 'steer-axle-tires', 1380.00),
    ('RIGID_TRUCK', 'drive-axle-tires', 1680.00), ('ARTICULATED_TRUCK', 'drive-axle-tires', 2150.00),
    ('RIGID_TRUCK', 'transmission-amt', 1250.00), ('ARTICULATED_TRUCK', 'transmission-amt', 1680.00),
    ('RIGID_TRUCK', 'differential-axle-oil', 460.00), ('ARTICULATED_TRUCK', 'differential-axle-oil', 580.00),
    ('RIGID_TRUCK', 'timing-gears-chain', 220.00), ('ARTICULATED_TRUCK', 'timing-gears-chain', 280.00),
    ('RIGID_TRUCK', 'clutch-assembly-actuator', 1850.00), ('ARTICULATED_TRUCK', 'clutch-assembly-actuator', 2400.00),
    ('RIGID_TRUCK', 'fuel-delivery-high-pressure-pump', 920.00), ('ARTICULATED_TRUCK', 'fuel-delivery-high-pressure-pump', 1180.00),
    ('RIGID_TRUCK', 'water-pump', 750.00), ('ARTICULATED_TRUCK', 'water-pump', 890.00),
    ('RIGID_TRUCK', 'engine-oil-pump', 640.00), ('ARTICULATED_TRUCK', 'engine-oil-pump', 790.00)
) AS component(vehicle_type, component_key, cost_eur)
    ON component.vehicle_type = fleet.vehicle_type
    AND (component.component_key NOT IN ('transmission-manual', 'transmission-automatic')
         OR component.component_key = CASE fleet.transmission_type
                WHEN 'MANUAL' THEN 'transmission-manual'
                ELSE 'transmission-automatic'
            END)
WHERE NOT EXISTS (
    SELECT 1 FROM vehicle_maintenance_operations existing
    WHERE existing.vehicle_id = fleet.vehicle_id
      AND existing.component_key = component.component_key
);

INSERT INTO trailers (
    license_plate, trailer_type, compatible_vehicle_type, make, model, year,
    max_weight_capacity_kg, max_volume_m3, status, maintenance_auto_triggered
)
SELECT seed.license_plate, seed.trailer_type, seed.compatible_vehicle_type, seed.make,
       seed.model, seed.year, seed.max_weight_capacity_kg, seed.max_volume_m3, 'AVAILABLE', 0
FROM (VALUES
    ('CJ-TR-12', 'REFRIGERATED_TRAILER', 'VAN', 'Schmitz Cargobull', 'SKO City', 2023, 8000, 28),
    ('CJ-TR-13', 'HAZARDOUS_TRAILER', 'RIGID_TRUCK', 'LeciTrailer', 'ADR Box', 2022, 16000, 48),
    ('CJ-TR-14', 'LOW_LOADER_TRAILER', 'ARTICULATED_TRUCK', 'Nooteboom', 'OSDS', 2024, 24000, 70),
    ('CJ-TR-15', 'BULK_TIPPER_TRAILER', 'RIGID_TRUCK', 'Schmitz Cargobull', 'Gotha', 2021, 18000, 42),
    ('CJ-TR-16', 'TANKER_TRAILER', 'ARTICULATED_TRUCK', 'Magyar', 'Chemical Tank', 2023, 24000, 38),
    ('CJ-TR-17', 'CONTAINER_CHASSIS_TRAILER', 'RIGID_TRUCK', 'Kogel', 'Port 45', 2024, 24000, 38),
    ('CJ-TR-19', 'FLATBED_TRAILER', 'RIGID_TRUCK', 'Krone', 'SD Flatliner', 2022, 18000, 44)
) AS seed(license_plate, trailer_type, compatible_vehicle_type, make, model, year, max_weight_capacity_kg, max_volume_m3)
WHERE NOT EXISTS (SELECT 1 FROM trailers t WHERE t.license_plate = seed.license_plate);

INSERT INTO @trailers (license_plate, id)
SELECT license_plate, id
FROM trailers
WHERE license_plate IN (
    'CJ-TR-07', 'CJ-TR-12', 'CJ-TR-13', 'CJ-TR-14',
    'CJ-TR-15', 'CJ-TR-16', 'CJ-TR-17', 'CJ-TR-19'
);

INSERT INTO trailer_maintenance_operations (trailer_id, component_key, performed_at, cost_eur)
SELECT trailer.id, component.component_key, DATEADD(month, -3, @now), component.cost_eur
FROM @trailers trailer
CROSS JOIN (VALUES
    ('brakes', 285.00), ('tyres', 610.00), ('lights', 95.00), ('chassis', 430.00)
) AS component(component_key, cost_eur)
WHERE NOT EXISTS (
    SELECT 1 FROM trailer_maintenance_operations existing
    WHERE existing.trailer_id = trailer.id AND existing.component_key = component.component_key
);

INSERT INTO goods (
    tracking_code, client_name, goods_type, description, weight_kg, revenue_eur,
    volume_m3, pickup_city, delivery_city, status
)
SELECT seed.tracking_code, seed.client_name, seed.goods_type, seed.description,
       seed.weight_kg, seed.revenue_eur, seed.volume_m3, seed.pickup_city, seed.delivery_city, 'DELIVERED'
FROM (VALUES
    ('SLH-000015', 'Ardeal Fresh Foods', 'REFRIGERATED', 'Chilled dairy and produce', 5200, 4280.00, 24.0, 'Cluj-Napoca, Romania', 'Brasov, Romania'),
    ('SLH-000016', 'Transilvania Safety Systems', 'HAZARDOUS', 'ADR packaged industrial materials', 6800, 5925.00, 30.0, 'Cluj-Napoca, Romania', 'Oradea, Romania'),
    ('SLH-000017', 'Carpathian Energy Works', 'OVERSIZED', 'Oversized turbine housing', 18500, 14850.00, 55.0, 'Cluj-Napoca, Romania', 'Sibiu, Romania'),
    ('SLH-000018', 'Somes Grain Cooperative', 'BULK', 'Food-grade grain delivery', 7200, 3650.00, 31.0, 'Cluj-Napoca, Romania', 'Satu Mare, Romania'),
    ('SLH-000019', 'Danube Industrial Oils', 'LIQUID', 'Sealed industrial lubricant shipment', 12500, 7840.00, 25.0, 'Cluj-Napoca, Romania', 'Timisoara, Romania'),
    ('SLH-000020', 'Central Europe Components', 'CONTAINERIZED', 'Containerized machinery parts', 9600, 5160.00, 35.0, 'Cluj-Napoca, Romania', 'Targu Mures, Romania'),
    ('SLH-000021', 'Alba Vehicle Imports', 'VEHICLES', 'Two commercial vans', 5100, 4720.00, 42.0, 'Cluj-Napoca, Romania', 'Alba Iulia, Romania'),
    ('SLH-000022', 'Mures Retail Group', 'GENERAL', 'Retail stock replenishment', 4600, 2980.00, 22.0, 'Cluj-Napoca, Romania', 'Bistrita, Romania')
) AS seed(tracking_code, client_name, goods_type, description, weight_kg, revenue_eur, volume_m3, pickup_city, delivery_city)
WHERE NOT EXISTS (SELECT 1 FROM goods existing WHERE existing.tracking_code = seed.tracking_code);

UPDATE goods
SET revenue_eur = CASE tracking_code
    WHEN 'SLH-000013' THEN 2380.00
    WHEN 'SLH-000015' THEN 4280.00
    WHEN 'SLH-000016' THEN 5925.00
    WHEN 'SLH-000017' THEN 14850.00
    WHEN 'SLH-000018' THEN 3650.00
    WHEN 'SLH-000019' THEN 7840.00
    WHEN 'SLH-000020' THEN 5160.00
    WHEN 'SLH-000021' THEN 4720.00
    WHEN 'SLH-000022' THEN 2980.00
END
WHERE tracking_code IN (
    'SLH-000013', 'SLH-000015', 'SLH-000016', 'SLH-000017', 'SLH-000018',
    'SLH-000019', 'SLH-000020', 'SLH-000021', 'SLH-000022'
) AND status IN ('DELIVERED', 'ARCHIVED');

INSERT INTO @routeSeeds VALUES
    ('SLH-000015', 'mara.pop@haulmaster.example', 'CJ 12 SLH', 'CJ-TR-12', -1, 4, 8, 550000, 'Cluj-Napoca, Romania', 'Brasov, Romania', 45.6579, 25.6012),
    ('SLH-000016', 'radu.ilie@haulmaster.example', 'CJ 13 SLH', 'CJ-TR-13', -2, 3, 6, 306000, 'Cluj-Napoca, Romania', 'Oradea, Romania', 47.0465, 21.9189),
    ('SLH-000017', 'sorin.dobre@haulmaster.example', 'CJ 14 SLH', 'CJ-TR-14', -3, 3, 7, 180000, 'Cluj-Napoca, Romania', 'Sibiu, Romania', 45.7983, 24.1256),
    ('SLH-000018', 'diana.enache@haulmaster.example', 'CJ 15 SLH', 'CJ-TR-15', -5, 3, 6, 160000, 'Cluj-Napoca, Romania', 'Satu Mare, Romania', 47.7928, 22.8857),
    ('SLH-000019', 'cosmin.pavel@haulmaster.example', 'CJ 16 SLH', 'CJ-TR-16', -6, 5, 10, 650000, 'Cluj-Napoca, Romania', 'Timisoara, Romania', 45.7489, 21.2087),
    ('SLH-000020', 'elena.barbu@haulmaster.example', 'CJ 17 SLH', 'CJ-TR-17', -8, 2, 4, 105000, 'Cluj-Napoca, Romania', 'Targu Mures, Romania', 46.5456, 24.5625),
    ('SLH-000021', 'tudor.neagu@haulmaster.example', 'CJ 18 SLH', NULL, -10, 2, 4, 105000, 'Cluj-Napoca, Romania', 'Alba Iulia, Romania', 46.0667, 23.5833),
    ('SLH-000022', 'bianca.muresan@haulmaster.example', 'CJ 19 SLH', 'CJ-TR-19', -12, 2, 4, 125000, 'Cluj-Napoca, Romania', 'Bistrita, Romania', 47.1332, 24.5001);

INSERT INTO routes (
    driver_id, vehicle_id, trailer_id, departure_time, estimated_arrival_time,
    estimated_return_time, returned_at, distance_meters, driving_duration_seconds,
    return_duration_seconds, status, activated_at
)
SELECT d.id, v.id, t.id,
       DATEADD(day, seed.departure_day, @now),
       DATEADD(hour, seed.travel_hours, DATEADD(day, seed.departure_day, @now)),
       DATEADD(hour, seed.return_hours, DATEADD(day, seed.departure_day, @now)),
       DATEADD(hour, seed.return_hours, DATEADD(day, seed.departure_day, @now)),
       seed.distance_meters, seed.travel_hours * 3600,
       (seed.return_hours - seed.travel_hours) * 3600, 'COMPLETED', NULL
FROM @routeSeeds seed
JOIN @drivers d ON d.email = seed.driver_email
JOIN vehicles v ON v.license_plate = seed.vehicle_plate
LEFT JOIN trailers t ON t.license_plate = seed.trailer_plate
WHERE NOT EXISTS (
    SELECT 1 FROM routes existing
    WHERE existing.driver_id = d.id AND existing.vehicle_id = v.id
      AND existing.departure_time = DATEADD(day, seed.departure_day, @now)
);

INSERT INTO @routes (tracking_code, id)
SELECT seed.tracking_code, route.id
FROM @routeSeeds seed
JOIN @drivers d ON d.email = seed.driver_email
JOIN vehicles v ON v.license_plate = seed.vehicle_plate
JOIN routes route
  ON route.driver_id = d.id
 AND route.vehicle_id = v.id
 AND route.departure_time = DATEADD(day, seed.departure_day, @now);

INSERT INTO route_stops (route_id, goods_id, stop_type, stop_order, city, latitude, longitude)
SELECT route.id, goods.id, stop.stop_type, stop.stop_order, stop.city, stop.latitude, stop.longitude
FROM @routes route
JOIN @routeSeeds seed ON seed.tracking_code = route.tracking_code
JOIN goods goods ON goods.tracking_code = seed.tracking_code
CROSS APPLY (VALUES
    ('PICKUP', 1, seed.pickup_city, 46.7712, 23.6236),
    ('DELIVERY', 2, seed.delivery_city, seed.delivery_latitude, seed.delivery_longitude)
) AS stop(stop_type, stop_order, city, latitude, longitude)
WHERE NOT EXISTS (
    SELECT 1 FROM route_stops existing
    WHERE existing.route_id = route.id AND existing.stop_order = stop.stop_order
);

INSERT INTO route_geometry_points (route_id, point_order, latitude, longitude)
SELECT route.id, point.point_order, point.latitude, point.longitude
FROM @routes route
JOIN @routeSeeds seed ON seed.tracking_code = route.tracking_code
CROSS APPLY (VALUES
    (1, 46.7712, 23.6236),
    (2, seed.delivery_latitude, seed.delivery_longitude),
    (3, 46.7712, 23.6236)
) AS point(point_order, latitude, longitude)
WHERE NOT EXISTS (
    SELECT 1 FROM route_geometry_points existing
    WHERE existing.route_id = route.id AND existing.point_order = point.point_order
);
