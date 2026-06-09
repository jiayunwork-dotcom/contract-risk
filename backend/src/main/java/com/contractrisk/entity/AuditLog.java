package com.contractrisk.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operator", length = 100, nullable = false)
    private String operator;

    @Column(name = "operation_type", length = 50, nullable = false)
    private String operationType;

    @Column(name = "target_contract_id")
    private Long targetContractId;

    @Column(name = "target_version_id")
    private Long targetVersionId;

    @Column(name = "operation_result", length = 20, nullable = false)
    private String operationResult;

    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @CreationTimestamp
    @Column(name = "operation_time", updatable = false)
    private LocalDateTime operationTime;
}
