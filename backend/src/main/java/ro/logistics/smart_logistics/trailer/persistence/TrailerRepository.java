package ro.logistics.smart_logistics.trailer.persistence;

import ro.logistics.smart_logistics.trailer.domain.Trailer;
import ro.logistics.smart_logistics.trailer.domain.TrailerStatus;
import ro.logistics.smart_logistics.trailer.domain.TrailerType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface TrailerRepository extends JpaRepository<Trailer, Long> {
    List<Trailer> findAllByOrderByLicensePlateAsc();

    List<Trailer> findAllByTrailerTypeAndStatusOrderByLicensePlateAsc(TrailerType trailerType, TrailerStatus status);

    boolean existsByLicensePlateIgnoreCase(String licensePlate);

    boolean existsByLicensePlateIgnoreCaseAndIdNot(String licensePlate, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Trailer t where t.id = :id")
    Optional<Trailer> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Trailer t where t.trailerType = :type and t.status in :statuses order by t.licensePlate")
    List<Trailer> findAllForPlanning(
            @Param("type") TrailerType type,
            @Param("statuses") List<TrailerStatus> statuses
    );
}
