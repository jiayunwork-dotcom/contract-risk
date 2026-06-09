package com.contractrisk.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchAnalysisResult {

    private String batchId;
    private int total;
    private int completed;
    private boolean finished;
    private List<ContractRiskRanking> rankings;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContractRiskRanking {
        private Long contractId;
        private String contractTitle;
        private BigDecimal riskScore;
        private int highRiskCount;
        private int mediumRiskCount;
        private int lowRiskCount;
        private int totalRiskCount;
        private int rank;
    }
}
