DECLARE @now DATETIMEOFFSET = TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00');

INSERT INTO vehicle_maintenance_operations (vehicle_id, component_key, performed_at, odometer_km)
SELECT v.id, component.component_key, DATEADD(day, -30, @now),
       v.current_mileage_km - CAST(v.current_mileage_km * 0.03 AS INT)
FROM vehicles v
JOIN (VALUES
    ('VAN', 'engine-oil-filter', 'ANY'),
    ('VAN', 'coolant-flush', 'ANY'),
    ('VAN', 'brake-pads-rotors', 'ANY'),
    ('VAN', 'tire-replacement', 'ANY'),
    ('VAN', 'transmission-manual', 'MANUAL'),
    ('VAN', 'transmission-automatic', 'AUTOMATIC'),
    ('VAN', 'timing-belt-water-pump', 'ANY'),
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
    ('RIGID_TRUCK', 'transmission-amt', 'AMT'),
    ('ARTICULATED_TRUCK', 'transmission-amt', 'AMT'),
    ('RIGID_TRUCK', 'differential-axle-oil', 'ANY'),
    ('ARTICULATED_TRUCK', 'differential-axle-oil', 'ANY'),
    ('RIGID_TRUCK', 'water-pump', 'ANY'),
    ('ARTICULATED_TRUCK', 'water-pump', 'ANY')
) AS component(vehicle_type, component_key, required_transmission)
    ON component.vehicle_type = v.vehicle_type
    AND (component.required_transmission = 'ANY' OR component.required_transmission = v.transmission_type)
WHERE v.license_plate IN ('CJ 07 SLH', 'CJ 08 SLH', 'CJ 09 SLH', 'CJ 10 SLH', 'CJ 11 SLH')
    AND NOT EXISTS (
        SELECT 1
        FROM vehicle_maintenance_operations existing
        WHERE existing.vehicle_id = v.id AND existing.component_key = component.component_key
    );

UPDATE vehicles
SET status = CASE WHEN license_plate = 'CJ 07 SLH' THEN 'IN_TRANSIT' ELSE 'AVAILABLE' END,
    maintenance_auto_triggered = 0
WHERE license_plate IN ('CJ 07 SLH', 'CJ 08 SLH', 'CJ 09 SLH', 'CJ 10 SLH', 'CJ 11 SLH');
