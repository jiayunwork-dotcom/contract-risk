package com.contractrisk.service;

import com.contractrisk.dto.AuditLogResponse;
import com.contractrisk.entity.AuditLog;
import com.contractrisk.repository.AuditLogRepository;
import com.contractrisk.repository.AuditLogSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public void log(String operator, String operationType, Long contractId,
                    Long versionId, String result, String detail) {
        AuditLog auditLog = new AuditLog();
        auditLog.setOperator(operator);
        auditLog.setOperationType(operationType);
        auditLog.setTargetContractId(contractId);
        auditLog.setTargetVersionId(versionId);
        auditLog.setOperationResult(result);
        auditLog.setDetail(detail);
        auditLogRepository.save(auditLog);
        log.debug("审计日志记录: {} - {} - 合同ID:{} 版本ID:{}", operator, operationType, contractId, versionId);
    }

    @Transactional
    public void logSuccess(String operator, String operationType, Long contractId,
                           Long versionId, String detail) {
        log(operator, operationType, contractId, versionId, "SUCCESS", detail);
    }

    @Transactional
    public void logFailure(String operator, String operationType, Long contractId,
                           Long versionId, String detail) {
        log(operator, operationType, contractId, versionId, "FAILURE", detail);
    }

    public Page<AuditLogResponse> queryAuditLogs(String operator, String operationType,
                                                   LocalDateTime startTime, LocalDateTime endTime,
                                                   Long contractId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "operationTime"));
        Page<AuditLog> logPage = auditLogRepository.findAll(
                AuditLogSpecification.withFilters(operator, operationType, startTime, endTime, contractId),
                pageable);
        return logPage.map(this::toResponse);
    }

    public Page<AuditLogResponse> getAllAuditLogs(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "operationTime"));
        Page<AuditLog> logPage = auditLogRepository.findAll(pageable);
        return logPage.map(this::toResponse);
    }

    private AuditLogResponse toResponse(AuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getId(),
                auditLog.getOperator(),
                auditLog.getOperationType(),
                auditLog.getOperationTime(),
                auditLog.getTargetContractId(),
                auditLog.getTargetVersionId(),
                auditLog.getOperationResult(),
                auditLog.getDetail()
        );
    }
}
