package com.smartprocure.service.impl;

import com.smartprocure.entity.*;
import com.smartprocure.entity.Approval.ApprovalStatus;
import com.smartprocure.entity.ApprovalWorkflowStep.ApproverType;
import com.smartprocure.entity.Role.RoleName;
import com.smartprocure.exception.BusinessRuleException;
import com.smartprocure.repository.ApprovalRepository;
import com.smartprocure.repository.ApprovalWorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Covers workflow selection precedence and step resolution — the part of the
 * approval process with the most branching, and the part most likely to
 * silently route a requisition to the wrong person.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApprovalChainServiceImplTest {

    @Mock private ApprovalWorkflowRepository workflowRepository;
    @Mock private ApprovalRepository approvalRepository;

    @InjectMocks
    private ApprovalChainServiceImpl chainService;

    private User buyer;
    private User manager;
    private User financeHead;
    private Department itDept;

    private static User user(Long id, String email, RoleName roleName) {
        Role role = new Role();
        role.setId(id);
        role.setName(roleName);
        User u = User.builder()
                .id(id).firstName("F" + id).lastName("L" + id)
                .email(email).status(User.UserStatus.ACTIVE).build();
        u.getRoles().add(role);
        return u;
    }

    @BeforeEach
    void setUp() {
        buyer = user(1L, "buyer@test.com", RoleName.BUYER);
        manager = user(2L, "manager@test.com", RoleName.APPROVER);
        financeHead = user(3L, "finance@test.com", RoleName.APPROVER);

        itDept = new Department();
        itDept.setId(10L);
        itDept.setName("Information Technology");
    }

    private Requisition requisition(String amount, Department department, User requester) {
        return Requisition.builder()
                .id(100L)
                .requisitionNumber("REQ-2026-0001")
                .totalAmount(new BigDecimal(amount))
                .department(department)
                .requester(requester)
                .build();
    }

    private ApprovalWorkflow workflow(Long id, String name, String min, String max,
                                      Department dept, int priority) {
        return ApprovalWorkflow.builder()
                .id(id).name(name)
                .minAmount(new BigDecimal(min))
                .maxAmount(max == null ? null : new BigDecimal(max))
                .department(dept)
                .priority(priority)
                .active(true)
                .build();
    }

    private ApprovalWorkflowStep step(int order, String name, ApproverType type,
                                      User approver, RoleName role) {
        return ApprovalWorkflowStep.builder()
                .stepOrder(order).name(name).approverType(type)
                .approver(approver).approverRole(role)
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<Approval> captureSavedChain() {
        ArgumentCaptor<List<Approval>> captor = ArgumentCaptor.forClass(List.class);
        verify(approvalRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    // ------------------------------------------------------------------
    // Selection precedence
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Higher priority workflow wins when several match")
    void selectWorkflow_HigherPriorityWins() {
        ApprovalWorkflow low = workflow(1L, "Standard", "0", null, null, 0);
        ApprovalWorkflow high = workflow(2L, "Override", "0", null, null, 5);
        when(workflowRepository.findCandidates(any(), any())).thenReturn(List.of(low, high));

        Optional<ApprovalWorkflow> selected =
                chainService.selectWorkflow(new BigDecimal("1000"), null);

        assertThat(selected).isPresent();
        assertThat(selected.get().getName()).isEqualTo("Override");
    }

    @Test
    @DisplayName("At equal priority, a department-specific workflow beats a global one")
    void selectWorkflow_DepartmentSpecificBeatsGlobal() {
        ApprovalWorkflow global = workflow(1L, "Global", "0", null, null, 0);
        ApprovalWorkflow deptSpecific = workflow(2L, "IT Only", "0", null, itDept, 0);
        when(workflowRepository.findCandidates(any(), any())).thenReturn(List.of(global, deptSpecific));

        Optional<ApprovalWorkflow> selected =
                chainService.selectWorkflow(new BigDecimal("1000"), 10L);

        assertThat(selected).isPresent();
        assertThat(selected.get().getName()).isEqualTo("IT Only");
    }

    @Test
    @DisplayName("Priority outranks department specificity")
    void selectWorkflow_PriorityOutranksSpecificity() {
        ApprovalWorkflow deptSpecific = workflow(1L, "IT Only", "0", null, itDept, 0);
        ApprovalWorkflow globalHighPriority = workflow(2L, "Global Override", "0", null, null, 9);
        when(workflowRepository.findCandidates(any(), any()))
                .thenReturn(List.of(deptSpecific, globalHighPriority));

        Optional<ApprovalWorkflow> selected =
                chainService.selectWorkflow(new BigDecimal("1000"), 10L);

        assertThat(selected.get().getName()).isEqualTo("Global Override");
    }

    @Test
    @DisplayName("Fully tied workflows resolve deterministically by lowest id")
    void selectWorkflow_TieBrokenByLowestId() {
        ApprovalWorkflow older = workflow(1L, "Older", "0", null, null, 0);
        ApprovalWorkflow newer = workflow(2L, "Newer", "0", null, null, 0);
        when(workflowRepository.findCandidates(any(), any())).thenReturn(List.of(newer, older));

        Optional<ApprovalWorkflow> selected =
                chainService.selectWorkflow(new BigDecimal("1000"), null);

        assertThat(selected.get().getName()).isEqualTo("Older");
    }

    @Test
    @DisplayName("No matching workflow returns empty")
    void selectWorkflow_NoMatch_ReturnsEmpty() {
        when(workflowRepository.findCandidates(any(), any())).thenReturn(List.of());

        assertThat(chainService.selectWorkflow(new BigDecimal("1000"), null)).isEmpty();
    }

    // ------------------------------------------------------------------
    // Chain building
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Builds a sequential chain with only the first step actionable")
    void buildChain_FirstStepPendingRestWaiting() {
        ApprovalWorkflow wf = workflow(1L, "Two Step", "0", null, null, 0);
        wf.addStep(step(1, "Manager", ApproverType.SPECIFIC_USER, manager, null));
        wf.addStep(step(2, "Finance", ApproverType.SPECIFIC_USER, financeHead, null));
        when(workflowRepository.findCandidates(any(), any())).thenReturn(List.of(wf));

        chainService.buildChain(requisition("120000", itDept, buyer));

        List<Approval> chain = captureSavedChain();
        assertThat(chain).hasSize(2);
        assertThat(chain.get(0).getStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(chain.get(0).getAssignedApprover()).isEqualTo(manager);
        assertThat(chain.get(1).getStatus()).isEqualTo(ApprovalStatus.WAITING);
        assertThat(chain.get(1).getAssignedApprover()).isEqualTo(financeHead);
    }

    @Test
    @DisplayName("Snapshots the workflow and step names onto each approval")
    void buildChain_SnapshotsNames() {
        ApprovalWorkflow wf = workflow(1L, "High Value", "0", null, null, 0);
        wf.addStep(step(1, "Finance sign-off", ApproverType.SPECIFIC_USER, financeHead, null));
        when(workflowRepository.findCandidates(any(), any())).thenReturn(List.of(wf));

        chainService.buildChain(requisition("120000", null, buyer));

        Approval only = captureSavedChain().get(0);
        assertThat(only.getWorkflowName()).isEqualTo("High Value");
        assertThat(only.getRuleName()).isEqualTo("Finance sign-off");
    }

    @Test
    @DisplayName("A ROLE step is left unassigned to an individual and records the role")
    void buildChain_RoleStep_RecordsRole() {
        ApprovalWorkflow wf = workflow(1L, "Role Based", "0", null, null, 0);
        wf.addStep(step(1, "Any approver", ApproverType.ROLE, null, RoleName.APPROVER));
        when(workflowRepository.findCandidates(any(), any())).thenReturn(List.of(wf));

        chainService.buildChain(requisition("5000", null, buyer));

        Approval only = captureSavedChain().get(0);
        assertThat(only.getAssignedApprover()).isNull();
        assertThat(only.getAssignedRole()).isEqualTo(RoleName.APPROVER);
        assertThat(only.getStatus()).isEqualTo(ApprovalStatus.PENDING);
    }

    @Test
    @DisplayName("A REQUESTER_MANAGER step resolves to the requester's manager")
    void buildChain_RequesterManagerStep_ResolvesManager() {
        buyer.setManager(manager);

        ApprovalWorkflow wf = workflow(1L, "Manager First", "0", null, null, 0);
        wf.addStep(step(1, "Reporting manager", ApproverType.REQUESTER_MANAGER, null, null));
        when(workflowRepository.findCandidates(any(), any())).thenReturn(List.of(wf));

        chainService.buildChain(requisition("5000", null, buyer));

        assertThat(captureSavedChain().get(0).getAssignedApprover()).isEqualTo(manager);
    }

    @Test
    @DisplayName("A REQUESTER_MANAGER step fails clearly when no manager is set")
    void buildChain_RequesterManagerStep_NoManager_Throws() {
        ApprovalWorkflow wf = workflow(1L, "Manager First", "0", null, null, 0);
        wf.addStep(step(1, "Reporting manager", ApproverType.REQUESTER_MANAGER, null, null));
        when(workflowRepository.findCandidates(any(), any())).thenReturn(List.of(wf));

        assertThatThrownBy(() -> chainService.buildChain(requisition("5000", null, buyer)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no manager is set");

        verify(approvalRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("A step resolving to the requester is skipped, and the next becomes actionable")
    void buildChain_SelfApprovalStep_Skipped() {
        // The requester also happens to be the named approver for step 1.
        User buyerWhoApproves = user(1L, "buyer@test.com", RoleName.APPROVER);

        ApprovalWorkflow wf = workflow(1L, "Two Step", "0", null, null, 0);
        wf.addStep(step(1, "Self", ApproverType.SPECIFIC_USER, buyerWhoApproves, null));
        wf.addStep(step(2, "Finance", ApproverType.SPECIFIC_USER, financeHead, null));
        when(workflowRepository.findCandidates(any(), any())).thenReturn(List.of(wf));

        chainService.buildChain(requisition("120000", null, buyerWhoApproves));

        List<Approval> chain = captureSavedChain();
        assertThat(chain.get(0).getStatus()).isEqualTo(ApprovalStatus.SKIPPED);
        assertThat(chain.get(0).getComments()).contains("cannot approve their own");
        // Step 2 is promoted so the chain still moves.
        assertThat(chain.get(1).getStatus()).isEqualTo(ApprovalStatus.PENDING);
    }

    @Test
    @DisplayName("A workflow whose every step is the requester is rejected rather than saved dead")
    void buildChain_AllStepsSelfApproval_Throws() {
        User buyerWhoApproves = user(1L, "buyer@test.com", RoleName.APPROVER);

        ApprovalWorkflow wf = workflow(1L, "Self Only", "0", null, null, 0);
        wf.addStep(step(1, "Self", ApproverType.SPECIFIC_USER, buyerWhoApproves, null));
        when(workflowRepository.findCandidates(any(), any())).thenReturn(List.of(wf));

        assertThatThrownBy(() -> chainService.buildChain(requisition("5000", null, buyerWhoApproves)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no step that someone other than you can approve");

        verify(approvalRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("With no matching workflow, a single open approval is created as a fallback")
    void buildChain_NoWorkflow_CreatesOpenFallback() {
        when(workflowRepository.findCandidates(any(), any())).thenReturn(List.of());
        when(approvalRepository.save(any(Approval.class))).thenAnswer(inv -> inv.getArgument(0));

        chainService.buildChain(requisition("5000", null, buyer));

        ArgumentCaptor<Approval> captor = ArgumentCaptor.forClass(Approval.class);
        verify(approvalRepository).save(captor.capture());

        Approval fallback = captor.getValue();
        assertThat(fallback.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(fallback.getAssignedApprover()).isNull();
        assertThat(fallback.getAssignedRole()).isNull();
        verify(approvalRepository, never()).saveAll(any());
    }
}
