package com.smartprocure.controller;

import com.smartprocure.dto.response.ApproverResponse;
import com.smartprocure.dto.response.CostCenterResponse;
import com.smartprocure.dto.response.DepartmentResponse;
import com.smartprocure.entity.CostCenter;
import com.smartprocure.entity.Role.RoleName;
import com.smartprocure.entity.User;
import com.smartprocure.entity.User.UserStatus;
import com.smartprocure.repository.CostCenterRepository;
import com.smartprocure.repository.DepartmentRepository;
import com.smartprocure.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Provides lookup data for dropdowns in the frontend.
 * Departments and cost centers are used when creating requisitions.
 */
@RestController
@RequestMapping("/api/lookup")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Lookup Data", description = "Departments, cost centers and approvers for dropdowns")
public class LookupController {

    private final DepartmentRepository departmentRepository;
    private final CostCenterRepository costCenterRepository;
    private final UserRepository userRepository;

    /**
     * Returns active users who can approve requisitions.
     *
     * By default only the APPROVER role, which is what a requester needs to
     * see. Set includeAdmins=true when building the approval matrix, since
     * admins are also valid approvers there.
     */
    @GetMapping("/approvers")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get active approvers")
    public ResponseEntity<List<ApproverResponse>> getApprovers(
            @RequestParam(defaultValue = "false") boolean includeAdmins) {

        log.info("Fetching active approvers [includeAdmins={}]", includeAdmins);

        List<User> users = new ArrayList<>(
                userRepository.findByRoles_NameAndStatus(RoleName.APPROVER, UserStatus.ACTIVE));

        if (includeAdmins) {
            userRepository.findByRoles_NameAndStatus(RoleName.ADMIN, UserStatus.ACTIVE).stream()
                    // An ADMIN who also holds APPROVER would otherwise appear twice.
                    .filter(admin -> users.stream().noneMatch(u -> u.getId().equals(admin.getId())))
                    .forEach(users::add);
        }

        List<ApproverResponse> approvers = users.stream()
                .map(u -> ApproverResponse.builder()
                        .id(u.getId())
                        .name(u.getFirstName() + " " + u.getLastName())
                        .email(u.getEmail())
                        .build())
                .collect(Collectors.toList());

        log.info("Found {} eligible approvers", approvers.size());
        return ResponseEntity.ok(approvers);
    }

    @GetMapping("/departments")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get all departments")
    public ResponseEntity<List<DepartmentResponse>> getAllDepartments() {
        List<DepartmentResponse> departments = departmentRepository.findAll().stream()
                .map(d -> DepartmentResponse.builder()
                        .id(d.getId())
                        .name(d.getName())
                        .code(d.getCode())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(departments);
    }

    @GetMapping("/cost-centers")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get cost centers, optionally filtered by department")
    public ResponseEntity<List<CostCenterResponse>> getCostCenters(
            @RequestParam(required = false) Long departmentId) {

        List<CostCenter> costCenters;
        if (departmentId != null) {
            costCenters = costCenterRepository.findByDepartmentId(departmentId);
        } else {
            costCenters = costCenterRepository.findAll();
        }

        List<CostCenterResponse> responses = costCenters.stream()
                .map(cc -> CostCenterResponse.builder()
                        .id(cc.getId())
                        .name(cc.getName())
                        .code(cc.getCode())
                        .departmentId(cc.getDepartment().getId())
                        .departmentName(cc.getDepartment().getName())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }
}
