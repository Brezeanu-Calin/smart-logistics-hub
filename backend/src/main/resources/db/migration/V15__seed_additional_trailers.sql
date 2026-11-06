DECLARE @now DATETIMEOFFSET = TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00');
DECLARE @trailers TABLE (license_plate NVARCHAR(20) NOT NULL, id BIGINT NOT NULL);

INSERT INTO trailers (
    license_plate, trailer_type, compatible_vehicle_type, make, model, year,
    max_weight_capacity_kg, max_volume_m3, status, maintenance_auto_triggered
)
SELECT seed.license_plate, seed.trailer_type, seed.compatible_vehicle_type, seed.make,
       seed.model, seed.year, seed.max_weight_capacity_kg, seed.max_volume_m3, 'AVAILABLE', 0
FROM (VALUES
    ('CJ-TR-20', 'BOX_TRAILER', 'VAN', 'Humbaur', 'HK 253015', 2024, 2500, 15),
    ('CJ-TR-21', 'CURTAIN_SIDE_TRAILER', 'ARTICULATED_TRUCK', 'Krone', 'Profi Liner', 2023, 24000, 92),
    ('CJ-TR-22', 'LIVESTOCK_TRAILER', 'RIGID_TRUCK', 'Bockmann', 'Big Master', 2022, 12000, 38),
    ('CJ-TR-23', 'VAN_TRAILER', 'VAN', 'Brian James', 'Cargo Digger', 2023, 3000, 18),
    ('CJ-TR-24', 'RIGID_TRUCK_TRAILER', 'RIGID_TRUCK', 'Schmitz Cargobull', 'M.KO', 2024, 16000, 56),
    ('CJ-TR-25', 'REFRIGERATED_TRAILER', 'ARTICULATED_TRUCK', 'Schmitz Cargobull', 'S.KO COOL', 2024, 24000, 86),
    ('CJ-TR-26', 'HAZARDOUS_TRAILER', 'ARTICULATED_TRUCK', 'LeciTrailer', 'ADR Tank', 2023, 24000, 36)
) AS seed(license_plate, trailer_type, compatible_vehicle_type, make, model, year,
          max_weight_capacity_kg, max_volume_m3)
WHERE NOT EXISTS (
    SELECT 1 FROM trailers existing WHERE existing.license_plate = seed.license_plate
);

INSERT INTO @trailers (license_plate, id)
SELECT license_plate, id
FROM trailers
WHERE license_plate IN (
    'CJ-TR-20', 'CJ-TR-21', 'CJ-TR-22', 'CJ-TR-23',
    'CJ-TR-24', 'CJ-TR-25', 'CJ-TR-26'
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
