package com.smartprocure.config;

import com.smartprocure.entity.ApprovalRule;
import com.smartprocure.entity.ApprovalWorkflow;
import com.smartprocure.entity.ApprovalWorkflowStep;
import com.smartprocure.entity.ApprovalWorkflowStep.ApproverType;
import com.smartprocure.repository.ApprovalRuleRepository;
import com.smartprocure.repository.ApprovalWorkflowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One-time migration from the earlier flat ApprovalRule model to workflows.
 *
 * Rules that shared an amount band formed a chain implicitly; each such band
 * becomes one workflow whose steps are the rules ordered by level.
 *
 * Runs only when workflows are empty, so it is safe on every restart and
 * never overwrites configuration made through the new UI. The legacy rows are
 * left in place rather than deleted, so the migration is reversible.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApprovalRuleMigrator implements ApplicationRunner {

    private final ApprovalRuleRepository legacyRuleRepository;
    private final ApprovalWorkflowRepository workflowRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (workflowRepository.count() > 0) {
            log.debug("Approval workflows already exist — skipping legacy rule migration");
            return;
        }

        List<ApprovalRule> legacyRules = legacyRuleRepository.findAll();
        if (legacyRules.isEmpty()) {
            log.debug("No legacy approval rules to migrate");
            return;
        }

        log.info("Migrating {} legacy approval rule(s) into workflows", legacyRules.size());

        // Group by amount band — that is what previously implied a chain.
        Map<String, List<ApprovalRule>> byBand = new LinkedHashMap<>();
        for (ApprovalRule rule : legacyRules) {
            String key = bandKey(rule.getMinAmount(), rule.getMaxAmount());
            byBand.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(rule);
        }

        int migrated = 0;
        for (Map.Entry<String, List<ApprovalRule>> entry : byBand.entrySet()) {
            List<ApprovalRule> bandRules = entry.getValue();
            bandRules.sort(Comparator.comparing(ApprovalRule::getApprovalLevel));

            ApprovalRule first = bandRules.get(0);
            String name = "Migrated: " + describeBand(first.getMinAmount(), first.getMaxAmount());

            if (workflowRepository.existsByNameIgnoreCase(name)) {
                log.warn("Skipping migration for band {} — a workflow named '{}' already exists",
                        entry.getKey(), name);
                continue;
            }

            ApprovalWorkflow workflow = ApprovalWorkflow.builder()
                    .name(name)
                    .description("Automatically migrated from the previous approval rules configuration.")
                    .minAmount(first.getMinAmount())
                    .maxAmount(first.getMaxAmount())
                    .priority(0)
                    // Preserve the original enabled state: if every rule in the
                    // band was inactive, the workflow should be inactive too.
                    .active(bandRules.stream().anyMatch(ApprovalRule::isActive))
                    .build();

            int order = 1;
            for (ApprovalRule rule : bandRules) {
                workflow.addStep(ApprovalWorkflowStep.builder()
                        .stepOrder(order++)
                        .name(rule.getName())
                        .approverType(ApproverType.SPECIFIC_USER)
                        .approver(rule.getApprover())
                        .build());
            }

            workflowRepository.save(workflow);
            migrated++;
            log.info("Migrated band {} into workflow '{}' with {} step(s)",
                    entry.getKey(), name, workflow.getSteps().size());
        }

        log.info("Legacy approval rule migration complete: {} workflow(s) created. "
                + "The legacy approval_rules table was left untouched.", migrated);
    }

    private String bandKey(BigDecimal min, BigDecimal max) {
        return min.toPlainString() + "-" + (max == null ? "UNLIMITED" : max.toPlainString());
    }

    private String describeBand(BigDecimal min, BigDecimal max) {
        return max == null
                ? min.toPlainString() + " and above"
                : min.toPlainString() + " to " + max.toPlainString();
    }
}
