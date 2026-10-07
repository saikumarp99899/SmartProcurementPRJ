package com.smartprocure.service;

import com.smartprocure.dto.response.AdminDashboardResponse;
import com.smartprocure.dto.response.ApproverDashboardResponse;
import com.smartprocure.dto.response.BuyerDashboardResponse;

public interface DashboardService {
    AdminDashboardResponse getAdminDashboard();
    BuyerDashboardResponse getBuyerDashboard(Long userId);
    ApproverDashboardResponse getApproverDashboard(Long userId);
}
