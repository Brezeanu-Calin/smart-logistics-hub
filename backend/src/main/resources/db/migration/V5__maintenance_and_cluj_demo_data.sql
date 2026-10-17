ALTER TABLE vehicles ADD transmission_type NVARCHAR(20) NOT NULL
    CONSTRAINT df_vehicles_transmission_type DEFAULT 'MANUAL';
GO
ALTER TABLE vehicles ADD maintenance_auto_triggered BIT NOT NULL
    CONSTRAINT df_vehicles_maintenance_auto_triggered DEFAULT 0;
GO
ALTER TABLE vehicles ADD CONSTRAINT ck_vehicles_transmission_type
    CHECK (transmission_type IN ('MANUAL', 'AUTOMATIC', 'AMT'));
GO
EXEC(N'UPDATE vehicles SET transmission_type = ''AMT''
    WHERE vehicle_type IN (''RIGID_TRUCK'', ''ARTICULATED_TRUCK'');');
GO

CREATE TABLE vehicle_maintenance_operations (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    vehicle_id BIGINT NOT NULL,
    component_key NVARCHAR(64) NOT NULL,
    performed_at DATETIMEOFFSET NOT NULL,
    odometer_km INT NOT NULL,
    CONSTRAINT fk_vehicle_maintenance_operations_vehicle FOREIGN KEY (vehicle_id)
        REFERENCES vehicles (id) ON DELETE CASCADE,
    CONSTRAINT ck_vehicle_maintenance_operations_odometer CHECK (odometer_km >= 0)
);

CREATE INDEX ix_vehicle_maintenance_latest
    ON vehicle_maintenance_operations (vehicle_id, component_key, performed_at, id);

DECLARE @now DATETIMEOFFSET = TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00');
DECLARE @drivers TABLE (email NVARCHAR(254) NOT NULL, id BIGINT NOT NULL);
DECLARE @vehicles TABLE (
    license_plate NVARCHAR(20) NOT NULL,
    id BIGINT NOT NULL,
    vehicle_type NVARCHAR(30) NOT NULL,
    transmission_type NVARCHAR(20) NOT NULL,
    current_mileage_km INT NOT NULL
);

INSERT INTO drivers (name, email, status)
VALUES
    ('Alexandru Popescu', 'alexandru.popescu@demo.smartlogistics.local', 'AVAILABLE'),
    ('Irina Ionescu', 'irina.ionescu@demo.smartlogistics.local', 'AVAILABLE'),
    ('Mihai Dumitru', 'mihai.dumitru@demo.smartlogistics.local', 'AVAILABLE'),
    ('Elena Stan', 'elena.stan@demo.smartlogistics.local', 'AVAILABLE'),
    ('Andrei Marin', 'andrei.marin@demo.smartlogistics.local', 'AVAILABLE'),
    ('Ioana Radu', 'ioana.radu@demo.smartlogistics.local', 'AVAILABLE');

INSERT INTO @drivers (email, id)
SELECT email, id
FROM drivers
WHERE email IN (
    'alexandru.popescu@demo.smartlogistics.local',
    'irina.ionescu@demo.smartlogistics.local',
    'mihai.dumitru@demo.smartlogistics.local',
    'elena.stan@demo.smartlogistics.local',
    'andrei.marin@demo.smartlogistics.local',
    'ioana.radu@demo.smartlogistics.local'
);

INSERT INTO driver_license_categories (driver_id, license_category)
SELECT d.id, licenses.license_category
FROM @drivers d
JOIN (VALUES
    ('alexandru.popescu@demo.smartlogistics.local', 'B'),
    ('irina.ionescu@demo.smartlogistics.local', 'B'),
    ('mihai.dumitru@demo.smartlogistics.local', 'C'),
    ('elena.stan@demo.smartlogistics.local', 'CE'),
    ('andrei.marin@demo.smartlogistics.local', 'C'),
    ('ioana.radu@demo.smartlogistics.local', 'BE')
) AS licenses(email, license_category) ON licenses.email = d.email;

INSERT INTO vehicles (
    license_plate, vehicle_type, make, model, year, max_weight_capacity_kg,
    max_volume_m3, status, current_mileage_km, transmission_type, maintenance_auto_triggered
)
VALUES
    ('CJ 01 SLH', 'VAN', 'Renault', 'Master', 2022, 1500, 12, 'AVAILABLE', 118000, 'MANUAL', 0),
    ('CJ 02 SLH', 'VAN', 'Ford', 'Transit', 2023, 1500, 13, 'AVAILABLE', 72000, 'AUTOMATIC', 0),
    ('CJ 03 SLH', 'RIGID_TRUCK', 'DAF', 'LF', 2021, 9000, 45, 'AVAILABLE', 310000, 'AMT', 0),
    ('CJ 04 SLH', 'RIGID_TRUCK', 'MAN', 'TGM', 2022, 10000, 50, 'AVAILABLE', 245000, 'AMT', 0),
    ('CJ 05 SLH', 'ARTICULATED_TRUCK', 'Scania', 'R450', 2021, 24000, 90, 'AVAILABLE', 620000, 'AMT', 0),
    ('CJ 06 SLH', 'ARTICULATED_TRUCK', 'Volvo', 'FH', 2020, 24000, 90, 'AVAILABLE', 790000, 'AMT', 0);

INSERT INTO @vehicles (license_plate, id, vehicle_type, transmission_type, current_mileage_km)
SELECT license_plate, id, vehicle_type, transmission_type, current_mileage_km
FROM vehicles
WHERE license_plate IN ('CJ 01 SLH', 'CJ 02 SLH', 'CJ 03 SLH', 'CJ 04 SLH', 'CJ 05 SLH', 'CJ 06 SLH');

INSERT INTO vehicle_maintenance_operations (vehicle_id, component_key, performed_at, odometer_km)
SELECT
    v.id,
    components.component_key,
    CASE
        WHEN v.license_plate = 'CJ 06 SLH' AND components.component_key = 'engine-oil-filter'
            THEN DATEADD(month, -11, @now)
        ELSE DATEADD(day, -60, @now)
    END,
    CASE
        WHEN v.license_plate = 'CJ 06 SLH' AND components.component_key = 'engine-oil-filter'
            THEN v.current_mileage_km - 55000
        ELSE v.current_mileage_km - CAST(v.current_mileage_km * 0.03 AS INT)
    END
FROM @vehicles v
JOIN (VALUES
    ('VAN', 'engine-oil-filter', 'ANY'),
    ('VAN', 'coolant-flush', 'ANY'),
    ('VAN', 'brake-pads-rotors', 'ANY'),
    ('VAN', 'tire-replacement', 'ANY'),
    ('VAN', 'transmission-manual', 'MANUAL'),
    ('VAN', 'transmission-automatic', 'AUTOMATIC'),
    ('VAN', 'timing-belt-water-pump', 'ANY'),
    ('VAN', 'timing-chain-tensioners', 'ANY'),
    ('VAN', 'clutch-assembly', 'ANY'),
    ('VAN', 'fuel-pump-module', 'ANY'),
    ('VAN', 'water-pump', 'ANY'),
    ('VAN', 'engine-oil-pump', 'ANY'),
    ('RIGID_TRUCK', 'engine-oil-filter', 'ANY'),
    ('ARTICULATED_TRUCK', 'engine-oil-filter', 'ANY'),
    ('RIGID_TRUCK', 'coolant-flush', 'ANY'),
    ('ARTICULATED_TRUCK', 'coolant-flush', 'ANY'),
    ('RIGID_TRUCK', 'brake-pads-shoes-discs', 'ANY'),
    ('ARTICULATED_TRUCK', 'brake-pads-shoes-discs', 'ANY'),
    ('RIGID_TRUCK', 'steer-axle-tires', 'ANY'),
    ('ARTICULATED_TRUCK', 'steer-axle-tires', 'ANY'),
    ('RIGID_TRUCK', 'drive-axle-tires', 'ANY'),
    ('ARTICULATED_TRUCK', 'drive-axle-tires', 'ANY'),
    ('RIGID_TRUCK', 'transmission-amt', 'ANY'),
    ('ARTICULATED_TRUCK', 'transmission-amt', 'ANY'),
    ('RIGID_TRUCK', 'differential-axle-oil', 'ANY'),
    ('ARTICULATED_TRUCK', 'differential-axle-oil', 'ANY'),
    ('RIGID_TRUCK', 'timing-gears-chain', 'ANY'),
    ('ARTICULATED_TRUCK', 'timing-gears-chain', 'ANY'),
    ('RIGID_TRUCK', 'clutch-assembly-actuator', 'ANY'),
    ('ARTICULATED_TRUCK', 'clutch-assembly-actuator', 'ANY'),
    ('RIGID_TRUCK', 'fuel-delivery-high-pressure-pump', 'ANY'),
    ('ARTICULATED_TRUCK', 'fuel-delivery-high-pressure-pump', 'ANY'),
    ('RIGID_TRUCK', 'water-pump', 'ANY'),
    ('ARTICULATED_TRUCK', 'water-pump', 'ANY'),
    ('RIGID_TRUCK', 'engine-oil-pump', 'ANY'),
    ('ARTICULATED_TRUCK', 'engine-oil-pump', 'ANY')
) AS components(vehicle_type, component_key, required_transmission)
    ON components.vehicle_type = v.vehicle_type
    AND (components.required_transmission = 'ANY' OR components.required_transmission = v.transmission_type);

UPDATE vehicles
SET status = 'MAINTENANCE', maintenance_auto_triggered = 1
WHERE license_plate = 'CJ 06 SLH';

INSERT INTO goods (
    tracking_code, client_name, goods_type, description, weight_kg, volume_m3,
    pickup_city, delivery_city, status
)
VALUES
    ('SLH-000001', 'Carpathia Foods', 'REFRIGERATED', 'Chilled dairy shipment', 850, 6.2, 'Cluj-Napoca, Romania', 'Brasov, Romania', 'ASSIGNED'),
    ('SLH-000002', 'Transylvania Parts', 'GENERAL', 'Palletized vehicle parts', 1200, 8.5, 'Cluj-Napoca, Romania', 'Oradea, Romania', 'ASSIGNED'),
    ('SLH-000003', 'Greenfield Produce', 'GENERAL', 'Packaged produce', 700, 5.4, 'Cluj-Napoca, Romania', 'Sibiu, Romania', 'ASSIGNED'),
    ('SLH-000004', 'Alba Packaging', 'GENERAL', 'Packaging materials', 420, 3.1, 'Cluj-Napoca, Romania', 'Alba Iulia, Romania', 'UNASSIGNED'),
    ('SLH-000005', 'Mures Medical', 'GENERAL', 'Palletized medical supplies', 360, 2.8, 'Cluj-Napoca, Romania', 'Targu Mures, Romania', 'UNASSIGNED'),
    ('SLH-000006', 'Bistrita Timber', 'GENERAL', 'Processed timber products', 1800, 11.5, 'Cluj-Napoca, Romania', 'Bistrita, Romania', 'UNASSIGNED'),
    ('SLH-000007', 'Somes Electronics', 'GENERAL', 'Electronic equipment', 500, 4.2, 'Cluj-Napoca, Romania', 'Baia Mare, Romania', 'UNASSIGNED'),
    ('SLH-000008', 'Crisana Chemical', 'HAZARDOUS', 'Sealed regulated materials', 650, 3.7, 'Cluj-Napoca, Romania', 'Oradea, Romania', 'UNASSIGNED'),
    ('SLH-000009', 'Brasov Home Goods', 'GENERAL', 'Household goods', 900, 7.0, 'Cluj-Napoca, Romania', 'Brasov, Romania', 'UNASSIGNED');

DECLARE @routeIds TABLE (tracking_code NVARCHAR(13) NOT NULL, id BIGINT NOT NULL);
DECLARE @insertedRoutes TABLE (id BIGINT NOT NULL, driver_id BIGINT NOT NULL, vehicle_id BIGINT NOT NULL);

INSERT INTO routes (
    driver_id, vehicle_id, trailer_id, departure_time, estimated_arrival_time,
    estimated_return_time, distance_meters, driving_duration_seconds,
    return_duration_seconds, status
)
OUTPUT inserted.id, inserted.driver_id, inserted.vehicle_id
    INTO @insertedRoutes (id, driver_id, vehicle_id)
SELECT
    d.id,
    v.id,
    NULL,
    DATEADD(day, source.departure_day, @now),
    DATEADD(hour, source.outbound_hours, DATEADD(day, source.departure_day, @now)),
    DATEADD(hour, source.round_trip_hours, DATEADD(day, source.departure_day, @now)),
    source.distance_meters,
    source.outbound_hours * 3600,
    (source.round_trip_hours - source.outbound_hours) * 3600,
    'PLANNED'
FROM (VALUES
    ('SLH-000001', 'mihai.dumitru@demo.smartlogistics.local', 'CJ 03 SLH', 'Brasov, Romania', 1, 4, 8, 550000, 45.6579, 25.6012),
    ('SLH-000002', 'alexandru.popescu@demo.smartlogistics.local', 'CJ 01 SLH', 'Oradea, Romania', 2, 3, 6, 306000, 47.0465, 21.9189),
    ('SLH-000003', 'andrei.marin@demo.smartlogistics.local', 'CJ 04 SLH', 'Sibiu, Romania', 3, 3, 6, 340000, 45.7983, 24.1256)
) AS source(tracking_code, driver_email, license_plate, delivery_city, departure_day, outbound_hours, round_trip_hours, distance_meters, delivery_latitude, delivery_longitude)
JOIN @drivers d ON d.email = source.driver_email
JOIN @vehicles v ON v.license_plate = source.license_plate;

INSERT INTO @routeIds (tracking_code, id)
SELECT seeds.tracking_code, newRoutes.id
FROM @insertedRoutes newRoutes
JOIN @drivers d ON d.id = newRoutes.driver_id
JOIN @vehicles v ON v.id = newRoutes.vehicle_id
JOIN (VALUES
    ('SLH-000001', 'mihai.dumitru@demo.smartlogistics.local', 'CJ 03 SLH'),
    ('SLH-000002', 'alexandru.popescu@demo.smartlogistics.local', 'CJ 01 SLH'),
    ('SLH-000003', 'andrei.marin@demo.smartlogistics.local', 'CJ 04 SLH')
) AS seeds(tracking_code, driver_email, license_plate)
    ON seeds.driver_email = d.email
    AND seeds.license_plate = v.license_plate;

INSERT INTO route_stops (route_id, goods_id, stop_type, stop_order, city, latitude, longitude)
SELECT r.id, g.id, stops.stop_type, stops.stop_order, stops.city, stops.latitude, stops.longitude
FROM @routeIds r
JOIN goods g ON g.tracking_code = r.tracking_code
JOIN (VALUES
    ('SLH-000001', 'PICKUP', 1, 'Cluj-Napoca, Romania', 46.7712, 23.6236),
    ('SLH-000001', 'DELIVERY', 2, 'Brasov, Romania', 45.6579, 25.6012),
    ('SLH-000002', 'PICKUP', 1, 'Cluj-Napoca, Romania', 46.7712, 23.6236),
    ('SLH-000002', 'DELIVERY', 2, 'Oradea, Romania', 47.0465, 21.9189),
    ('SLH-000003', 'PICKUP', 1, 'Cluj-Napoca, Romania', 46.7712, 23.6236),
    ('SLH-000003', 'DELIVERY', 2, 'Sibiu, Romania', 45.7983, 24.1256)
) AS stops(tracking_code, stop_type, stop_order, city, latitude, longitude)
    ON stops.tracking_code = r.tracking_code;

INSERT INTO route_geometry_points (route_id, point_order, latitude, longitude)
SELECT r.id, points.point_order, points.latitude, points.longitude
FROM @routeIds r
JOIN (VALUES
    ('SLH-000001', 1, 46.7712, 23.6236), ('SLH-000001', 2, 45.6579, 25.6012), ('SLH-000001', 3, 46.7712, 23.6236),
    ('SLH-000002', 1, 46.7712, 23.6236), ('SLH-000002', 2, 47.0465, 21.9189), ('SLH-000002', 3, 46.7712, 23.6236),
    ('SLH-000003', 1, 46.7712, 23.6236), ('SLH-000003', 2, 45.7983, 24.1256), ('SLH-000003', 3, 46.7712, 23.6236)
) AS points(tracking_code, point_order, latitude, longitude)
    ON points.tracking_code = r.tracking_code;
