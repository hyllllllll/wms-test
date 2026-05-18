package com.wms.repository;

import com.wms.entity.Inventory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 库存 Repository
 * 支持按商品名称/SKU模糊搜索、按仓库筛选和分页查询
 */
@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductIdAndLocationCode(Long productId, String locationCode);

    /**
     * 检查商品是否有库存记录
     * @param productId 商品ID
     * @return true 表示有关联库存
     */
    boolean existsByProductId(Long productId);

    /**
     * 统计商品的库存记录数
     */
    long countByProductId(Long productId);

    /**
     * 分页查询库存列表
     * 关联 Product、Location、Warehouse 表获取完整信息
     * 
     * @param keyword 商品名称或SKU模糊搜索关键字
     * @param warehouseId 仓库ID筛选（可选）
     * @param pageable 分页参数
     * @return 库存分页结果
     */
    @Query("SELECT i FROM Inventory i " +
           "JOIN Product p ON i.productId = p.id " +
           "JOIN Location l ON i.locationCode = l.code " +
           "JOIN Warehouse w ON l.warehouseId = w.id " +
           "WHERE (:keyword IS NULL OR :keyword = '' OR p.name LIKE %:keyword% OR p.sku LIKE %:keyword%) " +
           "AND (:warehouseId IS NULL OR l.warehouseId = :warehouseId) " +
           "ORDER BY i.updatedAt DESC")
    Page<Inventory> findInventoryWithFilters(
            @Param("keyword") String keyword,
            @Param("warehouseId") Long warehouseId,
            Pageable pageable);
}
