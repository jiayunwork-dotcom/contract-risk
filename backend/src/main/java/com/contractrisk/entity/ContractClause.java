package com.contractrisk.entity;

import com.contractrisk.entity.enums.ClauseSection;
import jakarta.persistence.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "contract_clauses")
public class ContractClause {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @Column(name = "clause_number", length = 50)
    private String clauseNumber;

    @Column(length = 500)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "section_type")
    private ClauseSection sectionType;

    @Column(name = "start_position")
    private Integer startPosition;

    @Column(name = "end_position")
    private Integer endPosition;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "parent_clause_id")
    private Long parentClauseId;

    @OneToMany(mappedBy = "clause", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RiskItem> riskItems = new ArrayList<>();

    @Column(name = "is_high_risk")
    private boolean highRisk = false;

    @Column(name = "risk_count")
    private Integer riskCount = 0;
}
