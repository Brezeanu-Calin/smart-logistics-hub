CREATE TABLE drivers (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    name NVARCHAR(100) NOT NULL,
    email NVARCHAR(254) NOT NULL,
    status NVARCHAR(20) NOT NULL CONSTRAINT df_drivers_status DEFAULT 'AVAILABLE',
    CONSTRAINT uk_drivers_email UNIQUE (email),
    CONSTRAINT ck_drivers_status CHECK (status IN ('AVAILABLE', 'ON_LEAVE', 'IN_TRANSIT'))
);

CREATE TABLE driver_license_categories (
    driver_id BIGINT NOT NULL,
    license_category NVARCHAR(2) NOT NULL,
    CONSTRAINT pk_driver_license_categories PRIMARY KEY (driver_id, license_category),
    CONSTRAINT fk_driver_license_categories_driver FOREIGN KEY (driver_id)
        REFERENCES drivers (id) ON DELETE CASCADE,
    CONSTRAINT ck_driver_license_categories_value CHECK (license_category IN ('B', 'BE', 'C', 'CE'))
);

CREATE TABLE vehicles (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    license_plate NVARCHAR(20) NOT NULL,
    vehicle_type NVARCHAR(30) NOT NULL,
    make NVARCHAR(100) NOT NULL,
    model NVARCHAR(100) NOT NULL,
    year INT NULL,
    max_weight_capacity_kg FLOAT NOT NULL,
    max_volume_m3 FLOAT NOT NULL,
    status NVARCHAR(20) NOT NULL CONSTRAINT df_vehicles_status DEFAULT 'AVAILABLE',
    current_mileage_km INT NOT NULL,
    CONSTRAINT uk_vehicles_license_plate UNIQUE (license_plate),
    CONSTRAINT ck_vehicles_type CHECK (vehicle_type IN ('VAN', 'RIGID_TRUCK', 'ARTICULATED_TRUCK')),
    CONSTRAINT ck_vehicles_status CHECK (status IN ('AVAILABLE', 'IN_TRANSIT', 'MAINTENANCE')),
    CONSTRAINT ck_vehicles_capacity CHECK (max_weight_capacity_kg > 0 AND max_volume_m3 > 0),
    CONSTRAINT ck_vehicles_mileage CHECK (current_mileage_km >= 0)
);

CREATE TABLE trailers (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    license_plate NVARCHAR(20) NOT NULL,
    trailer_type NVARCHAR(32) NOT NULL,
    make NVARCHAR(100) NOT NULL,
    model NVARCHAR(100) NOT NULL,
    year INT NOT NULL,
    max_weight_capacity_kg FLOAT NOT NULL,
    max_volume_m3 FLOAT NOT NULL,
    status NVARCHAR(20) NOT NULL CONSTRAINT df_trailers_status DEFAULT 'AVAILABLE',
    CONSTRAINT uk_trailers_license_plate UNIQUE (license_plate),
    CONSTRAINT ck_trailers_type CHECK (
        trailer_type IN ('VAN_TRAILER', 'RIGID_TRUCK_TRAILER', 'ARTICULATED_TRUCK_TRAILER')
    ),
    CONSTRAINT ck_trailers_status CHECK (status IN ('AVAILABLE', 'IN_TRANSIT', 'MAINTENANCE')),
    CONSTRAINT ck_trailers_capacity CHECK (max_weight_capacity_kg > 0 AND max_volume_m3 > 0)
);

CREATE TABLE goods (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    tracking_code NVARCHAR(13) NOT NULL,
    client_name NVARCHAR(150) NOT NULL,
    goods_type NVARCHAR(20) NOT NULL,
    description NVARCHAR(1000) NULL,
    weight_kg FLOAT NOT NULL,
    volume_m3 FLOAT NOT NULL,
    pickup_address NVARCHAR(500) NOT NULL,
    delivery_address NVARCHAR(500) NOT NULL,
    status NVARCHAR(20) NOT NULL CONSTRAINT df_goods_status DEFAULT 'UNASSIGNED',
    CONSTRAINT uk_goods_tracking_code UNIQUE (tracking_code),
    CONSTRAINT ck_goods_type CHECK (goods_type IN ('GENERAL', 'REFRIGERATED', 'HAZARDOUS')),
    CONSTRAINT ck_goods_status CHECK (status IN ('UNASSIGNED', 'ASSIGNED', 'IN_TRANSIT', 'DELIVERED')),
    CONSTRAINT ck_goods_capacity CHECK (weight_kg > 0 AND volume_m3 > 0)
);

CREATE TABLE routes (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    driver_id BIGINT NOT NULL,
    vehicle_id BIGINT NOT NULL,
    trailer_id BIGINT NULL,
    departure_time DATETIMEOFFSET NOT NULL,
    estimated_arrival_time DATETIMEOFFSET NOT NULL,
    status NVARCHAR(20) NOT NULL CONSTRAINT df_routes_status DEFAULT 'PLANNED',
    CONSTRAINT fk_routes_driver FOREIGN KEY (driver_id) REFERENCES drivers (id),
    CONSTRAINT fk_routes_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id),
    CONSTRAINT fk_routes_trailer FOREIGN KEY (trailer_id) REFERENCES trailers (id),
    CONSTRAINT ck_routes_status CHECK (status IN ('PLANNED', 'ACTIVE', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_routes_schedule CHECK (estimated_arrival_time > departure_time)
);

CREATE INDEX ix_routes_driver_schedule ON routes (driver_id, status, departure_time, estimated_arrival_time);
CREATE INDEX ix_routes_vehicle_schedule ON routes (vehicle_id, status, departure_time, estimated_arrival_time);
CREATE INDEX ix_routes_trailer_schedule ON routes (trailer_id, status, departure_time, estimated_arrival_time);

CREATE TABLE route_stops (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    route_id BIGINT NOT NULL,
    goods_id BIGINT NOT NULL,
    stop_type NVARCHAR(12) NOT NULL,
    stop_order INT NOT NULL,
    address NVARCHAR(500) NOT NULL,
    CONSTRAINT fk_route_stops_route FOREIGN KEY (route_id) REFERENCES routes (id) ON DELETE CASCADE,
    CONSTRAINT fk_route_stops_goods FOREIGN KEY (goods_id) REFERENCES goods (id),
    CONSTRAINT uk_route_stop_order UNIQUE (route_id, stop_order),
    CONSTRAINT uk_route_goods_stop_type UNIQUE (route_id, goods_id, stop_type),
    CONSTRAINT ck_route_stops_type CHECK (stop_type IN ('PICKUP', 'DELIVERY')),
    CONSTRAINT ck_route_stops_order CHECK (stop_order > 0)
);

CREATE INDEX ix_route_stops_goods ON route_stops (goods_id);
