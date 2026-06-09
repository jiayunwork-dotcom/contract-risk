package com.contractrisk.entity;

import com.contractrisk.entity.enums.RiskLevel;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "risk_rules")
public class RiskRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String ruleName;

    @Column(name = "match_pattern", columnDefinition = "TEXT", nullable = false)
    private String matchPattern;

    @Column(name = "keywords", length = 1000)
    private String keywords;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RiskLevel riskLevel;

    @Column(name = "risk_description", columnDefinition = "TEXT")
    private String riskDescription;

    @Column(name = "suggestion", columnDefinition = "TEXT")
    private String suggestion;

    @Column(name = "alternative_phrases", columnDefinition = "TEXT")
    private String alternativePhrases;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "rule_category", length = 100)
    private String ruleCategory;

    @Column(name = "custom_weight")
    private Integer customWeight;

    @Column(name = "match_mode", length = 50)
    private String matchMode = "REGEX_KEYWORD";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
