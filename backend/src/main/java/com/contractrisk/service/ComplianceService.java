package com.contractrisk.service;

import com.contractrisk.config.ContractConfig;
import com.contractrisk.entity.ComplianceTemplate;
import com.contractrisk.entity.Contract;
import com.contractrisk.entity.ContractClause;
import com.contractrisk.entity.RiskReport;
import com.contractrisk.entity.enums.ContractType;
import com.contractrisk.repository.ComplianceTemplateRepository;
import com.contractrisk.repository.RiskReportRepository;
import com.contractrisk.util.AmountUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComplianceService implements CommandLineRunner {

    private final ComplianceTemplateRepository templateRepository;
    private final RiskReportRepository riskReportRepository;
    private final ContractConfig contractConfig;
    private final ContractService contractService;

    @Override
    public void run(String... args) {
        if (templateRepository.count() == 0) {
            initializeDefaultTemplates();
        }
    }

    private void initializeDefaultTemplates() {
        log.info("初始化默认合规模板...");

        ComplianceTemplate purchaseTemplate = new ComplianceTemplate();
        purchaseTemplate.setName("采购合同标准模板");
        purchaseTemplate.setContractType(ContractType.PURCHASE);
        purchaseTemplate.setDescription("适用于物资采购、设备采购等采购类合同");
        purchaseTemplate.setRequiredClauses("标的物条款,质量标准条款,交付条款,验收条款,付款条款,违约责任条款,争议解决条款,保密条款,知识产权条款,不可抗力条款");
        purchaseTemplate.setForbiddenClauses("无限连带责任条款,单方任意解除权条款,管辖地仅约定对方所在地条款,自动续约无通知条款");
        purchaseTemplate.setMinAmount(BigDecimal.ZERO);
        purchaseTemplate.setMaxAmount(new BigDecimal("100000000"));
        purchaseTemplate.setActive(true);
        purchaseTemplate.setCreatedBy("system");
        templateRepository.save(purchaseTemplate);

        ComplianceTemplate leaseTemplate = new ComplianceTemplate();
        leaseTemplate.setName("租赁合同标准模板");
        leaseTemplate.setContractType(ContractType.LEASE);
        leaseTemplate.setDescription("适用于房屋租赁、设备租赁等租赁类合同");
        leaseTemplate.setRequiredClauses("租赁物条款,租赁期限条款,租金条款,押金条款,维修保养条款,违约责任条款,争议解决条款,保险条款,不可抗力条款");
        leaseTemplate.setForbiddenClauses("无限连带责任条款,单方随时解除权条款,管辖地仅约定对方所在地条款");
        leaseTemplate.setMinAmount(BigDecimal.ZERO);
        leaseTemplate.setMaxAmount(new BigDecimal("50000000"));
        leaseTemplate.setActive(true);
        leaseTemplate.setCreatedBy("system");
        templateRepository.save(leaseTemplate);

        ComplianceTemplate laborTemplate = new ComplianceTemplate();
        laborTemplate.setName("劳动合同标准模板");
        laborTemplate.setContractType(ContractType.LABOR);
        laborTemplate.setDescription("适用于企业与员工签订的劳动合同");
        laborTemplate.setRequiredClauses("工作内容条款,工作地点条款,工作时间条款,劳动报酬条款,社会保险条款,劳动保护条款,合同期限条款,试用期条款,竞业限制条款(如有),保密条款");
        laborTemplate.setForbiddenClauses("超过2年的竞业限制条款,低于当地最低工资标准的工资条款,不缴纳社会保险条款,无限期保密条款");
        laborTemplate.setMinAmount(BigDecimal.ZERO);
        laborTemplate.setMaxAmount(new BigDecimal("10000000"));
        laborTemplate.setActive(true);
        laborTemplate.setCreatedBy("system");
        templateRepository.save(laborTemplate);

        log.info("默认合规模板初始化完成，共3个模板");
    }

    @Transactional
    public Map<String, Object> checkCompliance(Long contractId) {
        Contract contract = contractService.getContractById(contractId)
                .orElseThrow(() -> new IllegalArgumentException("合同不存在: " + contractId));

        Optional<ComplianceTemplate> templateOpt = templateRepository
                .findByContractTypeAndActiveTrueOrderByCreatedAtDesc(contract.getContractType());

        if (templateOpt.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("compliant", true);
            result.put("message", "未找到对应合同类型的合规模板，跳过合规检查");
            result.put("missingRequired", Collections.emptyList());
            result.put("forbiddenClauses", Collections.emptyList());
            result.put("amountViolations", Collections.emptyList());
            result.put("penaltyScore", 0);
            return result;
        }

        ComplianceTemplate template = templateOpt.get();
        Map<String, Object> result = new HashMap<>();

        List<String> missingRequired = checkRequiredClauses(contract, template);
        List<Map<String, Object>> forbiddenClauses = checkForbiddenClauses(contract, template);
        List<Map<String, Object>> amountViolations = checkAmountRange(contract, template);

        int penaltyScore = 0;
        boolean compliant = true;
        String complianceStatus = "COMPLIANT";

        if (!missingRequired.isEmpty()) {
            penaltyScore += contractConfig.getRisk().getMissingRequiredPenalty() * missingRequired.size();
            compliant = false;
            complianceStatus = "NON_COMPLIANT";
        }

        if (!forbiddenClauses.isEmpty()) {
            penaltyScore += contractConfig.getRisk().getForbiddenClausePenalty() * forbiddenClauses.size();
            compliant = false;
            complianceStatus = "SERIOUS_NON_COMPLIANT";
        }

        if (!amountViolations.isEmpty()) {
            compliant = false;
            if ("COMPLIANT".equals(complianceStatus)) {
                complianceStatus = "NON_COMPLIANT";
            }
        }

        result.put("compliant", compliant);
        result.put("complianceStatus", complianceStatus);
        result.put("templateName", template.getName());
        result.put("templateId", template.getId());
        result.put("missingRequired", missingRequired);
        result.put("forbiddenClauses", forbiddenClauses);
        result.put("amountViolations", amountViolations);
        result.put("missingRequiredCount", missingRequired.size());
        result.put("forbiddenClauseCount", forbiddenClauses.size());
        result.put("amountViolationCount", amountViolations.size());
        result.put("penaltyScore", penaltyScore);

        updateRiskReportWithCompliance(contractId, result);

        return result;
    }

    private List<String> checkRequiredClauses(Contract contract, ComplianceTemplate template) {
        List<String> missing = new ArrayList<>();
        if (template.getRequiredClauses() == null || template.getRequiredClauses().isEmpty()) {
            return missing;
        }

        String[] requiredArray = template.getRequiredClauses().split("[,，、]");
        String fullText = contract.getFullText();

        for (String required : requiredArray) {
            String trimmed = required.trim();
            if (trimmed.isEmpty()) continue;

            boolean found = false;
            String[] keywords = trimmed.split("[或或者]");
            for (String keyword : keywords) {
                if (fullText.contains(keyword.trim())) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                missing.add(trimmed);
            }
        }

        return missing;
    }

    private List<Map<String, Object>> checkForbiddenClauses(Contract contract, ComplianceTemplate template) {
        List<Map<String, Object>> found = new ArrayList<>();
        if (template.getForbiddenClauses() == null || template.getForbiddenClauses().isEmpty()) {
            return found;
        }

        String[] forbiddenArray = template.getForbiddenClauses().split("[,，、]");

        for (ContractClause clause : contract.getClauses()) {
            for (String forbidden : forbiddenArray) {
                String trimmed = forbidden.trim();
                if (trimmed.isEmpty()) continue;

                if (clause.getContent().contains(trimmed) ||
                        (clause.getTitle() != null && clause.getTitle().contains(trimmed))) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("clauseId", clause.getId());
                    item.put("clauseNumber", clause.getClauseNumber());
                    item.put("clauseTitle", clause.getTitle());
                    item.put("forbiddenTerm", trimmed);
                    item.put("content", clause.getContent().length() > 200 ?
                            clause.getContent().substring(0, 200) + "..." : clause.getContent());
                    found.add(item);
                }
            }
        }

        return found;
    }

    private List<Map<String, Object>> checkAmountRange(Contract contract, ComplianceTemplate template) {
        List<Map<String, Object>> violations = new ArrayList<>();

        if (template.getMinAmount() == null && template.getMaxAmount() == null) {
            return violations;
        }

        if (contract.getTotalAmount() == null) {
            BigDecimal amount = AmountUtil.parseAmount(contract.getFullText());
            if (amount == null) {
                return violations;
            }
            contract.setTotalAmount(amount);
        }

        BigDecimal amount = contract.getTotalAmount();

        if (template.getMinAmount() != null && amount.compareTo(template.getMinAmount()) < 0) {
            Map<String, Object> violation = new HashMap<>();
            violation.put("type", "BELOW_MIN");
            violation.put("amount", amount);
            violation.put("minAmount", template.getMinAmount());
            violation.put("message", "合同金额低于模板规定的最低金额");
            violations.add(violation);
        }

        if (template.getMaxAmount() != null && amount.compareTo(template.getMaxAmount()) > 0) {
            Map<String, Object> violation = new HashMap<>();
            violation.put("type", "ABOVE_MAX");
            violation.put("amount", amount);
            violation.put("maxAmount", template.getMaxAmount());
            violation.put("message", "合同金额超出模板规定的最高金额");
            violations.add(violation);
        }

        return violations;
    }

    private void updateRiskReportWithCompliance(Long contractId, Map<String, Object> complianceResult) {
        Optional<RiskReport> reportOpt = riskReportRepository.findByContractId(contractId);
        if (reportOpt.isPresent()) {
            RiskReport report = reportOpt.get();
            report.setComplianceStatus((String) complianceResult.get("complianceStatus"));
            report.setMissingRequiredCount((Integer) complianceResult.get("missingRequiredCount"));
            report.setForbiddenClauseCount((Integer) complianceResult.get("forbiddenClauseCount"));
            report.setAmountViolationCount((Integer) complianceResult.get("amountViolationCount"));

            int penaltyScore = (Integer) complianceResult.get("penaltyScore");
            BigDecimal currentScore = report.getRiskScore();
            BigDecimal newScore = BigDecimal.valueOf(Math.max(0, currentScore.intValue() - penaltyScore));
            report.setRiskScore(newScore);
            report.setRecommendedReject(newScore.intValue() < contractConfig.getRisk().getRejectThreshold());

            riskReportRepository.save(report);
        }
    }

    public List<ComplianceTemplate> getAllTemplates() {
        return templateRepository.findByActiveTrue();
    }

    public List<ComplianceTemplate> getTemplatesByType(ContractType type) {
        return templateRepository.findByContractTypeAndActiveTrue(type);
    }

    public Optional<ComplianceTemplate> getTemplateById(Long id) {
        return templateRepository.findById(id);
    }

    @Transactional
    public ComplianceTemplate createTemplate(ComplianceTemplate template) {
        template.setActive(true);
        return templateRepository.save(template);
    }

    @Transactional
    public boolean deleteTemplate(Long id) {
        return templateRepository.findById(id)
                .map(t -> {
                    t.setActive(false);
                    templateRepository.save(t);
                    return true;
                })
                .orElse(false);
    }
}
