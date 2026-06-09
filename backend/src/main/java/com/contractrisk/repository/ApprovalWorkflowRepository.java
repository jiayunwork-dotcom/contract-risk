package com.contractrisk.repository;

import com.contractrisk.entity.ApprovalWorkflow;
import com.contractrisk.entity.enums.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ApprovalWorkflowRepository extends JpaRepository<ApprovalWorkflow, Long> {

    Optional<ApprovalWorkflow> findByContractId(Long contractId);

    List<ApprovalWorkflow> findByStatusOrderByCreatedAtDesc(ApprovalStatus status);

    List<ApprovalWorkflow> findByCurrentApproverOrderByCreatedAtDesc(String currentApprover);

    List<ApprovalWorkflow> findBySubmittedByOrderByCreatedAtDesc(String submittedBy);

    @Query("SELECT aw FROM ApprovalWorkflow aw WHERE aw.status IN (:statuses) " +
           "AND aw.submittedAt < :cutoffTime AND aw.escalated = false")
    List<ApprovalWorkflow> findPendingForEscalation(
            @Param("statuses") List<ApprovalStatus> statuses,
            @Param("cutoffTime") LocalDateTime cutoffTime);

    @Query("SELECT COUNT(aw) FROM ApprovalWorkflow aw WHERE aw.status = :status")
    long countByStatus(@Param("status") ApprovalStatus status);
}
