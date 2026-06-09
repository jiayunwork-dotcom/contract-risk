package com.contractrisk.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VersionTimelineResponse {

    private Long contractId;
    private String contractTitle;
    private Integer currentVersionNumber;
    private List<VersionNode> versions;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VersionNode {
        private Long versionId;
        private Integer versionNumber;
        private String versionLabel;
        private String uploadedBy;
        private LocalDateTime uploadTime;
        private String versionNote;
        private boolean isCurrent;
        private ChangeSummaryInfo changeSummary;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChangeSummaryInfo {
        private Integer addedClausesCount;
        private Integer removedClausesCount;
        private Integer modifiedClausesCount;
        private Integer riskScoreChange;
    }
}
