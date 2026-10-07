package com.smartprocure.service.impl;

import com.smartprocure.audit.AuditLogService;
import com.smartprocure.dto.request.ApprovalActionRequest;
import com.smartprocure.dto.response.ApprovalResponse;
import com.smartprocure.entity.Approval;
import com.smartprocure.entity.Approval.ApprovalStatus;
import com.smartprocure.entity.Requisition;
import com.smartprocure.entity.Requisition.RequisitionStatus;
import com.smartprocure.entity.Role;
import com.smartprocure.entity.Role.RoleName;
import com.smartprocure.entity.User;
import com.smartprocure.exception.BadRequestException;
import com.smartprocure.exception.BusinessRuleException;
import com.smartprocure.exception.ForbiddenException;
import com.smartprocure.mapper.ApprovalMapper;
import com.smartprocure.repository.ApprovalRepository;
import com.smartprocure.repository.RequisitionRepository;
import com.smartprocure.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Covers the multi-level approval chain: sequential advancement,
 * assignment enforcement, and rejection cancelling downstream levels.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApprovalServiceImplTest {

    @Mock private ApprovalRepository approvalRepository;
    @Mock private RequisitionRepository requisitionRepository;
    @Mock private UserRepository userRepository;
    @Mock private ApprovalMapper approvalMapper;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private ApprovalServiceImpl approvalService;

    private User manager;
    private User financeHead;
    private User admin;
    private Requisition requisition;

    private static User user(Long id, String email, RoleName roleName) {
        Role role = new Role();
        role.setId(id);
        role.setName(roleName);
        User u = User.builder()
                .id(id)
                .firstName("First" + id)
                .lastName("Last" + id)
                .email(email)
                .status(User.UserStatus.ACTIVE)
                .build();
        u.getRoles().add(role);
        return u;
    }

    @BeforeEach
    void setUp() {
        manager = user(1L, "manager@test.com", RoleName.APPROVER);
        financeHead = user(2L, "finance@test.com", RoleName.APPROVER);
        admin = user(3L, "admin@test.com", RoleName.ADMIN);

        requisition = Requisition.builder()
                .id(100L)
                .requisitionNumber("REQ-2026-0001")
                .totalAmount(new BigDecimal("120000.00"))
                .status(RequisitionStatus.SUBMITTED)
                .requester(user(9L, "buyer@test.com", RoleName.BUYER))
                .build();

        when(approvalMapper.toResponse(any(Approval.class)))
                .thenAnswer(inv -> {
                    Approval a = inv.getArgument(0);
                    return ApprovalResponse.builder()
                            .id(a.getId())
                            .status(a.getStatus().name())
                            .approvalLevel(a.getApprovalLevel())
                            .build();
                });
        when(approvalRepository.save(any(Approval.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Approval approval(Long id, int level, ApprovalStatus status, User assignedApprover) {
        return Approval.builder()
                .id(id)
                .requisition(requisition)
                .approvalLevel(level)
                .status(status)
                .assignedApprover(assignedApprover)
                .build();
    }

    private ApprovalActionRequest action(String comments) {
        ApprovalActionRequest req = new ApprovalActionRequest();
        req.setComments(comments);
        return req;
    }

    // ------------------------------------------------------------------
    // Sequential advancement
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Approving a non-final level promotes the next level and leaves requisition SUBMITTED")
    void approve_IntermediateLevel_PromotesNextLevel() {
        Approval level1 = approval(11L, 1, ApprovalStatus.PENDING, manager);
        Approval level2 = approval(12L, 2, ApprovalStatus.WAITING, financeHead);

        when(approvalRepository.findById(11L)).thenReturn(Optional.of(level1));
        when(userRepository.findById(1L)).thenReturn(Optional.of(manager));
        when(approvalRepository
                .findFirstByRequisitionIdAndStatusAndApprovalLevelGreaterThanOrderByApprovalLevelAsc(
                        100L, ApprovalStatus.WAITING, 1))
                .thenReturn(Optional.of(level2));

        approvalService.approveRequisition(11L, action("Looks fine"), 1L);

        assertThat(level1.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(level1.getApprover()).isEqualTo(manager);
        // Next level becomes actionable...
        assertThat(level2.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        // ...and the requisition is NOT yet approved.
        assertThat(requisition.getStatus()).isEqualTo(RequisitionStatus.SUBMITTED);
        verify(requisitionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Approving the final level marks the requisition APPROVED")
    void approve_FinalLevel_ApprovesRequisition() {
        Approval level2 = approval(12L, 2, ApprovalStatus.PENDING, financeHead);

        when(approvalRepository.findById(12L)).thenReturn(Optional.of(level2));
        when(userRepository.findById(2L)).thenReturn(Optional.of(financeHead));
        when(approvalRepository
                .findFirstByRequisitionIdAndStatusAndApprovalLevelGreaterThanOrderByApprovalLevelAsc(
                        anyLong(), any(), anyInt()))
                .thenReturn(Optional.empty());

        approvalService.approveRequisition(12L, action("Approved"), 2L);

        assertThat(level2.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(requisition.getStatus()).isEqualTo(RequisitionStatus.APPROVED);
        verify(requisitionRepository).save(requisition);
    }

    // ------------------------------------------------------------------
    // Assignment enforcement
    // ------------------------------------------------------------------

    @Test
    @DisplayName("An approver cannot action a level assigned to someone else")
    void approve_NotAssignedApprover_ThrowsForbidden() {
        Approval level1 = approval(11L, 1, ApprovalStatus.PENDING, manager);

        when(approvalRepository.findById(11L)).thenReturn(Optional.of(level1));
        when(userRepository.findById(2L)).thenReturn(Optional.of(financeHead));

        assertThatThrownBy(() -> approvalService.approveRequisition(11L, action("me too"), 2L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("manager@test.com");

        assertThat(level1.getStatus()).isEqualTo(ApprovalStatus.PENDING);
    }

    @Test
    @DisplayName("An admin may action a level assigned to another approver")
    void approve_AdminOverride_Allowed() {
        Approval level1 = approval(11L, 1, ApprovalStatus.PENDING, manager);

        when(approvalRepository.findById(11L)).thenReturn(Optional.of(level1));
        when(userRepository.findById(3L)).thenReturn(Optional.of(admin));
        when(approvalRepository
                .findFirstByRequisitionIdAndStatusAndApprovalLevelGreaterThanOrderByApprovalLevelAsc(
                        anyLong(), any(), anyInt()))
                .thenReturn(Optional.empty());

        approvalService.approveRequisition(11L, action("admin override"), 3L);

        assertThat(level1.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(level1.getApprover()).isEqualTo(admin);
    }

    @Test
    @DisplayName("An unassigned level is open to any approver")
    void approve_UnassignedLevel_AnyApproverAllowed() {
        Approval level1 = approval(11L, 1, ApprovalStatus.PENDING, null);

        when(approvalRepository.findById(11L)).thenReturn(Optional.of(level1));
        when(userRepository.findById(2L)).thenReturn(Optional.of(financeHead));
        when(approvalRepository
                .findFirstByRequisitionIdAndStatusAndApprovalLevelGreaterThanOrderByApprovalLevelAsc(
                        anyLong(), any(), anyInt()))
                .thenReturn(Optional.empty());

        approvalService.approveRequisition(11L, action("ok"), 2L);

        assertThat(level1.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
    }

    @Test
    @DisplayName("A WAITING level cannot be actioned out of turn")
    void approve_WaitingLevel_ThrowsBusinessRule() {
        Approval level2 = approval(12L, 2, ApprovalStatus.WAITING, financeHead);

        when(approvalRepository.findById(12L)).thenReturn(Optional.of(level2));
        when(userRepository.findById(2L)).thenReturn(Optional.of(financeHead));

        assertThatThrownBy(() -> approvalService.approveRequisition(12L, action("early"), 2L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not yet actionable");

        assertThat(level2.getStatus()).isEqualTo(ApprovalStatus.WAITING);
    }

    @Test
    @DisplayName("An already-approved level cannot be actioned again")
    void approve_AlreadyApproved_ThrowsBusinessRule() {
        Approval level1 = approval(11L, 1, ApprovalStatus.APPROVED, manager);

        when(approvalRepository.findById(11L)).thenReturn(Optional.of(level1));
        when(userRepository.findById(1L)).thenReturn(Optional.of(manager));

        assertThatThrownBy(() -> approvalService.approveRequisition(11L, action("again"), 1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already APPROVED");
    }

    // ------------------------------------------------------------------
    // Rejection
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Rejecting cancels downstream levels and rejects the requisition")
    void reject_SkipsRemainingLevels() {
        Approval level1 = approval(11L, 1, ApprovalStatus.PENDING, manager);
        Approval level2 = approval(12L, 2, ApprovalStatus.WAITING, financeHead);
        Approval level3 = approval(13L, 3, ApprovalStatus.WAITING, admin);

        when(approvalRepository.findById(11L)).thenReturn(Optional.of(level1));
        when(userRepository.findById(1L)).thenReturn(Optional.of(manager));
        when(approvalRepository.findByRequisitionIdAndStatusIn(100L, List.of(ApprovalStatus.WAITING)))
                .thenReturn(List.of(level2, level3));

        approvalService.rejectRequisition(11L, action("Over budget"), 1L);

        assertThat(level1.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(level2.getStatus()).isEqualTo(ApprovalStatus.SKIPPED);
        assertThat(level3.getStatus()).isEqualTo(ApprovalStatus.SKIPPED);
        assertThat(requisition.getStatus()).isEqualTo(RequisitionStatus.REJECTED);
    }

    @Test
    @DisplayName("Rejection requires comments")
    void reject_WithoutComments_ThrowsBadRequest() {
        Approval level1 = approval(11L, 1, ApprovalStatus.PENDING, manager);

        when(approvalRepository.findById(11L)).thenReturn(Optional.of(level1));
        when(userRepository.findById(1L)).thenReturn(Optional.of(manager));

        assertThatThrownBy(() -> approvalService.rejectRequisition(11L, action("   "), 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Comments are required");

        assertThat(level1.getStatus()).isEqualTo(ApprovalStatus.PENDING);
    }

    @Test
    @DisplayName("An approver cannot reject a level assigned to someone else")
    void reject_NotAssignedApprover_ThrowsForbidden() {
        Approval level1 = approval(11L, 1, ApprovalStatus.PENDING, manager);

        when(approvalRepository.findById(11L)).thenReturn(Optional.of(level1));
        when(userRepository.findById(2L)).thenReturn(Optional.of(financeHead));

        assertThatThrownBy(() -> approvalService.rejectRequisition(11L, action("no"), 2L))
                .isInstanceOf(ForbiddenException.class);
    }
}
