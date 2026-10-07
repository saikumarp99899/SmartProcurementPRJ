package com.smartprocure.repository;

import com.smartprocure.entity.RequisitionItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RequisitionItemRepository extends JpaRepository<RequisitionItem, Long> {

    @Query("SELECT DISTINCT ri.itemName FROM RequisitionItem ri WHERE LOWER(ri.itemName) LIKE LOWER(CONCAT(:prefix, '%')) ORDER BY ri.itemName")
    List<String> findItemNamesByPrefix(@Param("prefix") String prefix, Pageable pageable);

    /**
     * Search items by name (case-insensitive LIKE). Returns distinct items with their IDs.
     */
    @Query("SELECT ri FROM RequisitionItem ri WHERE LOWER(ri.itemName) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<RequisitionItem> searchByItemName(@Param("query") String query, Pageable pageable);
}
