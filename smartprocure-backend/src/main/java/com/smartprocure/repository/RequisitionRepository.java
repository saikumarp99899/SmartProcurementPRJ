package com.smartprocure.repository;

import com.smartprocure.entity.Requisition;
import com.smartprocure.entity.Requisition.RequisitionStatus;
import com.smartprocure.entity.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface RequisitionRepository extends JpaRepository<Requisition, Long> {

    Optional<Requisition> findByRequisitionNumber(String requisitionNumber);

    /**
     * Find all requisitions by a specific requester (user).
     * Used in Buyer dashboard: "My Requisitions"
     */
    Page<Requisition> findByRequesterId(Long requesterId, Pageable pageable);

    /**
     * Requester's requisitions narrowed by status.
     * Backs the deep-links from the Buyer dashboard cards.
     */
    Page<Requisition> findByRequesterIdAndStatus(Long requesterId, RequisitionStatus status, Pageable pageable);

    /**
     * Find requisitions by status — used by Approver to get SUBMITTED ones.
     */
    Page<Requisition> findByStatus(RequisitionStatus status, Pageable pageable);

    /**
     * Combined search for admin: filter by status and/or requester.
     */
    @Query("SELECT r FROM Requisition r WHERE " +
           "(:status IS NULL OR r.status = :status) AND " +
           "(:requesterId IS NULL OR r.requester.id = :requesterId)")
    Page<Requisition> findByStatusAndRequesterId(@Param("status") RequisitionStatus status,
                                                  @Param("requesterId") Long requesterId,
                                                  Pageable pageable);

    long countByStatus(RequisitionStatus status);

    long countByRequesterId(Long requesterId);

    long countByRequesterIdAndStatus(Long requesterId, RequisitionStatus status);

    /**
     * Check if a PO already exists for this requisition.
     * Used to prevent duplicate PO creation.
     */
    @Query("SELECT COUNT(r) > 0 FROM Requisition r WHERE r.id = :id AND r.status = :status")
    boolean hasActivePO(@Param("id") Long id, @Param("status") RequisitionStatus status);

    /**
     * Search requisitions by number or description (case-insensitive LIKE).
     * No role-based filtering — caller is responsible for applying it.
     */
    @Query("SELECT r FROM Requisition r WHERE " +
           "LOWER(r.requisitionNumber) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(r.description) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Requisition> searchByNumberOrDescription(@Param("query") String query, Pageable pageable);

    /**
     * Search own requisitions by number or description (for BUYER role).
     */
    @Query("SELECT r FROM Requisition r WHERE r.requester.id = :requesterId AND " +
           "(LOWER(r.requisitionNumber) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(r.description) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Requisition> searchByNumberOrDescriptionAndRequesterId(
            @Param("query") String query,
            @Param("requesterId") Long requesterId,
            Pageable pageable);

    /**
     * Search requisitions assigned to an approver (by assignedApprover or role).
     */
    @Query("SELECT DISTINCT a.requisition FROM Approval a WHERE " +
           "(a.assignedApprover.id = :approverId OR " +
           "(a.assignedApprover IS NULL AND a.assignedRole IN :roles)) AND " +
           "(LOWER(a.requisition.requisitionNumber) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(a.requisition.description) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Requisition> searchAssignedToApprover(
            @Param("query") String query,
            @Param("approverId") Long approverId,
            @Param("roles") Set<Role.RoleName> roles,
            Pageable pageable);
}
