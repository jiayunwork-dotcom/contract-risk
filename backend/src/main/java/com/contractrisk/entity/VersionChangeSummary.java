package com.contractrisk.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "version_change_summaries")
public class VersionChangeSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_version_id")
    private ContractVersion fromVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_version_id")
    private ContractVersion toVersion;

    @Column(name = "added_clauses_count", nullable = false)
    private Integer addedClausesCount = 0;

    @Column(name = "removed_clauses_count", nullable = false)
    private Integer removedClausesCount = 0;

    @Column(name = "modified_clauses_count", nullable = false)
    private Integer modifiedClausesCount = 0;

    @Column(name = "risk_score_change")
    private Integer riskScoreChange;

    @Column(name = "change_details", columnDefinition = "TEXT")
    private String changeDetails;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
