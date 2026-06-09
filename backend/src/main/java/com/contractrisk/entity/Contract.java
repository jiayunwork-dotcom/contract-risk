package com.contractrisk.entity;

import com.contractrisk.entity.enums.ContractType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "contracts")
public class Contract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 100)
    private String contractNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContractType contractType;

    @Column(length = 500)
    private String partyA;

    @Column(length = 500)
    private String partyB;

    @Column(precision = 18, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "amount_text")
    private String amountText;

    @Column(columnDefinition = "TEXT")
    private String fullText;

    @Column(length = 200)
    private String originalFileName;

    @Column(length = 100)
    private String fileType;

    @Column(length = 500)
    private String filePath;

    @Column(name = "effective_date")
    private LocalDateTime effectiveDate;

    @Column(name = "expiration_date")
    private LocalDateTime expirationDate;

    @Column(name = "signing_date")
    private LocalDateTime signingDate;

    @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ContractClause> clauses = new ArrayList<>();

    @OneToOne(mappedBy = "contract", cascade = CascadeType.ALL)
    private RiskReport riskReport;

    @OneToOne(mappedBy = "contract", cascade = CascadeType.ALL)
    private ApprovalWorkflow approvalWorkflow;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ContractVersion> versions = new ArrayList<>();

    @Column(name = "current_version_id")
    private Long currentVersionId;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;
}
