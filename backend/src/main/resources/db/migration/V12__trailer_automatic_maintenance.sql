ALTER TABLE trailers ADD maintenance_auto_triggered BIT NOT NULL
    CONSTRAINT df_trailers_maintenance_auto_triggered DEFAULT 0;
GO
