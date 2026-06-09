package com.contractrisk.controller;

import com.contractrisk.dto.ApiResponse;
import com.contractrisk.dto.AuditLogResponse;
import com.contractrisk.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Slf4j
@RestController
@RequestMapping("/audit-logs")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    public ApiResponse<Page<AuditLogResponse>> queryAuditLogs(
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) Long contractId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            Page<AuditLogResponse> result = auditLogService.queryAuditLogs(
                    operator, operationType, startTime, endTime, contractId, page, size);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("查询审计日志失败", e);
            return ApiResponse.error("查询审计日志失败: " + e.getMessage());
        }
    }
}
