package com.contractrisk.service;

import com.contractrisk.entity.Contract;
import com.contractrisk.entity.ContractClause;
import com.contractrisk.entity.RiskItem;
import com.contractrisk.entity.enums.ChangeType;
import com.contractrisk.entity.enums.RiskLevel;
import com.contractrisk.repository.ContractRepository;
import com.contractrisk.repository.RiskItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContractComparisonService {

    private final ContractRepository contractRepository;
    private final RiskItemRepository riskItemRepository;
    private final LevenshteinDistance levenshteinDistance = new LevenshteinDistance();

    @Transactional(readOnly = true)
    public Map<String, Object> compareContracts(Long contractId1, Long contractId2) {
        Contract contract1 = contractRepository.findByIdAndDeletedFalse(contractId1)
                .orElseThrow(() -> new IllegalArgumentException("合同不存在: " + contractId1));
        Contract contract2 = contractRepository.findByIdAndDeletedFalse(contractId2)
                .orElseThrow(() -> new IllegalArgumentException("合同不存在: " + contractId2));

        Map<String, Object> result = new HashMap<>();
        result.put("contract1", buildContractInfo(contract1));
        result.put("contract2", buildContractInfo(contract2));

        List<Map<String, Object>> clauseDiffs = compareClauses(
                contract1.getClauses(),
                contract2.getClauses()
        );
        result.put("clauseDiffs", clauseDiffs);

        Map<String, Object> summary = buildComparisonSummary(clauseDiffs, contract1, contract2);
        result.put("summary", summary);

        List<Map<String, Object>> newRisks = identifyNewRisks(contractId1, contractId2, clauseDiffs);
        result.put("newRisksIntroduced", newRisks);
        result.put("newRisksCount", newRisks.size());

        log.info("合同对比完成，合同1: {}, 合同2: {}, 差异条款数: {}",
                contractId1, contractId2, clauseDiffs.size());

        return result;
    }

    private Map<String, Object> buildContractInfo(Contract contract) {
        Map<String, Object> info = new HashMap<>();
        info.put("id", contract.getId());
        info.put("title", contract.getTitle());
        info.put("contractType", contract.getContractType());
        info.put("partyA", contract.getPartyA());
        info.put("partyB", contract.getPartyB());
        info.put("totalAmount", contract.getTotalAmount());
        info.put("clauseCount", contract.getClauses().size());
        info.put("createdAt", contract.getCreatedAt());
        return info;
    }

    private List<Map<String, Object>> compareClauses(List<ContractClause> clauses1, List<ContractClause> clauses2) {
        List<Map<String, Object>> diffs = new ArrayList<>();

        Map<String, ContractClause> clauseMap1 = clauses1.stream()
                .collect(Collectors.toMap(
                        c -> c.getClauseNumber() != null ? c.getClauseNumber() : c.getTitle(),
                        c -> c,
                        (a, b) -> a
                ));

        Map<String, ContractClause> clauseMap2 = clauses2.stream()
                .collect(Collectors.toMap(
                        c -> c.getClauseNumber() != null ? c.getClauseNumber() : c.getTitle(),
                        c -> c,
                        (a, b) -> a
                ));

        for (Map.Entry<String, ContractClause> entry : clauseMap2.entrySet()) {
            String key = entry.getKey();
            ContractClause clause2 = entry.getValue();
            ContractClause clause1 = clauseMap1.get(key);

            if (clause1 == null) {
                diffs.add(buildDiffEntry(ChangeType.ADDED, null, clause2));
            } else {
                if (!areClausesEqual(clause1, clause2)) {
                    diffs.add(buildDiffEntry(ChangeType.MODIFIED, clause1, clause2));
                }
                clauseMap1.remove(key);
            }
        }

        for (ContractClause clause1 : clauseMap1.values()) {
            diffs.add(buildDiffEntry(ChangeType.REMOVED, clause1, null));
        }

        diffs.sort((a, b) -> {
            Integer o1 = (Integer) a.get("sortOrder");
            Integer o2 = (Integer) b.get("sortOrder");
            if (o1 == null) o1 = Integer.MAX_VALUE;
            if (o2 == null) o2 = Integer.MAX_VALUE;
            return o1.compareTo(o2);
        });

        return diffs;
    }

    private boolean areClausesEqual(ContractClause c1, ContractClause c2) {
        if (c1.getContent() == null && c2.getContent() == null) return true;
        if (c1.getContent() == null || c2.getContent() == null) return false;
        return c1.getContent().equals(c2.getContent());
    }

    private Map<String, Object> buildDiffEntry(ChangeType changeType, ContractClause oldClause, ContractClause newClause) {
        Map<String, Object> diff = new HashMap<>();
        diff.put("changeType", changeType.name());

        ContractClause reference = newClause != null ? newClause : oldClause;
        diff.put("clauseNumber", reference.getClauseNumber());
        diff.put("clauseTitle", reference.getTitle());
        diff.put("sectionType", reference.getSectionType());
        diff.put("sortOrder", reference.getSortOrder());

        if (oldClause != null) {
            diff.put("oldContent", oldClause.getContent());
            diff.put("oldRiskCount", oldClause.getRiskCount());
            diff.put("oldIsHighRisk", oldClause.isHighRisk());
        }

        if (newClause != null) {
            diff.put("newContent", newClause.getContent());
            diff.put("newRiskCount", newClause.getRiskCount());
            diff.put("newIsHighRisk", newClause.isHighRisk());
        }

        if (changeType == ChangeType.MODIFIED && oldClause != null && newClause != null) {
            double similarity = calculateSimilarity(oldClause.getContent(), newClause.getContent());
            diff.put("similarity", similarity);
            diff.put("hasRiskIncrease", newClause.getRiskCount() > oldClause.getRiskCount() ||
                    (newClause.isHighRisk() && !oldClause.isHighRisk()));
        }

        return diff;
    }

    private double calculateSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0;
        if (s1.equals(s2)) return 1.0;

        int maxLength = Math.max(s1.length(), s2.length());
        if (maxLength == 0) return 1.0;

        int distance = levenshteinDistance.apply(s1, s2);
        return 1.0 - ((double) distance / maxLength);
    }

    private Map<String, Object> buildComparisonSummary(List<Map<String, Object>> diffs,
                                                        Contract c1, Contract c2) {
        Map<String, Object> summary = new HashMap<>();

        int added = 0, removed = 0, modified = 0;
        int increasedRisk = 0;

        for (Map<String, Object> diff : diffs) {
            String changeType = (String) diff.get("changeType");
            switch (ChangeType.valueOf(changeType)) {
                case ADDED -> added++;
                case REMOVED -> removed++;
                case MODIFIED -> {
                    modified++;
                    Boolean hasRiskIncrease = (Boolean) diff.get("hasRiskIncrease");
                    if (hasRiskIncrease != null && hasRiskIncrease) {
                        increasedRisk++;
                    }
                }
                default -> { }
            }
        }

        summary.put("totalDifferences", diffs.size());
        summary.put("addedClauses", added);
        summary.put("removedClauses", removed);
        summary.put("modifiedClauses", modified);
        summary.put("clausesWithIncreasedRisk", increasedRisk);

        summary.put("amountChange", c1.getTotalAmount() != null && c2.getTotalAmount() != null ?
                c2.getTotalAmount().subtract(c1.getTotalAmount()) : null);
        summary.put("amountChangePercentage", c1.getTotalAmount() != null && c2.getTotalAmount() != null &&
                c1.getTotalAmount().compareTo(java.math.BigDecimal.ZERO) != 0 ?
                c2.getTotalAmount().subtract(c1.getTotalAmount())
                        .multiply(java.math.BigDecimal.valueOf(100))
                        .divide(c1.getTotalAmount(), 2, java.math.RoundingMode.HALF_UP) : null);

        long c1HighRisks = c1.getClauses().stream().filter(ContractClause::isHighRisk).count();
        long c2HighRisks = c2.getClauses().stream().filter(ContractClause::isHighRisk).count();
        summary.put("highRiskChange", (int) (c2HighRisks - c1HighRisks));

        return summary;
    }

    private List<Map<String, Object>> identifyNewRisks(Long contractId1, Long contractId2,
                                                        List<Map<String, Object>> clauseDiffs) {
        List<Map<String, Object>> newRisks = new ArrayList<>();

        List<RiskItem> oldRisks = riskItemRepository.findByContractId(contractId1);
        List<RiskItem> newRisksList = riskItemRepository.findByContractId(contractId2);

        Set<String> oldRiskSignatures = oldRisks.stream()
                .map(r -> r.getRuleName() + "|" + r.getMatchedText())
                .collect(Collectors.toSet());

        for (RiskItem newRisk : newRisksList) {
            String signature = newRisk.getRuleName() + "|" + newRisk.getMatchedText();
            if (!oldRiskSignatures.contains(signature)) {
                Map<String, Object> riskInfo = new HashMap<>();
                riskInfo.put("ruleName", newRisk.getRuleName());
                riskInfo.put("riskLevel", newRisk.getRiskLevel());
                riskInfo.put("riskDescription", newRisk.getRiskDescription());
                riskInfo.put("matchedText", newRisk.getMatchedText());
                riskInfo.put("suggestion", newRisk.getSuggestion());
                riskInfo.put("isFromNlp", newRisk.isFromNlp());
                riskInfo.put("clauseNumber", newRisk.getClause().getClauseNumber());
                riskInfo.put("clauseTitle", newRisk.getClause().getTitle());

                String relatedChangeType = findRelatedChangeType(clauseDiffs, newRisk.getClause().getClauseNumber());
                riskInfo.put("introducedByChange", relatedChangeType);

                newRisks.add(riskInfo);
            }
        }

        newRisks.sort((a, b) -> {
            RiskLevel l1 = (RiskLevel) a.get("riskLevel");
            RiskLevel l2 = (RiskLevel) b.get("riskLevel");
            return Integer.compare(l1.ordinal(), l2.ordinal());
        });

        return newRisks;
    }

    private String findRelatedChangeType(List<Map<String, Object>> diffs, String clauseNumber) {
        if (clauseNumber == null) return "MODIFIED";

        for (Map<String, Object> diff : diffs) {
            String num = (String) diff.get("clauseNumber");
            if (clauseNumber.equals(num)) {
                return (String) diff.get("changeType");
            }
        }
        return "MODIFIED";
    }
}
