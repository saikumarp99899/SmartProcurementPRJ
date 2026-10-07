package com.smartprocure.repository;

import com.smartprocure.entity.ApprovalRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface ApprovalRuleRepository extends JpaRepository<ApprovalRule, Long> {

    /**
     * Active rules whose amount band contains the given amount, in chain order.
     * A NULL max_amount means the band has no upper limit.
     */
    @Query("""
           SELECT r FROM ApprovalRule r
           WHERE r.active = true
             AND r.minAmount <= :amount
             AND (r.maxAmount IS NULL OR r.maxAmount >= :amount)
           ORDER BY r.approvalLevel ASC
           """)
    List<ApprovalRule> findMatchingRules(@Param("amount") BigDecimal amount);

    List<ApprovalRule> findAllByOrderByMinAmountAscApprovalLevelAsc();

    boolean existsByApproverId(Long approverId);
}
