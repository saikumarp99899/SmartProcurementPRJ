package com.smartprocure.repository;

import com.smartprocure.entity.PurchaseOrderItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PurchaseOrderItemRepository extends JpaRepository<PurchaseOrderItem, Long> {

    @Query("SELECT poi FROM PurchaseOrderItem poi WHERE LOWER(poi.itemName) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<PurchaseOrderItem> findByItemNameContainingIgnoreCase(@Param("name") String name);

    @Query("SELECT DISTINCT poi.itemName FROM PurchaseOrderItem poi WHERE LOWER(poi.itemName) LIKE LOWER(CONCAT(:prefix, '%')) ORDER BY poi.itemName")
    List<String> findItemNamesByPrefix(@Param("prefix") String prefix, Pageable pageable);

    @Query("SELECT poi FROM PurchaseOrderItem poi WHERE LOWER(poi.itemName) = LOWER(:name) ORDER BY poi.purchaseOrder.orderDate DESC")
    List<PurchaseOrderItem> findByItemNameIgnoreCase(@Param("name") String name, Pageable pageable);

    /**
     * Search PO items by name (case-insensitive LIKE), limited results.
     */
    @Query("SELECT poi FROM PurchaseOrderItem poi WHERE LOWER(poi.itemName) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<PurchaseOrderItem> searchByItemName(@Param("query") String query, Pageable pageable);
}
