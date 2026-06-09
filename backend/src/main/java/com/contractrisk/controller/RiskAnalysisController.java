package com.contractrisk.controller;

import com.contractrisk.dto.ApiResponse;
import com.contractrisk.dto.BatchAnalysisResult;
import com.contractrisk.entity.RiskItem;
import com.contractrisk.entity.RiskReport;
import com.contractrisk.entity.RiskRule;
import com.contractrisk.entity.enums.RiskLevel;
import com.contractrisk.repository.RiskRuleRepository;
import com.contractrisk.service.RiskAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/risk")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class RiskAnalysisController {

    private final RiskAnalysisService riskAnalysisService;
    private final RiskRuleRepository riskRuleRepository;

    @PostMapping("/analyze/{contractId}")
    public ApiResponse<RiskReport> analyzeContract(@PathVariable Long contractId) {
        try {
            RiskReport report = riskAnalysisService.analyzeContract(contractId);
            return ApiResponse.success("风险分析完成", report);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("风险分析失败", e);
            return ApiResponse.error("风险分析失败: " + e.getMessage());
        }
    }

    @GetMapping("/report/{contractId}")
    public ApiResponse<RiskReport> getRiskReport(@PathVariable Long contractId) {
        Optional<RiskReport> reportOpt = riskAnalysisService.getReportByContractId(contractId);
        return reportOpt
                .map(report -> ApiResponse.success(report))
                .orElseGet(() -> ApiResponse.error("风险报告不存在，请先执行风险分析"));
    }

    @GetMapping("/items/{contractId}")
    public ApiResponse<List<RiskItem>> getRiskItems(
            @PathVariable Long contractId,
            @RequestParam(required = false) RiskLevel level) {

        List<RiskItem> items;
        if (level != null) {
            items = riskAnalysisService.getRiskItemsByContractIdAndLevel(contractId, level);
        } else {
            items = riskAnalysisService.getRiskItemsByContractId(contractId);
        }
        return ApiResponse.success(items);
    }

    @GetMapping("/rules")
    public ApiResponse<List<RiskRule>> getRiskRules() {
        List<RiskRule> rules = riskRuleRepository.findByEnabledTrueOrderByRiskLevelAsc();
        return ApiResponse.success(rules);
    }

    @GetMapping("/rules/{id}")
    public ApiResponse<RiskRule> getRiskRule(@PathVariable Long id) {
        return riskRuleRepository.findById(id)
                .map(rule -> ApiResponse.success(rule))
                .orElseGet(() -> ApiResponse.error("风险规则不存在"));
    }

    @PostMapping("/rules")
    public ApiResponse<RiskRule> createRiskRule(@RequestBody RiskRule rule) {
        rule.setEnabled(true);
        RiskRule saved = riskRuleRepository.save(rule);
        return ApiResponse.success("规则创建成功", saved);
    }

    @PutMapping("/rules/{id}")
    public ApiResponse<RiskRule> updateRiskRule(
            @PathVariable Long id,
            @RequestBody RiskRule rule) {
        return riskRuleRepository.findById(id)
                .map(existing -> {
                    existing.setRuleName(rule.getRuleName());
                    existing.setMatchPattern(rule.getMatchPattern());
                    existing.setKeywords(rule.getKeywords());
                    existing.setRiskLevel(rule.getRiskLevel());
                    existing.setRiskDescription(rule.getRiskDescription());
                    existing.setSuggestion(rule.getSuggestion());
                    existing.setAlternativePhrases(rule.getAlternativePhrases());
                    existing.setRuleCategory(rule.getRuleCategory());
                    existing.setEnabled(rule.isEnabled());
                    existing.setCustomWeight(rule.getCustomWeight());
                    RiskRule saved = riskRuleRepository.save(existing);
                    return ApiResponse.success("规则更新成功", saved);
                })
                .orElseGet(() -> ApiResponse.error("风险规则不存在"));
    }

    @DeleteMapping("/rules/{id}")
    public ApiResponse<Void> deleteRiskRule(@PathVariable Long id) {
        return riskRuleRepository.findById(id)
                .map(rule -> {
                    rule.setEnabled(false);
                    riskRuleRepository.save(rule);
                    return ApiResponse.<Void>success("规则已禁用", null);
                })
                .orElseGet(() -> ApiResponse.error("风险规则不存在"));
    }

    @GetMapping("/stats/categories")
    public ApiResponse<List<String>> getRuleCategories() {
        List<String> categories = riskRuleRepository.findAllCategories();
        return ApiResponse.success(categories);
    }

    @GetMapping("/stats/levels/{contractId}")
    public ApiResponse<Map<String, Long>> getRiskLevelStats(@PathVariable Long contractId) {
        long highCount = riskAnalysisService.getRiskItemsByContractIdAndLevel(contractId, RiskLevel.HIGH).size();
        long mediumCount = riskAnalysisService.getRiskItemsByContractIdAndLevel(contractId, RiskLevel.MEDIUM).size();
        long lowCount = riskAnalysisService.getRiskItemsByContractIdAndLevel(contractId, RiskLevel.LOW).size();

        return ApiResponse.success(Map.of(
                "HIGH", highCount,
                "MEDIUM", mediumCount,
                "LOW", lowCount
        ));
    }

    @PostMapping("/batch-analyze")
    public ApiResponse<Map<String, String>> startBatchAnalysis(@RequestBody Map<String, List<Long>> request) {
        List<Long> contractIds = request.get("contractIds");
        if (contractIds == null || contractIds.isEmpty()) {
            return ApiResponse.error("请选择至少一份合同");
        }
        try {
            String batchId = riskAnalysisService.startBatchAnalysis(contractIds);
            return ApiResponse.success("批量分析已启动", Map.of("batchId", batchId));
        } catch (Exception e) {
            log.error("启动批量分析失败", e);
            return ApiResponse.error("启动批量分析失败: " + e.getMessage());
        }
    }

    @GetMapping("/batch-progress/{batchId}")
    public ApiResponse<BatchAnalysisResult> getBatchProgress(@PathVariable String batchId) {
        try {
            BatchAnalysisResult result = riskAnalysisService.getBatchProgress(batchId);
            return ApiResponse.success(result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }
}
