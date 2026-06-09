package com.contractrisk.engine;

import com.contractrisk.entity.Contract;
import com.contractrisk.entity.ContractClause;
import com.contractrisk.entity.RiskItem;
import com.contractrisk.entity.RiskRule;
import com.contractrisk.entity.enums.RiskLevel;
import com.contractrisk.repository.RiskRuleRepository;
import com.contractrisk.util.AmountUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class RiskEngine {

    private final RiskRuleRepository riskRuleRepository;
    private final NLPRiskAnalyzer nlpRiskAnalyzer;

    public List<RiskItem> analyzeContract(Contract contract) {
        List<RiskItem> allRisks = new ArrayList<>();
        List<RiskRule> rules = riskRuleRepository.findByEnabledTrueOrderByRiskLevelAsc();

        for (ContractClause clause : contract.getClauses()) {
            List<RiskItem> clauseRisks = analyzeClause(clause, rules, contract);
            allRisks.addAll(clauseRisks);

            List<RiskItem> nlpRisks = nlpRiskAnalyzer.analyzeNlpRisks(clause, contract);
            allRisks.addAll(nlpRisks);

            updateClauseStats(clause, clauseRisks.size() + nlpRisks.size());
        }

        return allRisks;
    }

    private List<RiskItem> analyzeClause(ContractClause clause, List<RiskRule> rules, Contract contract) {
        List<RiskItem> risks = new ArrayList<>();
        String clauseText = clause.getContent();

        for (RiskRule rule : rules) {
            if (matchesRule(clauseText, rule, contract)) {
                RiskItem risk = createRiskItem(clause, rule, clauseText);
                if (risk != null) {
                    risks.add(risk);
                }
            }
        }

        return risks;
    }

    private boolean matchesRule(String text, RiskRule rule, Contract contract) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        boolean regexMatch = false;
        if (rule.getMatchPattern() != null && !rule.getMatchPattern().isEmpty()) {
            try {
                Pattern pattern = Pattern.compile(rule.getMatchPattern(),
                        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
                Matcher matcher = pattern.matcher(text);
                regexMatch = matcher.find();
            } catch (Exception e) {
                log.warn("正则表达式匹配失败: {}, 规则: {}", e.getMessage(), rule.getRuleName());
            }
        }

        boolean keywordMatch = true;
        if (rule.getKeywords() != null && !rule.getKeywords().isEmpty()) {
            String[] keywords = rule.getKeywords().split("[,，、]");
            keywordMatch = false;
            for (String keyword : keywords) {
                if (keyword != null && !keyword.trim().isEmpty() &&
                        text.toLowerCase().contains(keyword.trim().toLowerCase())) {
                    keywordMatch = true;
                    break;
                }
            }
        }

        if (regexMatch && keywordMatch) {
            return checkContextualConditions(text, rule, contract);
        }

        return false;
    }

    private boolean checkContextualConditions(String text, RiskRule rule, Contract contract) {
        String ruleName = rule.getRuleName();

        if ("违约金比例过高".equals(ruleName) && contract.getTotalAmount() != null) {
            BigDecimal penaltyAmount = AmountUtil.parseAmount(text);
            if (penaltyAmount != null) {
                return AmountUtil.exceedsPercentage(penaltyAmount, contract.getTotalAmount(), 30);
            }
        }

        if ("竞业限制超过法定期限".equals(ruleName)) {
            Pattern durationPattern = Pattern.compile("(\\d+)(?:年|个月)");
            Matcher matcher = durationPattern.matcher(text);
            while (matcher.find()) {
                try {
                    int value = Integer.parseInt(matcher.group(1));
                    String unit = matcher.group().replaceAll("\\d+", "");
                    if ("年".equals(unit) && value > 2) {
                        return true;
                    } else if ("个月".equals(unit) && value > 24) {
                        return true;
                    }
                } catch (Exception ignored) {
                }
            }
        }

        return true;
    }

    private RiskItem createRiskItem(ContractClause clause, RiskRule rule, String clauseText) {
        String matchedText = extractMatchedText(clauseText, rule.getMatchPattern());

        RiskItem risk = new RiskItem();
        risk.setClause(clause);
        risk.setRiskRule(rule);
        risk.setRuleName(rule.getRuleName());
        risk.setRiskLevel(rule.getRiskLevel());
        risk.setRiskDescription(rule.getRiskDescription());
        risk.setMatchedText(matchedText);
        risk.setOriginalText(extractContext(clauseText, matchedText));
        risk.setSuggestion(rule.getSuggestion());
        risk.setAlternativePhrases(rule.getAlternativePhrases());
        risk.setFromNlp(false);

        if (matchedText != null) {
            risk.setMatchPosition(clauseText.indexOf(matchedText));
            risk.setMatchLength(matchedText.length());
        }

        return risk;
    }

    private String extractMatchedText(String text, String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return null;
        }
        try {
            Pattern p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            Matcher m = p.matcher(text);
            if (m.find()) {
                return m.group();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String extractContext(String fullText, String matchedText) {
        if (matchedText == null || matchedText.isEmpty()) {
            return fullText.length() > 500 ? fullText.substring(0, 500) + "..." : fullText;
        }

        int pos = fullText.indexOf(matchedText);
        if (pos < 0) {
            return fullText.length() > 500 ? fullText.substring(0, 500) + "..." : fullText;
        }

        int start = Math.max(0, pos - 50);
        int end = Math.min(fullText.length(), pos + matchedText.length() + 50);
        return fullText.substring(start, end);
    }

    private void updateClauseStats(ContractClause clause, int riskCount) {
        clause.setRiskCount(riskCount);
        clause.setHighRisk(clause.getRiskItems().stream()
                .anyMatch(r -> r.getRiskLevel() == RiskLevel.HIGH));
    }

    public int calculatePenaltyScore(List<RiskItem> risks, int highPenalty, int mediumPenalty, int lowPenalty) {
        int score = 0;
        for (RiskItem risk : risks) {
            switch (risk.getRiskLevel()) {
                case HIGH -> score += highPenalty;
                case MEDIUM -> score += mediumPenalty;
                case LOW -> score += lowPenalty;
            }
        }
        return score;
    }
}
