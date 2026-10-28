ALTER TABLE goods ADD revenue_eur DECIMAL(12, 2) NULL;
ALTER TABLE goods ADD CONSTRAINT ck_goods_revenue_eur CHECK (revenue_eur IS NULL OR revenue_eur >= 0);

ALTER TABLE goods DROP CONSTRAINT ck_goods_type;
ALTER TABLE goods ADD CONSTRAINT ck_goods_type CHECK (
    goods_type IN (
        'GENERAL',
        'REFRIGERATED',
        'HAZARDOUS',
        'OVERSIZED',
        'BULK',
        'LIQUID',
        'CONTAINERIZED',
        'VEHICLES',
        'LIVESTOCK'
    )
);

ALTER TABLE vehicle_maintenance_operations ADD cost_eur DECIMAL(12, 2) NULL;
ALTER TABLE vehicle_maintenance_operations ADD CONSTRAINT ck_vehicle_maintenance_cost_eur
    CHECK (cost_eur IS NULL OR cost_eur >= 0);
CREATE INDEX ix_vehicle_maintenance_performed_at
    ON vehicle_maintenance_operations (performed_at);
CREATE INDEX ix_routes_finance_departure_time
    ON routes (departure_time);

ALTER TABLE trailers ADD compatible_vehicle_type NVARCHAR(30) NULL;
UPDATE trailers
SET compatible_vehicle_type = CASE trailer_type
    WHEN 'VAN_TRAILER' THEN 'VAN'
    WHEN 'RIGID_TRUCK_TRAILER' THEN 'RIGID_TRUCK'
    WHEN 'ARTICULATED_TRUCK_TRAILER' THEN 'ARTICULATED_TRUCK'
END;
ALTER TABLE trailers ALTER COLUMN compatible_vehicle_type NVARCHAR(30) NOT NULL;
ALTER TABLE trailers ADD CONSTRAINT ck_trailers_compatible_vehicle_type
    CHECK (compatible_vehicle_type IN ('VAN', 'RIGID_TRUCK', 'ARTICULATED_TRUCK'));

ALTER TABLE trailers DROP CONSTRAINT ck_trailers_type;
ALTER TABLE trailers ADD CONSTRAINT ck_trailers_type CHECK (
    trailer_type IN (
        'VAN_TRAILER',
        'RIGID_TRUCK_TRAILER',
        'ARTICULATED_TRUCK_TRAILER',
        'BOX_TRAILER',
        'CURTAIN_SIDE_TRAILER',
        'REFRIGERATED_TRAILER',
        'HAZARDOUS_TRAILER',
        'TANKER_TRAILER',
        'FLATBED_TRAILER',
        'LOW_LOADER_TRAILER',
        'CONTAINER_CHASSIS_TRAILER',
        'BULK_TIPPER_TRAILER',
        'LIVESTOCK_TRAILER'
    )
);
