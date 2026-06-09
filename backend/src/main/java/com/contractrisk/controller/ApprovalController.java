package com.contractrisk.controller;

import com.contractrisk.dto.ApiResponse;
import com.contractrisk.entity.ApprovalRecord;
import com.contractrisk.entity.ApprovalWorkflow;
import com.contractrisk.entity.enums.ApprovalStatus;
import com.contractrisk.service.ApprovalService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/approval")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class ApprovalController {

    private final ApprovalService approvalService;

    @PostMapping("/submit/{contractId}")
    public ApiResponse<ApprovalWorkflow> submitForApproval(
            @PathVariable Long contractId,
            @RequestBody SubmitApprovalRequest request) {
        try {
            ApprovalWorkflow workflow = approvalService.submitForApproval(
                    contractId,
                    request.getSubmitter(),
                    request.getCurrentApprover()
            );
            return ApiResponse.success("提交审批成功", workflow);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("提交审批失败", e);
            return ApiResponse.error("提交审批失败: " + e.getMessage());
        }
    }

    @PostMapping("/process/{workflowId}")
    public ApiResponse<ApprovalWorkflow> processApproval(
            @PathVariable Long workflowId,
            @RequestBody ProcessApprovalRequest request) {
        try {
            ApprovalWorkflow workflow = approvalService.processApproval(
                    workflowId,
                    request.getAction(),
                    request.getApprover(),
                    request.getComments()
            );
            return ApiResponse.success("审批处理完成", workflow);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("审批处理失败", e);
            return ApiResponse.error("审批处理失败: " + e.getMessage());
        }
    }

    @PostMapping("/escalate/{workflowId}")
    public ApiResponse<ApprovalWorkflow> escalateApproval(
            @PathVariable Long workflowId,
            @RequestBody EscalateApprovalRequest request) {
        try {
            ApprovalWorkflow workflow = approvalService.escalateApproval(
                    workflowId,
                    request.getEscalatedApprover(),
                    request.getReason()
            );
            return ApiResponse.success("审批升级成功", workflow);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("审批升级失败", e);
            return ApiResponse.error("审批升级失败: " + e.getMessage());
        }
    }

    @GetMapping("/workflow/{id}")
    public ApiResponse<ApprovalWorkflow> getWorkflowById(@PathVariable Long id) {
        Optional<ApprovalWorkflow> workflow = approvalService.getWorkflowById(id);
        return workflow
                .map(w -> ApiResponse.success(w))
                .orElseGet(() -> ApiResponse.error("审批流程不存在"));
    }

    @GetMapping("/workflow/contract/{contractId}")
    public ApiResponse<ApprovalWorkflow> getWorkflowByContractId(@PathVariable Long contractId) {
        Optional<ApprovalWorkflow> workflow = approvalService.getWorkflowByContractId(contractId);
        return workflow
                .map(w -> ApiResponse.success(w))
                .orElseGet(() -> ApiResponse.error("该合同未提交审批"));
    }

    @GetMapping("/workflows/status/{status}")
    public ApiResponse<List<ApprovalWorkflow>> getWorkflowsByStatus(@PathVariable ApprovalStatus status) {
        List<ApprovalWorkflow> workflows = approvalService.getWorkflowsByStatus(status);
        return ApiResponse.success(workflows);
    }

    @GetMapping("/workflows/approver/{approver}")
    public ApiResponse<List<ApprovalWorkflow>> getWorkflowsByApprover(@PathVariable String approver) {
        List<ApprovalWorkflow> workflows = approvalService.getWorkflowsByApprover(approver);
        return ApiResponse.success(workflows);
    }

    @GetMapping("/workflows/submitter/{submitter}")
    public ApiResponse<List<ApprovalWorkflow>> getWorkflowsBySubmitter(@PathVariable String submitter) {
        List<ApprovalWorkflow> workflows = approvalService.getWorkflowsBySubmitter(submitter);
        return ApiResponse.success(workflows);
    }

    @GetMapping("/records/{workflowId}")
    public ApiResponse<List<ApprovalRecord>> getApprovalRecords(@PathVariable Long workflowId) {
        List<ApprovalRecord> records = approvalService.getApprovalRecords(workflowId);
        return ApiResponse.success(records);
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Long>> getApprovalStats() {
        Map<String, Long> stats = approvalService.getApprovalStats();
        return ApiResponse.success(stats);
    }

    @PostMapping("/urge/{workflowId}")
    public ApiResponse<ApprovalWorkflow> urgeApproval(
            @PathVariable Long workflowId,
            @RequestBody UrgeApprovalRequest request) {
        try {
            ApprovalWorkflow workflow = approvalService.urgeApproval(
                    workflowId,
                    request.getRemindedBy()
            );
            return ApiResponse.success("催办成功", workflow);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("催办失败", e);
            return ApiResponse.error("催办失败: " + e.getMessage());
        }
    }

    @Data
    public static class SubmitApprovalRequest {
        private String submitter;
        private String currentApprover;
    }

    @Data
    public static class ProcessApprovalRequest {
        private ApprovalStatus action;
        private String approver;
        private String comments;
    }

    @Data
    public static class EscalateApprovalRequest {
        private String escalatedApprover;
        private String reason;
    }

    @Data
    public static class UrgeApprovalRequest {
        private String remindedBy;
    }
}
