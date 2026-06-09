package com.contractrisk.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContractUploadResponse {
    private Long id;
    private String title;
    private String contractNumber;
    private String contractType;
    private String partyA;
    private String partyB;
    private BigDecimal totalAmount;
    private String amountText;
    private String originalFileName;
    private String fileType;
    private LocalDateTime effectiveDate;
    private LocalDateTime expirationDate;
    private LocalDateTime signingDate;
    private int clauseCount;
    private LocalDateTime createdAt;
}
