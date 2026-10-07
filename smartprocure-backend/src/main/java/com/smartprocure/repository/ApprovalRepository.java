package com.smartprocure.repository;

import com.smartprocure.entity.Approval;
import com.smartprocure.entity.Approval.ApprovalStatus;
import com.smartprocure.entity.Role.RoleName;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ApprovalRepository extends JpaRepository<Approval, Long> {

    List<Approval> findByRequisitionId(Long requisitionId);

    /** Chain order matters when displaying or advancing the workflow. */
    List<Approval> findByRequisitionIdOrderByApprovalLevelAsc(Long requisitionId);

    Optional<Approval> findByRequisitionIdAndStatus(Long requisitionId, ApprovalStatus status);

    Page<Approval> findByStatus(ApprovalStatus status, Pageable pageable);

    /**
     * The next queued level after the one just approved.
     * Ordering by level guarantees we promote the immediate successor.
     */
    Optional<Approval> findFirstByRequisitionIdAndStatusAndApprovalLevelGreaterThanOrderByApprovalLevelAsc(
            Long requisitionId, ApprovalStatus status, Integer approvalLevel);

    List<Approval> findByRequisitionIdAndStatusIn(Long requisitionId, List<ApprovalStatus> statuses);

    /**
     * Approvals a given user may act on:
     * - assigned to them individually, or
     * - assigned to a role they hold, or
     * - assigned to nobody (the unrouted fallback).
     *
     * Requisitions they raised themselves are excluded, matching the
     * separation-of-duties rule enforced when actioning an approval.
     */
    @Query("""
           SELECT a FROM Approval a
           WHERE a.status = :status
             AND a.requisition.requester.id <> :approverId
             AND (
                   a.assignedApprover.id = :approverId
                   OR (a.assignedApprover IS NULL AND a.assignedRole IS NULL)
                   OR (a.assignedApprover IS NULL AND a.assignedRole IN :roles)
                 )
           """)
    Page<Approval> findActionableByApprover(@Param("status") ApprovalStatus status,
                                           @Param("approverId") Long approverId,
                                           @Param("roles") Collection<RoleName> roles,
                                           Pageable pageable);

    @Query("""
           SELECT COUNT(a) FROM Approval a
           WHERE a.status = :status
             AND a.requisition.requester.id <> :approverId
             AND (
                   a.assignedApprover.id = :approverId
                   OR (a.assignedApprover IS NULL AND a.assignedRole IS NULL)
                   OR (a.assignedApprover IS NULL AND a.assignedRole IN :roles)
                 )
           """)
    long countActionableByApprover(@Param("status") ApprovalStatus status,
                                   @Param("approverId") Long approverId,
                                   @Param("roles") Collection<RoleName> roles);

    long countByStatus(ApprovalStatus status);

    long countByApproverIdAndStatus(Long approverId, ApprovalStatus status);
}
