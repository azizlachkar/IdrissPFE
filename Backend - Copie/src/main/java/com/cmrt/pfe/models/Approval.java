package com.cmrt.pfe.models;

import com.cmrt.pfe.models.enums.ApprovalDecision;
import com.cmrt.pfe.models.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One required sign-off. Embedded inside a {@link ProjectStage} or an
 * {@link EngineeringChange}; a gate only closes once every approval it carries is
 * {@link ApprovalDecision#APPROVED}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Approval {

    /** The role that must sign off. */
    private Role requiredRole;

    @Builder.Default
    private ApprovalDecision decision = ApprovalDecision.PENDING;

    private String approverId;

    private String approverName;

    private String comment;

    private LocalDateTime decidedAt;

    public boolean isPending() {
        return decision == null || decision == ApprovalDecision.PENDING;
    }
}
