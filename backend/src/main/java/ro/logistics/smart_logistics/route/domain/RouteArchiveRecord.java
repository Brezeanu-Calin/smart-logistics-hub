package ro.logistics.smart_logistics.route.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "route_archive_records")
public class RouteArchiveRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "route_id", nullable = false)
    private Long routeId;

    @Column(name = "archived_at", nullable = false, columnDefinition = "datetimeoffset")
    private OffsetDateTime archivedAt;

    @Column(name = "snapshot_json", nullable = false, columnDefinition = "nvarchar(max)")
    private String snapshotJson;

    protected RouteArchiveRecord() {
    }

    public RouteArchiveRecord(Long routeId, OffsetDateTime archivedAt, String snapshotJson) {
        this.routeId = routeId;
        this.archivedAt = archivedAt;
        this.snapshotJson = snapshotJson;
    }

    public void update(OffsetDateTime archivedAt, String snapshotJson) {
        this.archivedAt = archivedAt;
        this.snapshotJson = snapshotJson;
    }
}
