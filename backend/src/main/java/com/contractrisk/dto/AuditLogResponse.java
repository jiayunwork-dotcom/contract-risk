package com.contractrisk.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogResponse {

    private Long id;
    private String operator;
    private String operationType;
    private LocalDateTime operationTime;
    private Long targetContractId;
    private Long targetVersionId;
    private String operationResult;
    private String detail;
}
