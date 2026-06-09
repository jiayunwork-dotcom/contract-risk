package com.contractrisk.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "risk_reports")
public class RiskReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false, unique = true)
    private Contract contract;

    @Column(name = "risk_score", precision = 5, scale = 2)
    private BigDecimal riskScore;

    @Column(name = "high_risk_count")
    private Integer highRiskCount = 0;

    @Column(name = "medium_risk_count")
    private Integer mediumRiskCount = 0;

    @Column(name = "low_risk_count")
    private Integer lowRiskCount = 0;

    @Column(name = "total_risk_count")
    private Integer totalRiskCount = 0;

    @Column(name = "compliance_status", length = 50)
    private String complianceStatus;

    @Column(name = "missing_required_count")
    private Integer missingRequiredCount = 0;

    @Column(name = "forbidden_clause_count")
    private Integer forbiddenClauseCount = 0;

    @Column(name = "amount_violation_count")
    private Integer amountViolationCount = 0;

    @Column(name = "section_risk_distribution", columnDefinition = "TEXT")
    private String sectionRiskDistribution;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "recommendation", columnDefinition = "TEXT")
    private String recommendation;

    @Column(name = "is_recommended_reject")
    private boolean recommendedReject = false;

    @Column(name = "generated_by", length = 100)
    private String generatedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
