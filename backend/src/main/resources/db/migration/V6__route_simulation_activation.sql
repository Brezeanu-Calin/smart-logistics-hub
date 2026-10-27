ALTER TABLE routes ADD activated_at DATETIMEOFFSET NULL;
GO

DECLARE @activated_at DATETIMEOFFSET = TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00');
UPDATE routes SET activated_at = @activated_at WHERE status = 'ACTIVE';
GO

ALTER TABLE routes ADD CONSTRAINT ck_routes_activation
    CHECK (status <> 'ACTIVE' OR activated_at IS NOT NULL);
GO
