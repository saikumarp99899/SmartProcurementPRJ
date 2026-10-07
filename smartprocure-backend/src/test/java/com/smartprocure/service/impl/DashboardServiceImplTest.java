package com.smartprocure.service.impl;

import com.smartprocure.dto.response.AdminDashboardResponse;
import com.smartprocure.dto.response.BuyerDashboardResponse;
import com.smartprocure.dto.response.ApproverDashboardResponse;
import com.smartprocure.entity.Approval.ApprovalStatus;
import com.smartprocure.entity.PurchaseOrder.PurchaseOrderStatus;
import com.smartprocure.entity.Requisition.RequisitionStatus;
import com.smartprocure.entity.Role;
import com.smartprocure.entity.Role.RoleName;
import com.smartprocure.entity.User;
import com.smartprocure.entity.Vendor.VendorStatus;
import com.smartprocure.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DashboardServiceImpl.
 * Verifies that dashboard aggregation correctly calls repositories
 * and returns properly assembled responses.
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private VendorRepository vendorRepository;
    @Mock private RequisitionRepository requisitionRepository;
    @Mock private ApprovalRepository approvalRepository;
    @Mock private PurchaseOrderRepository purchaseOrderRepository;

    @InjectMocks
    private DashboardServiceImpl dashboardService;

    @Test
    @DisplayName("Admin dashboard should aggregate all metrics")
    void getAdminDashboard_ReturnsAllMetrics() {
        // Arrange
        when(userRepository.count()).thenReturn(25L);
        when(vendorRepository.countByStatus(VendorStatus.ACTIVE)).thenReturn(10L);
        when(requisitionRepository.count()).thenReturn(50L);
        when(approvalRepository.countByStatus(ApprovalStatus.PENDING)).thenReturn(5L);
        when(purchaseOrderRepository.count()).thenReturn(30L);
        when(purchaseOrderRepository.calculateTotalSpend(PurchaseOrderStatus.CANCELLED))
                .thenReturn(new BigDecimal("500000.00"));

        // Act
        AdminDashboardResponse result = dashboardService.getAdminDashboard();

        // Assert
        assertThat(result.getTotalUsers()).isEqualTo(25L);
        assertThat(result.getActiveVendors()).isEqualTo(10L);
        assertThat(result.getTotalRequisitions()).isEqualTo(50L);
        assertThat(result.getPendingApprovals()).isEqualTo(5L);
        assertThat(result.getTotalPurchaseOrders()).isEqualTo(30L);
        assertThat(result.getTotalProcurementSpend()).isEqualByComparingTo("500000.00");
    }

    @Test
    @DisplayName("Buyer dashboard should show user-specific metrics")
    void getBuyerDashboard_ReturnsUserSpecificMetrics() {
        // Arrange
        Long userId = 1L;
        when(requisitionRepository.countByRequesterId(userId)).thenReturn(12L);
        when(requisitionRepository.countByRequesterIdAndStatus(userId, RequisitionStatus.DRAFT)).thenReturn(3L);
        when(requisitionRepository.countByRequesterIdAndStatus(userId, RequisitionStatus.SUBMITTED)).thenReturn(2L);
        when(requisitionRepository.countByRequesterIdAndStatus(userId, RequisitionStatus.APPROVED)).thenReturn(5L);
        when(requisitionRepository.countByRequesterIdAndStatus(userId, RequisitionStatus.REJECTED)).thenReturn(2L);
        when(purchaseOrderRepository.countByStatus(PurchaseOrderStatus.DRAFT)).thenReturn(4L);
        when(purchaseOrderRepository.calculateTotalSpendByUser(userId, PurchaseOrderStatus.CANCELLED))
                .thenReturn(new BigDecimal("150000.00"));

        // Act
        BuyerDashboardResponse result = dashboardService.getBuyerDashboard(userId);

        // Assert
        assertThat(result.getTotalMyRequisitions()).isEqualTo(12L);
        assertThat(result.getDraftRequisitions()).isEqualTo(3L);
        assertThat(result.getPendingRequisitions()).isEqualTo(2L);
        assertThat(result.getApprovedRequisitions()).isEqualTo(5L);
        assertThat(result.getRejectedRequisitions()).isEqualTo(2L);
        assertThat(result.getMyTotalSpend()).isEqualByComparingTo("150000.00");
    }

    @Test
    @DisplayName("Approver dashboard should count only approvals this approver can action")
    void getApproverDashboard_ReturnsApprovalMetrics() {
        // Arrange
        Long userId = 2L;

        // The count is role-aware, since a step may target a role rather than
        // an individual, so the approver's roles must be resolved first.
        Role approverRole = new Role();
        approverRole.setId(1L);
        approverRole.setName(RoleName.APPROVER);
        User approver = User.builder().id(userId).email("approver@test.com").build();
        approver.getRoles().add(approverRole);
        when(userRepository.findById(userId)).thenReturn(Optional.of(approver));

        // Assigned/role-matched/unassigned levels only — not every pending approval.
        when(approvalRepository.countActionableByApprover(
                eq(ApprovalStatus.PENDING), eq(userId), anyCollection())).thenReturn(8L);
        when(approvalRepository.countByApproverIdAndStatus(userId, ApprovalStatus.APPROVED)).thenReturn(15L);
        when(approvalRepository.countByApproverIdAndStatus(userId, ApprovalStatus.REJECTED)).thenReturn(3L);

        // Act
        ApproverDashboardResponse result = dashboardService.getApproverDashboard(userId);

        // Assert
        assertThat(result.getPendingApprovals()).isEqualTo(8L);
        assertThat(result.getApprovedByMe()).isEqualTo(15L);
        assertThat(result.getRejectedByMe()).isEqualTo(3L);
    }
}
