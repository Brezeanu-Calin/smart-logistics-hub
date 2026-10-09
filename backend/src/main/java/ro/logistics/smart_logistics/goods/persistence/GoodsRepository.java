package ro.logistics.smart_logistics.goods.persistence;

import ro.logistics.smart_logistics.goods.domain.Goods;
import ro.logistics.smart_logistics.goods.domain.GoodsStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GoodsRepository extends JpaRepository<Goods, Long> {
    List<Goods> findAllByOrderByIdAsc();

    List<Goods> findAllByStatusOrderByIdAsc(GoodsStatus status);

    boolean existsByTrackingCode(String trackingCode);
}
