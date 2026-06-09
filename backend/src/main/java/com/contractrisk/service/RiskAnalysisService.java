package com.contractrisk.service;

import com.contractrisk.config.ContractConfig;
import com.contractrisk.dto.BatchAnalysisResult;
import com.contractrisk.entity.Contract;
import com.contractrisk.entity.ContractClause;
import com.contractrisk.entity.ContractVersion;
import com.contractrisk.entity.RiskItem;
import com.contractrisk.entity.RiskReport;
import com.contractrisk.entity.enums.ClauseSection;
import com.contractrisk.entity.enums.RiskLevel;
import com.contractrisk.engine.RiskEngine;
import com.contractrisk.repository.ContractClauseRepository;
import com.contractrisk.repository.ContractVersionRepository;
import com.contractrisk.repository.RiskItemRepository;
import com.contractrisk.repository.RiskReportRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskAnalysisService {

    private final RiskEngine riskEngine;
    private final RiskItemRepository riskItemRepository;
    private final RiskReportRepository riskReportRepository;
    private final ContractClauseRepository clauseRepository;
    private final ContractVersionRepository versionRepository;
    private final ContractService contractService;
    private final ContractConfig contractConfig;
    private final ObjectMapper objectMapper;

    @Transactional
    public RiskReport analyzeContract(Long contractId) {
        Contract contract = contractService.getContractById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("合同不存在: " + contractId));

        List<ContractVersion> currentVersions = versionRepository.findByContractIdAndCurrentTrue(contractId);
        if (!currentVersions.isEmpty()) {
            Long versionId = currentVersions.get(0).getId();
            return analyzeByVersion(contractId, versionId);
        }

        List<ContractClause> clauses = clauseRepository.findByContractIdOrderBySortOrderAsc(contractId);
        contract.setClauses(clauses);

        if (riskReportRepository.existsByContractId(contractId)) {
            riskReportRepository.deleteByContractId(contractId);
        }
        riskItemRepository.deleteByClauseContractId(contractId);

        List<RiskItem> risks = riskEngine.analyzeContract(contract);

        ContractConfig.Risk riskConfig = contractConfig.getRisk();
        for (RiskItem risk : risks) {
            int weight = riskEngine.calculatePenaltyScore(
                    List.of(risk), riskConfig.getHighPenalty(),
                    riskConfig.getMediumPenalty(), riskConfig.getLowPenalty()
            );
            risk.setPenaltyScore(weight);
        }

        riskItemRepository.saveAll(risks);

        for (ContractClause clause : contract.getClauses()) {
            clause.setRiskCount((int) risks.stream()
                    .filter(r -> r.getClause().getId().equals(clause.getId())).count());
            clause.setHighRisk(risks.stream()
                    .filter(r -> r.getClause().getId().equals(clause.getId()))
                    .anyMatch(r -> r.getRiskLevel() == RiskLevel.HIGH));
        }

        RiskReport report = generateReport(contract, risks);
        report = riskReportRepository.save(report);

        log.info("风险分析完成，合同ID: {}, 风险项: {}, 风险评分: {}",
                contractId, risks.size(), report.getRiskScore());

        return report;
    }

    private RiskReport generateReport(Contract contract, List<RiskItem> risks) {
        ContractConfig.Risk riskConfig = contractConfig.getRisk();

        int highCount = (int) risks.stream().filter(r -> r.getRiskLevel() == RiskLevel.HIGH).count();
        int mediumCount = (int) risks.stream().filter(r -> r.getRiskLevel() == RiskLevel.MEDIUM).count();
        int lowCount = (int) risks.stream().filter(r -> r.getRiskLevel() == RiskLevel.LOW).count();

        int penaltyScore = riskEngine.calculatePenaltyScore(
                risks, riskConfig.getHighPenalty(),
                riskConfig.getMediumPenalty(), riskConfig.getLowPenalty()
        );

        int score = Math.max(0, 100 - penaltyScore);

        RiskReport report = new RiskReport();
        report.setContract(contract);
        report.setRiskScore(BigDecimal.valueOf(score));
        report.setHighRiskCount(highCount);
        report.setMediumRiskCount(mediumCount);
        report.setLowRiskCount(lowCount);
        report.setTotalRiskCount(risks.size());
        report.setRecommendedReject(score < riskConfig.getRejectThreshold());

        Map<String, Integer> sectionDistribution = calculateSectionDistribution(contract);
        try {
            report.setSectionRiskDistribution(objectMapper.writeValueAsString(sectionDistribution));
        } catch (JsonProcessingException e) {
            report.setSectionRiskDistribution("{}");
        }

        report.setSummary(generateSummary(risks, score, sectionDistribution));
        report.setRecommendation(generateRecommendation(score, highCount, mediumCount));

        return report;
    }

    private Map<String, Integer> calculateSectionDistribution(Contract contract) {
        Map<String, Integer> distribution = new LinkedHashMap<>();
        for (ClauseSection section : ClauseSection.values()) {
            long count = contract.getClauses().stream()
                    .filter(c -> c.getSectionType() == section && c.getRiskCount() > 0)
                    .count();
            if (count > 0) {
                distribution.put(section.name(), (int) count);
            }
        }
        return distribution;
    }

    private String generateSummary(List<RiskItem> risks, int score, Map<String, Integer> sectionDistribution) {
        StringBuilder sb = new StringBuilder();
        sb.append("本次合同风险审查共发现").append(risks.size()).append("个风险点。");
        sb.append("综合风险评分为").append(score).append("分（满分100分）。");

        long highCount = risks.stream().filter(r -> r.getRiskLevel() == RiskLevel.HIGH).count();
        long mediumCount = risks.stream().filter(r -> r.getRiskLevel() == RiskLevel.MEDIUM).count();
        long lowCount = risks.stream().filter(r -> r.getRiskLevel() == RiskLevel.LOW).count();

        sb.append("其中高风险").append(highCount).append("个，");
        sb.append("中风险").append(mediumCount).append("个，");
        sb.append("低风险").append(lowCount).append("个。");

        if (!sectionDistribution.isEmpty()) {
            sb.append("风险主要分布在以下章节：");
            sectionDistribution.entrySet().stream()
                    .limit(5)
                    .forEach(e -> sb.append(translateSection(e.getKey())).append("(").append(e.getValue()).append(")、"));
            sb.setLength(sb.length() - 1);
            sb.append("。");
        }

        return sb.toString();
    }

    private String generateRecommendation(int score, int highCount, int mediumCount) {
        StringBuilder sb = new StringBuilder();

        if (score < 60) {
            sb.append("【不建议签署】");
            sb.append("合同综合风险评分低于60分，存在重大法律风险，建议拒签或要求对方进行重大修改。");
        } else if (score < 75) {
            sb.append("【谨慎签署】");
            sb.append("合同存在较多风险点，建议与对方协商修改主要风险条款后再考虑签署。");
        } else if (score < 90) {
            sb.append("【可签署但需注意】");
            sb.append("合同整体风险可控，但仍存在一些需要关注的条款，建议签署前与法务确认。");
        } else {
            sb.append("【建议签署】");
            sb.append("合同风险较低，条款较为公平合理，可以正常签署。");
        }

        if (highCount > 0) {
            sb.append("高风险条款需要重点关注，必须与对方协商修改。");
        }
        if (mediumCount > 0) {
            sb.append("中风险条款建议尽可能优化，降低潜在风险。");
        }

        return sb.toString();
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

    public Optional<RiskReport> getReportByContractId(Long contractId) {
        return riskReportRepository.findTopByContractIdOrderByIdDesc(contractId);
    }

    public Optional<RiskReport> getReportByVersionId(Long versionId) {
        return riskReportRepository.findByVersionId(versionId);
    }

    @Transactional
    public RiskReport analyzeByVersion(Long contractId, Long versionId) {
        Contract contract = contractService.getContractById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("合同不存在: " + contractId));
        ContractVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("版本不存在: " + versionId));

        List<ContractClause> clauses = clauseRepository.findByVersionId(versionId);
        contract.setClauses(clauses);

        List<RiskItem> existingRisks = riskItemRepository.findByContractId(contractId).stream()
                .filter(r -> r.getClause() != null && r.getClause().getVersion() != null
                        && r.getClause().getVersion().getId().equals(versionId))
                .collect(java.util.stream.Collectors.toList());
        riskItemRepository.deleteAll(existingRisks);

        List<RiskItem> risks = riskEngine.analyzeContract(contract);

        ContractConfig.Risk riskConfig = contractConfig.getRisk();
        for (RiskItem risk : risks) {
            int weight = riskEngine.calculatePenaltyScore(
                    List.of(risk), riskConfig.getHighPenalty(),
                    riskConfig.getMediumPenalty(), riskConfig.getLowPenalty()
            );
            risk.setPenaltyScore(weight);
        }

        riskItemRepository.saveAll(risks);

        for (ContractClause clause : clauses) {
            clause.setRiskCount((int) risks.stream()
                    .filter(r -> r.getClause().getId().equals(clause.getId())).count());
            clause.setHighRisk(risks.stream()
                    .filter(r -> r.getClause().getId().equals(clause.getId()))
                    .anyMatch(r -> r.getRiskLevel() == RiskLevel.HIGH));
        }
        clauseRepository.saveAll(clauses);

        Optional<RiskReport> existingReport = riskReportRepository.findByVersionId(versionId);
        existingReport.ifPresent(riskReportRepository::delete);

        RiskReport report = generateReport(contract, risks);
        report.setVersion(version);
        report = riskReportRepository.save(report);

        log.info("版本风险分析完成，合同ID: {}, 版本ID: {}, 风险项: {}, 风险评分: {}",
                contractId, versionId, risks.size(), report.getRiskScore());

        return report;
    }

    public List<RiskItem> getRiskItemsByContractId(Long contractId) {
        return riskItemRepository.findByContractId(contractId);
    }

    public List<RiskItem> getRiskItemsByContractIdAndLevel(Long contractId, RiskLevel level) {
        return riskItemRepository.findByContractIdAndRiskLevel(contractId, level);
    }

    private final Map<String, BatchAnalysisResult> batchProgressMap = new java.util.concurrent.ConcurrentHashMap<>();

    public String startBatchAnalysis(List<Long> contractIds) {
        String batchId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        BatchAnalysisResult result = new BatchAnalysisResult();
        result.setBatchId(batchId);
        result.setTotal(contractIds.size());
        result.setCompleted(0);
        result.setFinished(false);
        result.setRankings(new ArrayList<>());
        batchProgressMap.put(batchId, result);

        executeBatchAsync(batchId, contractIds);

        return batchId;
    }

    @Async
    public void executeBatchAsync(String batchId, List<Long> contractIds) {
        BatchAnalysisResult result = batchProgressMap.get(batchId);
        List<BatchAnalysisResult.ContractRiskRanking> rankings = Collections.synchronizedList(new ArrayList<>());

        for (Long contractId : contractIds) {
            try {
                RiskReport report = analyzeContract(contractId);
                Contract contract = contractService.getContractById(contractId).orElse(null);

                BatchAnalysisResult.ContractRiskRanking ranking = new BatchAnalysisResult.ContractRiskRanking();
                ranking.setContractId(contractId);
                ranking.setContractTitle(contract != null ? contract.getTitle() : "未知");
                ranking.setRiskScore(report.getRiskScore());
                ranking.setHighRiskCount(report.getHighRiskCount());
                ranking.setMediumRiskCount(report.getMediumRiskCount());
                ranking.setLowRiskCount(report.getLowRiskCount());
                ranking.setTotalRiskCount(report.getTotalRiskCount());
                rankings.add(ranking);
            } catch (Exception e) {
                log.error("批量分析 - 合同ID {} 分析失败: {}", contractId, e.getMessage());
                BatchAnalysisResult.ContractRiskRanking ranking = new BatchAnalysisResult.ContractRiskRanking();
                ranking.setContractId(contractId);
                ranking.setContractTitle("分析失败");
                ranking.setRiskScore(BigDecimal.ZERO);
                rankings.add(ranking);
            }

            result.setCompleted(result.getCompleted() + 1);
        }

        rankings.sort((a, b) -> b.getRiskScore().compareTo(a.getRiskScore()));
        for (int i = 0; i < rankings.size(); i++) {
            rankings.get(i).setRank(i + 1);
        }

        result.setRankings(rankings);
        result.setFinished(true);
    }

    public BatchAnalysisResult getBatchProgress(String batchId) {
        BatchAnalysisResult result = batchProgressMap.get(batchId);
        if (result == null) {
            throw new IllegalArgumentException("批量任务不存在: " + batchId);
        }
        return result;
    }
}
