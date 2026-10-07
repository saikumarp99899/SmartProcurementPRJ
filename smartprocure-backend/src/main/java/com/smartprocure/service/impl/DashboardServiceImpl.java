package com.smartprocure.service.impl;

import com.smartprocure.dto.response.AdminDashboardResponse;
import com.smartprocure.dto.response.ApproverDashboardResponse;
import com.smartprocure.dto.response.BuyerDashboardResponse;
import com.smartprocure.entity.Approval.ApprovalStatus;
import com.smartprocure.entity.PurchaseOrder.PurchaseOrderStatus;
import com.smartprocure.entity.Requisition.RequisitionStatus;
import com.smartprocure.entity.Role;
import com.smartprocure.entity.Role.RoleName;
import com.smartprocure.entity.Vendor.VendorStatus;
import com.smartprocure.repository.*;
import com.smartprocure.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final UserRepository userRepository;
    private final VendorRepository vendorRepository;
    private final RequisitionRepository requisitionRepository;
    private final ApprovalRepository approvalRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;

    @Override
    @Transactional(readOnly = true)
    public AdminDashboardResponse getAdminDashboard() {
        return AdminDashboardResponse.builder()
                .totalUsers(userRepository.count())
                .activeVendors(vendorRepository.countByStatus(VendorStatus.ACTIVE))
                .totalRequisitions(requisitionRepository.count())
                .pendingApprovals(approvalRepository.countByStatus(ApprovalStatus.PENDING))
                .totalPurchaseOrders(purchaseOrderRepository.count())
                .totalProcurementSpend(purchaseOrderRepository.calculateTotalSpend(PurchaseOrderStatus.CANCELLED))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public BuyerDashboardResponse getBuyerDashboard(Long userId) {
        return BuyerDashboardResponse.builder()
                .totalMyRequisitions(requisitionRepository.countByRequesterId(userId))
                .draftRequisitions(requisitionRepository.countByRequesterIdAndStatus(userId, RequisitionStatus.DRAFT))
                .pendingRequisitions(requisitionRepository.countByRequesterIdAndStatus(userId, RequisitionStatus.SUBMITTED))
                .approvedRequisitions(requisitionRepository.countByRequesterIdAndStatus(userId, RequisitionStatus.APPROVED))
                .rejectedRequisitions(requisitionRepository.countByRequesterIdAndStatus(userId, RequisitionStatus.REJECTED))
                .myPurchaseOrders(purchaseOrderRepository.countByStatus(PurchaseOrderStatus.DRAFT)) // Simplified
                .myTotalSpend(purchaseOrderRepository.calculateTotalSpendByUser(userId, PurchaseOrderStatus.CANCELLED))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ApproverDashboardResponse getApproverDashboard(Long userId) {
        // Role names are needed because a step may be assigned to a role
        // rather than to an individual.
        Set<RoleName> roles = userRepository.findById(userId)
                .map(u -> u.getRoles().stream().map(Role::getName).collect(Collectors.toSet()))
                .orElseGet(() -> Set.of(RoleName.APPROVER));

        return ApproverDashboardResponse.builder()
                // Only levels this approver can actually action, so the card
                // matches what the Pending Approvals page will show them.
                .pendingApprovals(approvalRepository.countActionableByApprover(
                        ApprovalStatus.PENDING, userId, roles))
                .approvedByMe(approvalRepository.countByApproverIdAndStatus(userId, ApprovalStatus.APPROVED))
                .rejectedByMe(approvalRepository.countByApproverIdAndStatus(userId, ApprovalStatus.REJECTED))
                .build();
    }
}
