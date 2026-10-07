package com.smartprocure.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * Minimal approver info shown to requesters so they know
 * who is expected to act on their requisition.
 * Deliberately excludes sensitive fields.
 */
@Getter
@Builder
public class ApproverResponse {
    private Long id;
    private String name;
    private String email;
}
