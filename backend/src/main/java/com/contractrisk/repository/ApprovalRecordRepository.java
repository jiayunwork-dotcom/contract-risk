package com.contractrisk.repository;

import com.contractrisk.entity.ApprovalRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApprovalRecordRepository extends JpaRepository<ApprovalRecord, Long> {

    List<ApprovalRecord> findByWorkflowIdOrderByCreatedAtAsc(Long workflowId);

    List<ApprovalRecord> findByApproverOrderByCreatedAtDesc(String approver);
}
