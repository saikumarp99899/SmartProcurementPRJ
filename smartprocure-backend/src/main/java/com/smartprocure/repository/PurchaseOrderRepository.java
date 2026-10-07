package com.smartprocure.repository;

import com.smartprocure.entity.PurchaseOrder;
import com.smartprocure.entity.PurchaseOrder.PurchaseOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    Optional<PurchaseOrder> findByPoNumber(String poNumber);

    boolean existsByRequisitionId(Long requisitionId);

    Page<PurchaseOrder> findByCreatedById(Long userId, Pageable pageable);

    Page<PurchaseOrder> findByVendorId(Long vendorId, Pageable pageable);

    Page<PurchaseOrder> findByStatus(PurchaseOrderStatus status, Pageable pageable);

    long countByStatus(PurchaseOrderStatus status);

    /**
     * Sum all PO amounts for the dashboard "Total Procurement Spend" card.
     * COALESCE handles the case when there are no POs (would return null).
     */
    @Query("SELECT COALESCE(SUM(p.totalAmount), 0) FROM PurchaseOrder p WHERE p.status != :excludedStatus")
    BigDecimal calculateTotalSpend(@Param("excludedStatus") PurchaseOrderStatus excludedStatus);

    @Query("SELECT COALESCE(SUM(p.totalAmount), 0) FROM PurchaseOrder p WHERE p.createdBy.id = :userId AND p.status != :excludedStatus")
    BigDecimal calculateTotalSpendByUser(@Param("userId") Long userId, @Param("excludedStatus") PurchaseOrderStatus excludedStatus);

    long countByCreatedByIdAndStatus(Long userId, PurchaseOrderStatus status);

    /**
     * Search purchase orders by PO number (case-insensitive LIKE).
     */
    @Query("SELECT p FROM PurchaseOrder p WHERE LOWER(p.poNumber) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<PurchaseOrder> searchByPoNumber(@Param("query") String query, Pageable pageable);
}
