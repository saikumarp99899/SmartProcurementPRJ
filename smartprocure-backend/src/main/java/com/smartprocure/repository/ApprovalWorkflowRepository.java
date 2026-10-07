package com.smartprocure.repository;

import com.smartprocure.entity.ApprovalWorkflow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ApprovalWorkflowRepository extends JpaRepository<ApprovalWorkflow, Long> {

    /**
     * Active workflows that could route the given amount and department.
     *
     * A workflow with no department applies everywhere; one with a department
     * applies only to that department. Final precedence among the candidates
     * is decided in the service, since it depends on more than one field.
     */
    @Query("""
           SELECT DISTINCT w FROM ApprovalWorkflow w
           LEFT JOIN FETCH w.steps
           WHERE w.active = true
             AND w.minAmount <= :amount
             AND (w.maxAmount IS NULL OR w.maxAmount >= :amount)
             AND (w.department IS NULL OR w.department.id = :departmentId)
           """)
    List<ApprovalWorkflow> findCandidates(@Param("amount") BigDecimal amount,
                                          @Param("departmentId") Long departmentId);

    @Query("SELECT DISTINCT w FROM ApprovalWorkflow w LEFT JOIN FETCH w.steps ORDER BY w.priority DESC, w.minAmount ASC")
    List<ApprovalWorkflow> findAllWithSteps();

    @Query("SELECT w FROM ApprovalWorkflow w LEFT JOIN FETCH w.steps WHERE w.id = :id")
    Optional<ApprovalWorkflow> findByIdWithSteps(@Param("id") Long id);

    Optional<ApprovalWorkflow> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}
