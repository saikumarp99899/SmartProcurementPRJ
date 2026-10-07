package com.smartprocure.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ApproverDashboardResponse {
    private long pendingApprovals;
    private long approvedByMe;
    private long rejectedByMe;
}
