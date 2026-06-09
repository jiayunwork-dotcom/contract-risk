package com.contractrisk.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClauseDiffResponse {

    private Long fromVersionId;
    private Long toVersionId;
    private String fromVersionLabel;
    private String toVersionLabel;
    private Integer addedClausesCount;
    private Integer removedClausesCount;
    private Integer modifiedClausesCount;
    private List<ClauseDiffItem> diffs;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClauseDiffItem {
        private String changeType;
        private String clauseNumber;
        private String clauseTitle;
        private String sectionType;
        private Integer sortOrder;
        private String oldContent;
        private String newContent;
        private Double similarity;
        private List<TextDiffSegment> textDiffs;
        private Boolean introducesNewRisk;
        private String newRiskDescription;
        private Integer oldRiskCount;
        private Integer newRiskCount;
        private Boolean oldIsHighRisk;
        private Boolean newIsHighRisk;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TextDiffSegment {
        private String type;
        private String text;
    }
}
