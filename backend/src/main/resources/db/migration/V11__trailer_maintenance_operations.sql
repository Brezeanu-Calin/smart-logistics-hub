CREATE TABLE trailer_maintenance_operations (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    trailer_id BIGINT NOT NULL,
    component_key NVARCHAR(64) NOT NULL,
    performed_at DATETIMEOFFSET NOT NULL,
    cost_eur DECIMAL(12, 2) NULL,
    CONSTRAINT fk_trailer_maintenance_operations_trailer FOREIGN KEY (trailer_id)
        REFERENCES trailers (id),
    CONSTRAINT ck_trailer_maintenance_component CHECK (
        component_key IN ('brakes', 'tyres', 'lights', 'chassis')
    ),
    CONSTRAINT ck_trailer_maintenance_cost CHECK (cost_eur IS NULL OR cost_eur >= 0)
);

CREATE INDEX ix_trailer_maintenance_operations_history
    ON trailer_maintenance_operations (trailer_id, performed_at DESC, id DESC);
