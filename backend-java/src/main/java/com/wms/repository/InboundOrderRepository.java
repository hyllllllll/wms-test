package com.wms.repository;

import com.wms.entity.InboundOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 入库单 Repository
 */
@Repository
public interface InboundOrderRepository extends JpaRepository<InboundOrder, Long> {

    /**
     * 查询指定时间之后创建的最大序号
     * 用于生成入库单号 IN-YYYYMMDD-XXX
     */
    @Query("SELECT MAX(o.orderNo) FROM InboundOrder o " +
           "WHERE o.createdAt >= :startOfDay AND o.createdAt < :endOfDay")
    Optional<String> findMaxOrderNoToday(@Param("startOfDay") LocalDateTime startOfDay,
                                         @Param("endOfDay") LocalDateTime endOfDay);
}
