package ro.logistics.smart_logistics.goods.persistence;

import ro.logistics.smart_logistics.goods.domain.Goods;
import ro.logistics.smart_logistics.goods.domain.GoodsStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface GoodsRepository extends JpaRepository<Goods, Long> {
    List<Goods> findAllByOrderByIdAsc();

    List<Goods> findAllByStatusOrderByIdAsc(GoodsStatus status);

    boolean existsByTrackingCode(String trackingCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Goods g where g.id in :ids order by g.id")
    List<Goods> findAllByIdForUpdate(@Param("ids") List<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Goods g where g.id = :id")
    Optional<Goods> findByIdForUpdate(@Param("id") Long id);
}
