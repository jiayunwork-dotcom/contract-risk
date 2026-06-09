package com.contractrisk.service;

import com.contractrisk.config.ContractConfig;
import com.contractrisk.entity.ApprovalRecord;
import com.contractrisk.entity.ApprovalWorkflow;
import com.contractrisk.entity.Contract;
import com.contractrisk.entity.enums.ApprovalStatus;
import com.contractrisk.repository.ApprovalRecordRepository;
import com.contractrisk.repository.ApprovalWorkflowRepository;
import com.contractrisk.repository.ContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalService {

    private final ApprovalWorkflowRepository workflowRepository;
    private final ApprovalRecordRepository recordRepository;
    private final ContractRepository contractRepository;
    private final ContractConfig contractConfig;

    @Transactional
    public ApprovalWorkflow submitForApproval(Long contractId, String submitter, String currentApprover) {
        Contract contract = contractRepository.findByIdAndDeletedFalse(contractId)
                .orElseThrow(() -> new IllegalArgumentException("合同不存在: " + contractId));

        Optional<ApprovalWorkflow> existingWorkflow = workflowRepository.findByContractId(contractId);
        ApprovalWorkflow workflow;

        if (existingWorkflow.isPresent()) {
            workflow = existingWorkflow.get();
            workflow.setStatus(ApprovalStatus.PENDING);
            workflow.setCurrentApprover(currentApprover);
            workflow.setCurrentLevel(1);
            workflow.setEscalated(false);
            workflow.setEscalatedApprover(null);
            workflow.getApprovalRecords().clear();
        } else {
            workflow = new ApprovalWorkflow();
            workflow.setContract(contract);
            workflow.setStatus(ApprovalStatus.PENDING);
            workflow.setCurrentApprover(currentApprover);
            workflow.setSubmittedBy(submitter);
            workflow.setCurrentLevel(1);
        }

        workflow.setSubmittedAt(LocalDateTime.now());
        workflow = workflowRepository.save(workflow);

        ApprovalRecord record = new ApprovalRecord();
        record.setWorkflow(workflow);
        record.setAction(ApprovalStatus.PENDING);
        record.setApprover(submitter);
        record.setComments("提交审批");
        record.setApprovalLevel(1);
        recordRepository.save(record);

        log.info("合同提交审批，合同ID: {}, 提交人: {}, 当前审批人: {}",
                contractId, submitter, currentApprover);

        return workflow;
    }

    @Transactional
    public ApprovalWorkflow processApproval(Long workflowId, ApprovalStatus action,
                                            String approver, String comments) {
        ApprovalWorkflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new IllegalArgumentException("审批流程不存在: " + workflowId));

        if (workflow.getStatus() == ApprovalStatus.APPROVED ||
                workflow.getStatus() == ApprovalStatus.REJECTED) {
            throw new IllegalStateException("审批流程已结束，状态: " + workflow.getStatus());
        }

        if (action != ApprovalStatus.APPROVED &&
                action != ApprovalStatus.REJECTED &&
                action != ApprovalStatus.NEEDS_MODIFICATION) {
            throw new IllegalArgumentException("无效的审批动作: " + action);
        }

        ApprovalRecord record = new ApprovalRecord();
        record.setWorkflow(workflow);
        record.setAction(action);
        record.setApprover(approver);
        record.setComments(comments);
        record.setApprovalLevel(workflow.getCurrentLevel());
        recordRepository.save(record);

        switch (action) {
            case APPROVED -> {
                if (workflow.getCurrentLevel() < 2) {
                    workflow.setStatus(ApprovalStatus.UNDER_REVIEW);
                    workflow.setCurrentLevel(2);
                    workflow.setEscalatedApprover("senior_approver");
                    log.info("一级审批通过，进入二级审批，工作流ID: {}", workflowId);
                } else {
                    workflow.setStatus(ApprovalStatus.APPROVED);
                    log.info("审批通过，工作流ID: {}", workflowId);
                }
            }
            case REJECTED -> {
                workflow.setStatus(ApprovalStatus.REJECTED);
                log.info("审批拒绝，工作流ID: {}", workflowId);
            }
            case NEEDS_MODIFICATION -> {
                workflow.setStatus(ApprovalStatus.NEEDS_MODIFICATION);
                log.info("需修改，工作流ID: {}", workflowId);
            }
            default -> { }
        }

        return workflowRepository.save(workflow);
    }

    @Transactional
    public ApprovalWorkflow escalateApproval(Long workflowId, String escalatedApprover, String reason) {
        ApprovalWorkflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new IllegalArgumentException("审批流程不存在: " + workflowId));

        if (workflow.isEscalated()) {
            throw new IllegalStateException("审批已升级");
        }

        workflow.setEscalated(true);
        workflow.setEscalatedApprover(escalatedApprover);
        workflow.setStatus(ApprovalStatus.ESCALATED);
        workflow.setCurrentLevel(workflow.getCurrentLevel() + 1);

        ApprovalRecord record = new ApprovalRecord();
        record.setWorkflow(workflow);
        record.setAction(ApprovalStatus.ESCALATED);
        record.setApprover(escalatedApprover);
        record.setComments("自动升级审批: " + reason);
        record.setApprovalLevel(workflow.getCurrentLevel());
        recordRepository.save(record);

        log.info("审批升级，工作流ID: {}, 升级给: {}, 原因: {}", workflowId, escalatedApprover, reason);

        return workflowRepository.save(workflow);
    }

    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void checkForAutoEscalation() {
        log.debug("检查需要自动升级的审批...");

        int hours = contractConfig.getApproval().getAutoEscalateHours();
        LocalDateTime cutoffTime = LocalDateTime.now().minusHours(hours);

        List<ApprovalStatus> pendingStatuses = Arrays.asList(
                ApprovalStatus.PENDING,
                ApprovalStatus.UNDER_REVIEW
        );

        List<ApprovalWorkflow> pendingWorkflows = workflowRepository.findPendingForEscalation(
                pendingStatuses, cutoffTime
        );

        for (ApprovalWorkflow workflow : pendingWorkflows) {
            try {
                escalateApproval(
                        workflow.getId(),
                        "auto_escalation_manager",
                        "审批超过" + hours + "小时未处理，自动升级"
                );
            } catch (Exception e) {
                log.error("自动升级审批失败，工作流ID: {}", workflow.getId(), e);
            }
        }

        if (!pendingWorkflows.isEmpty()) {
            log.info("自动升级了{}个审批流程", pendingWorkflows.size());
        }
    }

    public Optional<ApprovalWorkflow> getWorkflowById(Long id) {
        return workflowRepository.findById(id);
    }

    public Optional<ApprovalWorkflow> getWorkflowByContractId(Long contractId) {
        return workflowRepository.findByContractId(contractId);
    }

    public List<ApprovalWorkflow> getWorkflowsByStatus(ApprovalStatus status) {
        return workflowRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    public List<ApprovalWorkflow> getWorkflowsByApprover(String approver) {
        return workflowRepository.findByCurrentApproverOrderByCreatedAtDesc(approver);
    }

    public List<ApprovalWorkflow> getWorkflowsBySubmitter(String submitter) {
        return workflowRepository.findBySubmittedByOrderByCreatedAtDesc(submitter);
    }

    public List<ApprovalRecord> getApprovalRecords(Long workflowId) {
        return recordRepository.findByWorkflowIdOrderByCreatedAtAsc(workflowId);
    }

    public Map<String, Long> getApprovalStats() {
        return Map.of(
                "pending", workflowRepository.countByStatus(ApprovalStatus.PENDING),
                "underReview", workflowRepository.countByStatus(ApprovalStatus.UNDER_REVIEW),
                "approved", workflowRepository.countByStatus(ApprovalStatus.APPROVED),
                "rejected", workflowRepository.countByStatus(ApprovalStatus.REJECTED),
                "needsModification", workflowRepository.countByStatus(ApprovalStatus.NEEDS_MODIFICATION),
                "escalated", workflowRepository.countByStatus(ApprovalStatus.ESCALATED)
        );
    }
}
