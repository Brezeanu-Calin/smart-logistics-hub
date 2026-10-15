CREATE TABLE geocoded_addresses (
    address_hash NVARCHAR(64) NOT NULL PRIMARY KEY,
    normalized_address NVARCHAR(1000) NOT NULL,
    latitude FLOAT(53) NOT NULL,
    longitude FLOAT(53) NOT NULL,
    CONSTRAINT ck_geocoded_addresses_latitude CHECK (latitude >= -90 AND latitude <= 90),
    CONSTRAINT ck_geocoded_addresses_longitude CHECK (longitude >= -180 AND longitude <= 180)
);

ALTER TABLE route_stops ADD latitude FLOAT(53) NULL;
ALTER TABLE route_stops ADD longitude FLOAT(53) NULL;
ALTER TABLE route_stops ADD CONSTRAINT ck_route_stops_coordinates
    CHECK ((latitude IS NULL AND longitude IS NULL) OR (latitude IS NOT NULL AND longitude IS NOT NULL));

CREATE TABLE route_geometry_points (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    route_id BIGINT NOT NULL,
    point_order INT NOT NULL,
    latitude FLOAT(53) NOT NULL,
    longitude FLOAT(53) NOT NULL,
    CONSTRAINT fk_route_geometry_points_route FOREIGN KEY (route_id)
        REFERENCES routes (id) ON DELETE CASCADE,
    CONSTRAINT uk_route_geometry_order UNIQUE (route_id, point_order),
    CONSTRAINT ck_route_geometry_order CHECK (point_order > 0),
    CONSTRAINT ck_route_geometry_latitude CHECK (latitude >= -90 AND latitude <= 90),
    CONSTRAINT ck_route_geometry_longitude CHECK (longitude >= -180 AND longitude <= 180)
);
