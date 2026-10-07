package com.smartprocure.service.impl;

import com.smartprocure.dto.request.ApprovalWorkflowRequest;
import com.smartprocure.dto.request.ApprovalWorkflowRequest.StepRequest;
import com.smartprocure.dto.response.ApprovalWorkflowResponse;
import com.smartprocure.entity.ApprovalWorkflow;
import com.smartprocure.entity.Role;
import com.smartprocure.entity.Role.RoleName;
import com.smartprocure.entity.User;
import com.smartprocure.exception.BadRequestException;
import com.smartprocure.exception.BusinessRuleException;
import com.smartprocure.exception.DuplicateResourceException;
import com.smartprocure.repository.ApprovalWorkflowRepository;
import com.smartprocure.repository.DepartmentRepository;
import com.smartprocure.repository.UserRepository;
import com.smartprocure.service.ApprovalChainService;
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
import static org.mockito.Mockito.*;

/**
 * Covers workflow validation: uniqueness, amount bands, step ordering and
 * approver authority.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApprovalWorkflowServiceImplTest {

    @Mock private ApprovalWorkflowRepository workflowRepository;
    @Mock private UserRepository userRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private ApprovalChainService approvalChainService;

    @InjectMocks
    private ApprovalWorkflowServiceImpl workflowService;

    private User approver;
    private User buyer;
    private User inactiveApprover;

    private static User user(Long id, String email, RoleName roleName, User.UserStatus status) {
        Role role = new Role();
        role.setId(id);
        role.setName(roleName);
        User u = User.builder()
                .id(id).firstName("F").lastName("L").email(email).status(status).build();
        u.getRoles().add(role);
        return u;
    }

    @BeforeEach
    void setUp() {
        approver = user(1L, "approver@test.com", RoleName.APPROVER, User.UserStatus.ACTIVE);
        buyer = user(2L, "buyer@test.com", RoleName.BUYER, User.UserStatus.ACTIVE);
        inactiveApprover = user(3L, "old@test.com", RoleName.APPROVER, User.UserStatus.INACTIVE);

        when(workflowRepository.save(any(ApprovalWorkflow.class))).thenAnswer(inv -> {
            ApprovalWorkflow w = inv.getArgument(0);
            if (w.getId() == null) w.setId(50L);
            return w;
        });
        when(workflowRepository.existsByNameIgnoreCase(any())).thenReturn(false);
        when(workflowRepository.findByNameIgnoreCase(any())).thenReturn(Optional.empty());
    }

    private StepRequest stepRequest(String name, String type, Long approverId, String role) {
        StepRequest s = new StepRequest();
        s.setName(name);
        s.setApproverType(type);
        s.setApproverId(approverId);
        s.setApproverRole(role);
        return s;
    }

    private ApprovalWorkflowRequest request(String min, String max, List<StepRequest> steps) {
        ApprovalWorkflowRequest r = new ApprovalWorkflowRequest();
        r.setName("Standard Approval");
        r.setMinAmount(new BigDecimal(min));
        r.setMaxAmount(max == null ? null : new BigDecimal(max));
        r.setSteps(steps);
        return r;
    }

    @Test
    @DisplayName("Creates a workflow and numbers its steps sequentially")
    void createWorkflow_NumbersStepsInOrder() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(approver));

        ApprovalWorkflowResponse result = workflowService.createWorkflow(request("0", "100000", List.of(
                stepRequest("Manager", "SPECIFIC_USER", 1L, null),
                stepRequest("Any approver", "ROLE", null, "APPROVER"),
                stepRequest("Reporting manager", "REQUESTER_MANAGER", null, null)
        )));

        assertThat(result.getSteps()).hasSize(3);
        assertThat(result.getSteps()).extracting(ApprovalWorkflowResponse.StepResponse::getStepOrder)
                .containsExactly(1, 2, 3);
        assertThat(result.getSteps().get(1).getApproverLabel()).isEqualTo("Any APPROVER");
        assertThat(result.getSteps().get(2).getApproverLabel()).isEqualTo("Requester's manager");
    }

    @Test
    @DisplayName("A null maximum amount is accepted as an unlimited top band")
    void createWorkflow_NullMaxAmount_Allowed() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(approver));

        ApprovalWorkflowResponse result = workflowService.createWorkflow(request("500000", null,
                List.of(stepRequest("Manager", "SPECIFIC_USER", 1L, null))));

        assertThat(result.getMaxAmount()).isNull();
    }

    @Test
    @DisplayName("Rejects a duplicate workflow name")
    void createWorkflow_DuplicateName_Throws() {
        when(workflowRepository.existsByNameIgnoreCase("Standard Approval")).thenReturn(true);

        assertThatThrownBy(() -> workflowService.createWorkflow(request("0", "1000",
                List.of(stepRequest("Manager", "SPECIFIC_USER", 1L, null)))))
                .isInstanceOf(DuplicateResourceException.class);

        verify(workflowRepository, never()).save(any());
    }

    @Test
    @DisplayName("Rejects a band whose maximum is below its minimum")
    void createWorkflow_MaxBelowMin_Throws() {
        assertThatThrownBy(() -> workflowService.createWorkflow(request("50000", "1000",
                List.of(stepRequest("Manager", "SPECIFIC_USER", 1L, null)))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Maximum amount must be greater");
    }

    @Test
    @DisplayName("Rejects a named approver who lacks approval authority")
    void createWorkflow_ApproverWithoutAuthority_Throws() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(buyer));

        assertThatThrownBy(() -> workflowService.createWorkflow(request("0", "1000",
                List.of(stepRequest("Buyer", "SPECIFIC_USER", 2L, null)))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("does not have the APPROVER or ADMIN role");
    }

    @Test
    @DisplayName("Rejects an inactive named approver")
    void createWorkflow_InactiveApprover_Throws() {
        when(userRepository.findById(3L)).thenReturn(Optional.of(inactiveApprover));

        assertThatThrownBy(() -> workflowService.createWorkflow(request("0", "1000",
                List.of(stepRequest("Old", "SPECIFIC_USER", 3L, null)))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("is inactive");
    }

    @Test
    @DisplayName("Rejects a SPECIFIC_USER step with no approver selected")
    void createWorkflow_SpecificUserWithoutApprover_Throws() {
        assertThatThrownBy(() -> workflowService.createWorkflow(request("0", "1000",
                List.of(stepRequest("Nobody", "SPECIFIC_USER", null, null)))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("needs an approver");
    }

    @Test
    @DisplayName("Rejects a ROLE step with no role selected")
    void createWorkflow_RoleStepWithoutRole_Throws() {
        assertThatThrownBy(() -> workflowService.createWorkflow(request("0", "1000",
                List.of(stepRequest("No role", "ROLE", null, null)))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("needs a role");
    }

    @Test
    @DisplayName("Rejects BUYER as an approver role, which would let requesters approve")
    void createWorkflow_BuyerRoleStep_Throws() {
        assertThatThrownBy(() -> workflowService.createWorkflow(request("0", "1000",
                List.of(stepRequest("Buyers", "ROLE", null, "BUYER")))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Only the APPROVER and ADMIN roles");
    }

    @Test
    @DisplayName("Rejects an unrecognised approver type")
    void createWorkflow_InvalidApproverType_Throws() {
        assertThatThrownBy(() -> workflowService.createWorkflow(request("0", "1000",
                List.of(stepRequest("Odd", "SOMETHING_ELSE", null, null)))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid approver type");
    }

    @Test
    @DisplayName("Preview returns null when no workflow matches, signalling the fallback")
    void previewWorkflow_NoMatch_ReturnsNull() {
        when(approvalChainService.selectWorkflow(any(), any())).thenReturn(Optional.empty());

        assertThat(workflowService.previewWorkflow(new BigDecimal("100"), null)).isNull();
    }

    @Test
    @DisplayName("Preview rejects a negative amount")
    void previewWorkflow_NegativeAmount_Throws() {
        assertThatThrownBy(() -> workflowService.previewWorkflow(new BigDecimal("-1"), null))
                .isInstanceOf(BadRequestException.class);
    }
}
