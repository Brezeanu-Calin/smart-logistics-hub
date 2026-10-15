ALTER TABLE drivers DROP CONSTRAINT ck_drivers_status;
ALTER TABLE drivers ADD CONSTRAINT ck_drivers_status
    CHECK (status IN ('AVAILABLE', 'ON_LEAVE', 'IN_TRANSIT', 'RETURNING'));

ALTER TABLE goods DROP CONSTRAINT ck_goods_status;
ALTER TABLE goods ADD CONSTRAINT ck_goods_status
    CHECK (status IN ('UNASSIGNED', 'ASSIGNED', 'IN_TRANSIT', 'DELIVERED', 'ARCHIVED'));

ALTER TABLE routes ADD estimated_return_time DATETIMEOFFSET NULL;
ALTER TABLE routes ADD returned_at DATETIMEOFFSET NULL;
ALTER TABLE routes ADD distance_meters BIGINT NOT NULL CONSTRAINT df_routes_distance_meters DEFAULT 0;
ALTER TABLE routes ADD driving_duration_seconds BIGINT NOT NULL
    CONSTRAINT df_routes_driving_duration DEFAULT 0;
ALTER TABLE routes ADD return_duration_seconds BIGINT NOT NULL
    CONSTRAINT df_routes_return_duration DEFAULT 0;

CREATE INDEX ix_routes_return_schedule ON routes (status, estimated_return_time, returned_at);
