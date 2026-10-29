CREATE TABLE push_subscriptions (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    endpoint_hash CHAR(64) NOT NULL,
    endpoint NVARCHAR(2048) NOT NULL,
    p256dh_key NVARCHAR(256) NOT NULL,
    auth_key NVARCHAR(128) NOT NULL,
    created_at DATETIMEOFFSET NOT NULL CONSTRAINT df_push_subscriptions_created_at DEFAULT SYSDATETIMEOFFSET()
);

CREATE UNIQUE INDEX ux_push_subscriptions_endpoint_hash
    ON push_subscriptions (endpoint_hash);
