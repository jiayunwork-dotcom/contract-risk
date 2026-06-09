package com.contractrisk.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChangeImpactReport {

    private Long contractId;
    private String fromVersionLabel;
    private String toVersionLabel;
    private BigDecimal fromRiskScore;
    private BigDecimal toRiskScore;
    private Integer riskScoreChange;
    private List<SectionRiskTrend> sectionRiskTrends;
    private List<RiskItemInfo> newRiskItems;
    private List<RiskItemInfo> eliminatedRiskItems;
    private List<String> modificationSuggestions;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SectionRiskTrend {
        private String sectionName;
        private String sectionChineseName;
        private String trend;
        private Integer fromCount;
        private Integer toCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskItemInfo {
        private String ruleName;
        private String riskLevel;
        private String riskDescription;
        private String clauseNumber;
        private String clauseTitle;
        private String suggestion;
    }
}
