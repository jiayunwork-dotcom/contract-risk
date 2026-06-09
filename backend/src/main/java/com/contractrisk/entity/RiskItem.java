package com.contractrisk.entity;

import com.contractrisk.entity.enums.RiskLevel;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "risk_items")
public class RiskItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clause_id", nullable = false)
    private ContractClause clause;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "risk_rule_id")
    private RiskRule riskRule;

    @Column(name = "rule_name", length = 200)
    private String ruleName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RiskLevel riskLevel;

    @Column(name = "risk_description", columnDefinition = "TEXT")
    private String riskDescription;

    @Column(name = "matched_text", columnDefinition = "TEXT")
    private String matchedText;

    @Column(name = "original_text", columnDefinition = "TEXT")
    private String originalText;

    @Column(name = "suggestion", columnDefinition = "TEXT")
    private String suggestion;

    @Column(name = "alternative_phrases", columnDefinition = "TEXT")
    private String alternativePhrases;

    @Column(name = "is_from_nlp", nullable = false)
    private boolean fromNlp = false;

    @Column(name = "match_position")
    private Integer matchPosition;

    @Column(name = "match_length")
    private Integer matchLength;

    @Column(name = "penalty_score")
    private Integer penaltyScore;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
