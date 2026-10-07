package com.smartprocure.service;

import com.smartprocure.dto.request.CreateRequisitionRequest;
import com.smartprocure.dto.response.PageResponse;
import com.smartprocure.dto.response.RequisitionResponse;
import com.smartprocure.entity.Requisition.RequisitionStatus;
import org.springframework.data.domain.Pageable;

public interface RequisitionService {
    RequisitionResponse createRequisition(CreateRequisitionRequest request, Long requesterId);
    RequisitionResponse getRequisitionById(Long id);
    RequisitionResponse submitRequisition(Long id, Long requesterId);
    PageResponse<RequisitionResponse> getMyRequisitions(Long requesterId, RequisitionStatus status, Pageable pageable);
    PageResponse<RequisitionResponse> getAllRequisitions(RequisitionStatus status, Long requesterId, Pageable pageable);
}
