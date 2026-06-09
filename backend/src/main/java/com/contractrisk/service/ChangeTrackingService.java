package com.contractrisk.service;

import com.contractrisk.dto.ChangeImpactReport;
import com.contractrisk.dto.ClauseDiffResponse;
import com.contractrisk.engine.RiskEngine;
import com.contractrisk.entity.ContractClause;
import com.contractrisk.entity.ContractVersion;
import com.contractrisk.entity.RiskItem;
import com.contractrisk.entity.RiskReport;
import com.contractrisk.entity.VersionChangeSummary;
import com.contractrisk.entity.enums.ChangeType;
import com.contractrisk.entity.enums.OperationType;
import com.contractrisk.repository.ContractClauseRepository;
import com.contractrisk.repository.ContractVersionRepository;
import com.contractrisk.repository.RiskItemRepository;
import com.contractrisk.repository.RiskReportRepository;
import com.contractrisk.repository.VersionChangeSummaryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChangeTrackingService {

    private static final double SIMILARITY_THRESHOLD = 0.8;

    private final ContractVersionRepository versionRepository;
    private final ContractClauseRepository clauseRepository;
    private final VersionChangeSummaryRepository changeSummaryRepository;
    private final RiskReportRepository riskReportRepository;
    private final RiskItemRepository riskItemRepository;
    private final RiskEngine riskEngine;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final LevenshteinDistance levenshteinDistance = new LevenshteinDistance();

    @Transactional
    public VersionChangeSummary generateChangeSummary(Long contractId, int fromVersionNum, int toVersionNum) {
        ContractVersion fromVersion = versionRepository
                .findByContractIdAndVersionNumber(contractId, fromVersionNum)
                .orElseThrow(() -> new IllegalArgumentException("源版本不存在: v" + fromVersionNum));
        ContractVersion toVersion = versionRepository
                .findByContractIdAndVersionNumber(contractId, toVersionNum)
                .orElseThrow(() -> new IllegalArgumentException("目标版本不存在: v" + toVersionNum));

        List<ContractClause> oldClauses = clauseRepository.findByVersionId(fromVersion.getId());
        List<ContractClause> newClauses = clauseRepository.findByVersionId(toVersion.getId());

        ClauseMatchResult matchResult = matchClausesByContent(oldClauses, newClauses);

        int added = matchResult.added.size();
        int removed = matchResult.removed.size();
        int modified = matchResult.modified.size();

        Integer riskScoreChange = calculateRiskScoreChange(contractId, fromVersion.getId(), toVersion.getId());

        VersionChangeSummary summary = new VersionChangeSummary();
        summary.setContractId(contractId);
        summary.setFromVersion(fromVersion);
        summary.setToVersion(toVersion);
        summary.setAddedClausesCount(added);
        summary.setRemovedClausesCount(removed);
        summary.setModifiedClausesCount(modified);
        summary.setRiskScoreChange(riskScoreChange);

        try {
            Map<String, Object> details = new HashMap<>();
            details.put("addedClauseNumbers", matchResult.added.stream()
                    .map(ContractClause::getClauseNumber).collect(Collectors.toList()));
            details.put("removedClauseNumbers", matchResult.removed.stream()
                    .map(ContractClause::getClauseNumber).collect(Collectors.toList()));
            details.put("modifiedClauseInfo", matchResult.modified.stream()
                    .map(m -> Map.of(
                            "oldNumber", m.oldClause.getClauseNumber() != null ? m.oldClause.getClauseNumber() : "",
                            "newNumber", m.newClause.getClauseNumber() != null ? m.newClause.getClauseNumber() : "",
                            "similarity", String.format("%.2f", m.similarity)
                    ))
                    .collect(Collectors.toList()));
            summary.setChangeDetails(objectMapper.writeValueAsString(details));
        } catch (JsonProcessingException e) {
            summary.setChangeDetails("{}");
        }

        summary = changeSummaryRepository.save(summary);
        log.info("变更摘要生成完成，合同ID: {}，{} -> {}，新增:{} 删除:{} 修改:{}",
                contractId, fromVersion.getVersionLabel(), toVersion.getVersionLabel(),
                added, removed, modified);
        return summary;
    }

    public ClauseDiffResponse getClauseDiff(Long contractId, Integer fromVersionNum, Integer toVersionNum) {
        ContractVersion fromVersion = versionRepository
                .findByContractIdAndVersionNumber(contractId, fromVersionNum)
                .orElseThrow(() -> new IllegalArgumentException("源版本不存在: v" + fromVersionNum));
        ContractVersion toVersion = versionRepository
                .findByContractIdAndVersionNumber(contractId, toVersionNum)
                .orElseThrow(() -> new IllegalArgumentException("目标版本不存在: v" + toVersionNum));

        List<ContractClause> oldClauses = clauseRepository.findByVersionId(fromVersion.getId());
        List<ContractClause> newClauses = clauseRepository.findByVersionId(toVersion.getId());

        ClauseMatchResult matchResult = matchClausesByContent(oldClauses, newClauses);

        List<RiskItem> newVersionRisks = riskItemRepository.findByContractId(contractId);

        List<ClauseDiffResponse.ClauseDiffItem> diffs = new ArrayList<>();

        for (ContractClause added : matchResult.added) {
            ClauseDiffResponse.ClauseDiffItem item = new ClauseDiffResponse.ClauseDiffItem();
            item.setChangeType(ChangeType.ADDED.name());
            item.setClauseNumber(added.getClauseNumber());
            item.setClauseTitle(added.getTitle());
            item.setSectionType(added.getSectionType() != null ? added.getSectionType().name() : null);
            item.setSortOrder(added.getSortOrder());
            item.setNewContent(added.getContent());
            item.setNewRiskCount(added.getRiskCount());
            item.setNewIsHighRisk(added.isHighRisk());

            List<RiskItem> clauseRisks = findRisksForClause(newVersionRisks, added);
            item.setIntroducesNewRisk(!clauseRisks.isEmpty());
            if (!clauseRisks.isEmpty()) {
                item.setNewRiskDescription(clauseRisks.stream()
                        .map(r -> r.getRuleName() + ": " + r.getRiskDescription())
                        .collect(Collectors.joining("; ")));
            }
            diffs.add(item);
        }

        for (ContractClause removed : matchResult.removed) {
            ClauseDiffResponse.ClauseDiffItem item = new ClauseDiffResponse.ClauseDiffItem();
            item.setChangeType(ChangeType.REMOVED.name());
            item.setClauseNumber(removed.getClauseNumber());
            item.setClauseTitle(removed.getTitle());
            item.setSectionType(removed.getSectionType() != null ? removed.getSectionType().name() : null);
            item.setSortOrder(removed.getSortOrder());
            item.setOldContent(removed.getContent());
            item.setOldRiskCount(removed.getRiskCount());
            item.setOldIsHighRisk(removed.isHighRisk());
            diffs.add(item);
        }

        for (MatchedClause matched : matchResult.modified) {
            ClauseDiffResponse.ClauseDiffItem item = new ClauseDiffResponse.ClauseDiffItem();
            item.setChangeType(ChangeType.MODIFIED.name());
            item.setClauseNumber(matched.newClause.getClauseNumber());
            item.setClauseTitle(matched.newClause.getTitle());
            item.setSectionType(matched.newClause.getSectionType() != null ? matched.newClause.getSectionType().name() : null);
            item.setSortOrder(matched.newClause.getSortOrder());
            item.setOldContent(matched.oldClause.getContent());
            item.setNewContent(matched.newClause.getContent());
            item.setSimilarity(matched.similarity);
            item.setOldRiskCount(matched.oldClause.getRiskCount());
            item.setNewRiskCount(matched.newClause.getRiskCount());
            item.setOldIsHighRisk(matched.oldClause.isHighRisk());
            item.setNewIsHighRisk(matched.newClause.isHighRisk());

            List<ClauseDiffResponse.TextDiffSegment> segments = generateTextDiff(
                    matched.oldClause.getContent(), matched.newClause.getContent());
            item.setTextDiffs(segments);

            boolean riskIncreased = matched.newClause.getRiskCount() > matched.oldClause.getRiskCount()
                    || (matched.newClause.isHighRisk() && !matched.oldClause.isHighRisk());
            item.setIntroducesNewRisk(riskIncreased);
            if (riskIncreased) {
                List<RiskItem> clauseRisks = findRisksForClause(newVersionRisks, matched.newClause);
                if (!clauseRisks.isEmpty()) {
                    item.setNewRiskDescription(clauseRisks.stream()
                            .map(r -> r.getRuleName() + ": " + r.getRiskDescription())
                            .collect(Collectors.joining("; ")));
                }
            }
            diffs.add(item);
        }

        diffs.sort(Comparator.comparingInt(a -> a.getSortOrder() != null ? a.getSortOrder() : Integer.MAX_VALUE));

        ClauseDiffResponse response = new ClauseDiffResponse();
        response.setFromVersionId(fromVersion.getId());
        response.setToVersionId(toVersion.getId());
        response.setFromVersionLabel(fromVersion.getVersionLabel());
        response.setToVersionLabel(toVersion.getVersionLabel());
        response.setAddedClausesCount(matchResult.added.size());
        response.setRemovedClausesCount(matchResult.removed.size());
        response.setModifiedClausesCount(matchResult.modified.size());
        response.setDiffs(diffs);

        auditLogService.logSuccess("system", OperationType.VIEW_CHANGE_DIFF.name(),
                contractId, toVersion.getId(),
                "查看变更详情 " + fromVersion.getVersionLabel() + " -> " + toVersion.getVersionLabel());

        return response;
    }

    public ChangeImpactReport getImpactAssessment(Long contractId, Integer fromVersionNum, Integer toVersionNum) {
        ContractVersion fromVersion = versionRepository
                .findByContractIdAndVersionNumber(contractId, fromVersionNum)
                .orElseThrow(() -> new IllegalArgumentException("源版本不存在: v" + fromVersionNum));
        ContractVersion toVersion = versionRepository
                .findByContractIdAndVersionNumber(contractId, toVersionNum)
                .orElseThrow(() -> new IllegalArgumentException("目标版本不存在: v" + toVersionNum));

        Optional<RiskReport> fromReportOpt = riskReportRepository.findByVersionId(fromVersion.getId());
        Optional<RiskReport> toReportOpt = riskReportRepository.findByVersionId(toVersion.getId());

        ChangeImpactReport report = new ChangeImpactReport();
        report.setContractId(contractId);
        report.setFromVersionLabel(fromVersion.getVersionLabel());
        report.setToVersionLabel(toVersion.getVersionLabel());

        BigDecimal fromScore = fromReportOpt.map(RiskReport::getRiskScore).orElse(BigDecimal.valueOf(100));
        BigDecimal toScore = toReportOpt.map(RiskReport::getRiskScore).orElse(BigDecimal.valueOf(100));
        report.setFromRiskScore(fromScore);
        report.setToRiskScore(toScore);
        report.setRiskScoreChange(toScore.intValue() - fromScore.intValue());

        report.setSectionRiskTrends(calculateSectionRiskTrends(
                contractId, fromVersion.getId(), toVersion.getId(),
                fromReportOpt, toReportOpt));

        report.setNewRiskItems(calculateNewRisks(contractId, fromVersion.getId(), toVersion.getId()));
        report.setEliminatedRiskItems(calculateEliminatedRisks(contractId, fromVersion.getId(), toVersion.getId()));
        report.setModificationSuggestions(collectModificationSuggestions(contractId, toVersion.getId()));

        auditLogService.logSuccess("system", OperationType.IMPACT_ASSESSMENT.name(),
                contractId, toVersion.getId(),
                "变更影响评估 " + fromVersion.getVersionLabel() + " -> " + toVersion.getVersionLabel());

        return report;
    }

    ClauseMatchResult matchClausesByContent(List<ContractClause> oldClauses, List<ContractClause> newClauses) {
        ClauseMatchResult result = new ClauseMatchResult();
        boolean[] oldMatched = new boolean[oldClauses.size()];
        boolean[] newMatched = new boolean[newClauses.size()];

        double[][] simMatrix = new double[oldClauses.size()][newClauses.size()];
        for (int i = 0; i < oldClauses.size(); i++) {
            for (int j = 0; j < newClauses.size(); j++) {
                simMatrix[i][j] = calculateSimilarity(
                        oldClauses.get(i).getContent(), newClauses.get(j).getContent());
            }
        }

        List<MatchCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < oldClauses.size(); i++) {
            for (int j = 0; j < newClauses.size(); j++) {
                if (simMatrix[i][j] >= SIMILARITY_THRESHOLD) {
                    candidates.add(new MatchCandidate(i, j, simMatrix[i][j]));
                }
            }
        }
        candidates.sort((a, b) -> Double.compare(b.similarity, a.similarity));

        for (MatchCandidate mc : candidates) {
            if (oldMatched[mc.oldIdx] || newMatched[mc.newIdx]) {
                continue;
            }
            oldMatched[mc.oldIdx] = true;
            newMatched[mc.newIdx] = true;

            if (mc.similarity < 1.0) {
                result.modified.add(new MatchedClause(
                        oldClauses.get(mc.oldIdx), newClauses.get(mc.newIdx), mc.similarity));
            }
        }

        for (int i = 0; i < oldClauses.size(); i++) {
            if (!oldMatched[i]) {
                result.removed.add(oldClauses.get(i));
            }
        }

        for (int j = 0; j < newClauses.size(); j++) {
            if (!newMatched[j]) {
                result.added.add(newClauses.get(j));
            }
        }

        return result;
    }

    private double calculateSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0;
        if (s1.equals(s2)) return 1.0;

        int maxLength = Math.max(s1.length(), s2.length());
        if (maxLength == 0) return 1.0;

        int distance = levenshteinDistance.apply(s1, s2);
        return 1.0 - ((double) distance / maxLength);
    }

    private List<ClauseDiffResponse.TextDiffSegment> generateTextDiff(String oldText, String newText) {
        List<ClauseDiffResponse.TextDiffSegment> segments = new ArrayList<>();
        if (oldText == null) oldText = "";
        if (newText == null) newText = "";

        String[] oldLines = oldText.split("\n");
        String[] newLines = newText.split("\n");

        int[][] dp = computeLCSLength(oldLines, newLines);

        List<String> lcsOld = new ArrayList<>();
        List<String> lcsNew = new ArrayList<>();
        backtrackLCS(dp, oldLines, newLines, oldLines.length, newLines.length, lcsOld, lcsNew);

        int oi = 0, ni = 0;
        for (int k = 0; k < lcsOld.size(); k++) {
            while (oi < oldLines.length && !oldLines[oi].equals(lcsOld.get(k))) {
                segments.add(new ClauseDiffResponse.TextDiffSegment("removed", oldLines[oi]));
                oi++;
            }
            while (ni < newLines.length && !newLines[ni].equals(lcsNew.get(k))) {
                segments.add(new ClauseDiffResponse.TextDiffSegment("added", newLines[ni]));
                ni++;
            }
            if (oi < oldLines.length && ni < newLines.length) {
                segments.add(new ClauseDiffResponse.TextDiffSegment("unchanged", oldLines[oi]));
                oi++;
                ni++;
            }
        }
        while (oi < oldLines.length) {
            segments.add(new ClauseDiffResponse.TextDiffSegment("removed", oldLines[oi]));
            oi++;
        }
        while (ni < newLines.length) {
            segments.add(new ClauseDiffResponse.TextDiffSegment("added", newLines[ni]));
            ni++;
        }

        return segments;
    }

    private int[][] computeLCSLength(String[] a, String[] b) {
        int m = a.length, n = b.length;
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (a[i - 1].equals(b[j - 1])) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }
        return dp;
    }

    private void backtrackLCS(int[][] dp, String[] a, String[] b, int i, int j,
                              List<String> lcsA, List<String> lcsB) {
        if (i == 0 || j == 0) return;
        if (a[i - 1].equals(b[j - 1])) {
            backtrackLCS(dp, a, b, i - 1, j - 1, lcsA, lcsB);
            lcsA.add(a[i - 1]);
            lcsB.add(b[j - 1]);
        } else if (dp[i - 1][j] >= dp[i][j - 1]) {
            backtrackLCS(dp, a, b, i - 1, j, lcsA, lcsB);
        } else {
            backtrackLCS(dp, a, b, i, j - 1, lcsA, lcsB);
        }
    }

    private Integer calculateRiskScoreChange(Long contractId, Long fromVersionId, Long toVersionId) {
        Optional<RiskReport> fromReport = riskReportRepository.findByVersionId(fromVersionId);
        Optional<RiskReport> toReport = riskReportRepository.findByVersionId(toVersionId);

        if (fromReport.isPresent() && toReport.isPresent()) {
            int fromScore = fromReport.get().getRiskScore().intValue();
            int toScore = toReport.get().getRiskScore().intValue();
            return toScore - fromScore;
        }
        return null;
    }

    private List<ChangeImpactReport.SectionRiskTrend> calculateSectionRiskTrends(
            Long contractId, Long fromVersionId, Long toVersionId,
            Optional<RiskReport> fromReportOpt, Optional<RiskReport> toReportOpt) {

        List<ContractClause> fromClauses = clauseRepository.findByVersionId(fromVersionId);
        List<ContractClause> toClauses = clauseRepository.findByVersionId(toVersionId);

        Map<String, Long> fromSectionRiskCounts = fromClauses.stream()
                .filter(c -> c.getSectionType() != null && c.getRiskCount() > 0)
                .collect(Collectors.groupingBy(c -> c.getSectionType().name(), Collectors.counting()));

        Map<String, Long> toSectionRiskCounts = toClauses.stream()
                .filter(c -> c.getSectionType() != null && c.getRiskCount() > 0)
                .collect(Collectors.groupingBy(c -> c.getSectionType().name(), Collectors.counting()));

        Set<String> allSections = new HashSet<>();
        allSections.addAll(fromSectionRiskCounts.keySet());
        allSections.addAll(toSectionRiskCounts.keySet());

        List<ChangeImpactReport.SectionRiskTrend> trends = new ArrayList<>();
        for (String section : allSections) {
            long fromCount = fromSectionRiskCounts.getOrDefault(section, 0L);
            long toCount = toSectionRiskCounts.getOrDefault(section, 0L);

            String trend;
            if (toCount > fromCount) {
                trend = "升高";
            } else if (toCount < fromCount) {
                trend = "降低";
            } else {
                trend = "持平";
            }

            trends.add(new ChangeImpactReport.SectionRiskTrend(
                    section, translateSection(section), trend,
                    (int) fromCount, (int) toCount));
        }

        trends.sort(Comparator.comparing(ChangeImpactReport.SectionRiskTrend::getSectionName));
        return trends;
    }

    private List<ChangeImpactReport.RiskItemInfo> calculateNewRisks(
            Long contractId, Long fromVersionId, Long toVersionId) {
        List<RiskItem> oldRisks = riskItemRepository.findByContractId(contractId).stream()
                .filter(r -> r.getClause() != null && r.getClause().getVersion() != null
                        && r.getClause().getVersion().getId().equals(fromVersionId))
                .collect(Collectors.toList());

        List<RiskItem> newRisks = riskItemRepository.findByContractId(contractId).stream()
                .filter(r -> r.getClause() != null && r.getClause().getVersion() != null
                        && r.getClause().getVersion().getId().equals(toVersionId))
                .collect(Collectors.toList());

        Set<String> oldSignatures = oldRisks.stream()
                .map(r -> r.getRuleName() + "|" + r.getRiskDescription())
                .collect(Collectors.toSet());

        return newRisks.stream()
                .filter(r -> !oldSignatures.contains(r.getRuleName() + "|" + r.getRiskDescription()))
                .map(r -> new ChangeImpactReport.RiskItemInfo(
                        r.getRuleName(),
                        r.getRiskLevel().name(),
                        r.getRiskDescription(),
                        r.getClause() != null ? r.getClause().getClauseNumber() : null,
                        r.getClause() != null ? r.getClause().getTitle() : null,
                        r.getSuggestion()))
                .collect(Collectors.toList());
    }

    private List<ChangeImpactReport.RiskItemInfo> calculateEliminatedRisks(
            Long contractId, Long fromVersionId, Long toVersionId) {
        List<RiskItem> oldRisks = riskItemRepository.findByContractId(contractId).stream()
                .filter(r -> r.getClause() != null && r.getClause().getVersion() != null
                        && r.getClause().getVersion().getId().equals(fromVersionId))
                .collect(Collectors.toList());

        List<RiskItem> newRisks = riskItemRepository.findByContractId(contractId).stream()
                .filter(r -> r.getClause() != null && r.getClause().getVersion() != null
                        && r.getClause().getVersion().getId().equals(toVersionId))
                .collect(Collectors.toList());

        Set<String> newSignatures = newRisks.stream()
                .map(r -> r.getRuleName() + "|" + r.getRiskDescription())
                .collect(Collectors.toSet());

        return oldRisks.stream()
                .filter(r -> !newSignatures.contains(r.getRuleName() + "|" + r.getRiskDescription()))
                .map(r -> new ChangeImpactReport.RiskItemInfo(
                        r.getRuleName(),
                        r.getRiskLevel().name(),
                        r.getRiskDescription(),
                        r.getClause() != null ? r.getClause().getClauseNumber() : null,
                        r.getClause() != null ? r.getClause().getTitle() : null,
                        r.getSuggestion()))
                .collect(Collectors.toList());
    }

    private List<String> collectModificationSuggestions(Long contractId, Long versionId) {
        List<RiskItem> risks = riskItemRepository.findByContractId(contractId).stream()
                .filter(r -> r.getClause() != null && r.getClause().getVersion() != null
                        && r.getClause().getVersion().getId().equals(versionId))
                .collect(Collectors.toList());

        return risks.stream()
                .filter(r -> r.getSuggestion() != null && !r.getSuggestion().isEmpty())
                .map(r -> "[" + r.getRiskLevel().name() + "] " + r.getClause().getClauseNumber()
                        + " " + r.getRuleName() + ": " + r.getSuggestion())
                .distinct()
                .collect(Collectors.toList());
    }

    private List<RiskItem> findRisksForClause(List<RiskItem> risks, ContractClause clause) {
        return risks.stream()
                .filter(r -> r.getClause() != null && r.getClause().getId().equals(clause.getId()))
                .collect(Collectors.toList());
    }

    private String translateSection(String section) {
        return switch (section) {
            case "PARTIES_INFO" -> "当事人信息";
            case "DEFINITIONS" -> "定义条款";
            case "RIGHTS_AND_OBLIGATIONS" -> "权利义务";
            case "BREACH_LIABILITY" -> "违约责任";
            case "DISPUTE_RESOLUTION" -> "争议解决";
            case "CONFIDENTIALITY" -> "保密条款";
            case "INTELLECTUAL_PROPERTY" -> "知识产权";
            case "PAYMENT_TERMS" -> "付款条款";
            case "TERM_AND_TERMINATION" -> "期限与终止";
            case "FORCE_MAJEURE" -> "不可抗力";
            case "MISCELLANEOUS" -> "其他条款";
            case "SUPPLEMENTARY" -> "附则";
            default -> "其他";
        };
    }

    static class ClauseMatchResult {
        List<ContractClause> added = new ArrayList<>();
        List<ContractClause> removed = new ArrayList<>();
        List<MatchedClause> modified = new ArrayList<>();
    }

    static class MatchedClause {
        ContractClause oldClause;
        ContractClause newClause;
        double similarity;

        MatchedClause(ContractClause oldClause, ContractClause newClause, double similarity) {
            this.oldClause = oldClause;
            this.newClause = newClause;
            this.similarity = similarity;
        }
    }

    static class MatchCandidate {
        int oldIdx;
        int newIdx;
        double similarity;

        MatchCandidate(int oldIdx, int newIdx, double similarity) {
            this.oldIdx = oldIdx;
            this.newIdx = newIdx;
            this.similarity = similarity;
        }
    }
}
