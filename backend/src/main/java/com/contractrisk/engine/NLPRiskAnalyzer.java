package com.contractrisk.engine;

import com.contractrisk.entity.Contract;
import com.contractrisk.entity.ContractClause;
import com.contractrisk.entity.RiskItem;
import com.contractrisk.entity.enums.RiskLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class NLPRiskAnalyzer {

    private static final List<NLPRiskPattern> NLP_PATTERNS = new ArrayList<>();

    static {
        NLP_PATTERNS.add(new NLPRiskPattern(
                "单方决定权风险",
                "(甲方|对方).*(?:有权|可以|随时|单方|单方面).*(?:决定|变更|调整|解释).*(?:无需|不须|不必).*(?:乙方|我方|另一方)",
                "潜在的单方决定权风险，对方可能单方面做出对我方不利的决定。",
                RiskLevel.MEDIUM,
                "建议明确约定重大事项需双方协商一致，或增加我方的否决权。",
                "1. 本合同的重大变更需经双方协商一致并签订书面补充协议。\n" +
                        "2. 双方对本合同条款的解释应本着诚实信用原则协商确定。\n" +
                        "3. 一方欲行使本合同项下的权利时，应提前[X]日书面通知对方。"
        ));

        NLP_PATTERNS.add(new NLPRiskPattern(
                "单方保留权利风险",
                "(甲方|对方).*(?:保留.*(?:最终|最终的).*(?:决定权|解释权|权利)).*",
                "对方保留了最终决定权或解释权，合同执行过程中可能存在不确定性。",
                RiskLevel.MEDIUM,
                "建议删除单方保留最终决定权的表述，改为双方协商确定。",
                "1. 本合同条款的解释权归双方共同享有。\n" +
                        "2. 双方对合同履行产生争议时，应首先通过友好协商解决。\n" +
                        "3. 任何一方未行使本合同项下的权利，不构成对该权利的放弃。"
        ));

        NLP_PATTERNS.add(new NLPRiskPattern(
                "不利后果推定",
                "(?:乙方|我方).*如不.*(?:则视为|视为).*(?:同意|认可|接受|放弃)|(?:逾期|未提出异议).*(?:视为).*(?:认可|同意)",
                "存在不利的推定条款，我方未及时回复可能被视为同意对我方不利的事项。",
                RiskLevel.MEDIUM,
                "建议删除不利推定条款，或明确约定双方均适用相同的推定规则。",
                "1. 一方收到对方通知后，应在[X]日内书面回复，逾期未回复的，视为同意。\n" +
                        "2. 双方确认，本合同的任何变更均需以书面形式进行。\n" +
                        "3. 除非双方书面确认，否则任何一方的行为不得视为对本合同的修改或变更。"
        ));

        NLP_PATTERNS.add(new NLPRiskPattern(
                "无上限赔偿责任",
                "赔偿.*(?:全部|所有|一切).*损失(?!.*不超过|以.*为限)|(?:损失|赔偿).*(?:包括但不限于).*(?:利润损失|间接损失|预期利益)",
                "赔偿责任范围过宽且未设定上限，可能导致我方承担不可预见的巨额赔偿。",
                RiskLevel.HIGH,
                "建议对赔偿责任设定上限，并明确排除间接损失和预期利益损失。",
                "1. 一方违约的，其赔偿责任总额不超过合同总金额的[X]%。\n" +
                        "2. 双方确认，违约方不承担对方的间接损失、预期利润损失或商誉损失。\n" +
                        "3. 因一方故意或重大过失造成的损失，不受上述赔偿限额的限制。"
        ));

        NLP_PATTERNS.add(new NLPRiskPattern(
                "不规范表述风险",
                "(大概|可能|尽量|尽可能|视情况|原则上|一般情况下).*(?:不承担|不负责|免除)",
                "条款表述模糊不规范，可能为对方逃避责任留下空间。",
                RiskLevel.LOW,
                "建议将模糊表述修改为明确、具体的责任约定。",
                "1. 双方应按照本合同的约定全面履行各自的义务。\n" +
                        "2. 除本合同明确约定的免责情形外，任何一方不履行合同义务的，应承担违约责任。\n" +
                        "3. 本合同中使用的\"包括但不限于\"等表述仅为举例说明，不影响条款的完整性。"
        ));

        NLP_PATTERNS.add(new NLPRiskPattern(
                "信息不对称风险",
                "(甲方|对方).*(?:有权|可以).*(?:了解|查询|要求提供|检查).*(?:乙方|我方).*(?:无|没有|不享有).*(?:相同|同等|对应)",
                "双方在知情权上不对等，对方可以了解我方信息而我方不能了解对方信息。",
                RiskLevel.LOW,
                "建议约定双方享有对等的知情权和信息获取权。",
                "1. 双方均有权了解对方履行本合同的相关情况。\n" +
                        "2. 一方要求对方提供相关信息的，应提前[X]日书面通知。\n" +
                        "3. 双方均应对在履行合同过程中知悉的对方商业秘密承担保密义务。"
        ));

        NLP_PATTERNS.add(new NLPRiskPattern(
                "过度授权风险",
                "(甲方|对方).*(?:全权负责|全权处理|独家代理|唯一代表).*(?:本合同|相关事宜)",
                "对方被授予过度的全权处理权利，我方可能失去对事务的控制权。",
                RiskLevel.MEDIUM,
                "建议缩小授权范围，增加我方对重大事项的审批权。",
                "1. 一方可以委托代理人处理本合同相关事宜，但应向对方出具授权委托书。\n" +
                        "2. 本合同项下的重大事项需经双方共同确认。\n" +
                        "3. 一方超越授权范围实施的行为，对另一方不发生效力。"
        ));

        NLP_PATTERNS.add(new NLPRiskPattern(
                "不平等终止权",
                "(甲方|对方).*(?:有权|可以).*(?:随时|立即).*(?:终止|解除|中止).*(?:本合同|合作)(?!.*相同|同等|对等)",
                "对方享有随时终止合同的权利，但未约定我方享有同等权利。",
                RiskLevel.HIGH,
                "建议删除单方随时终止权，或约定双方均享有同等的终止权利。",
                "1. 任何一方欲提前终止本合同，应提前[X]日书面通知对方。\n" +
                        "2. 因一方违约导致合同终止的，违约方应承担相应的违约责任。\n" +
                        "3. 合同终止后，双方应按照本合同的约定进行结算。"
        ));
    }

    public List<RiskItem> analyzeNlpRisks(ContractClause clause, Contract contract) {
        List<RiskItem> risks = new ArrayList<>();
        String text = clause.getContent();

        if (text == null || text.trim().isEmpty()) {
            return risks;
        }

        for (NLPRiskPattern pattern : NLP_PATTERNS) {
            if (matchesPattern(text, pattern)) {
                RiskItem risk = createNlpRiskItem(clause, pattern, text);
                if (risk != null) {
                    risks.add(risk);
                }
            }
        }

        risks.addAll(analyzeSentencePatterns(clause, text));

        return risks;
    }

    private boolean matchesPattern(String text, NLPRiskPattern pattern) {
        try {
            Pattern p = Pattern.compile(pattern.getPattern(),
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            Matcher m = p.matcher(text);
            return m.find();
        } catch (Exception e) {
            log.warn("NLP模式匹配失败: {}", e.getMessage());
            return false;
        }
    }

    private List<RiskItem> analyzeSentencePatterns(ContractClause clause, String text) {
        List<RiskItem> risks = new ArrayList<>();

        Pattern unfairPattern = Pattern.compile(
                "(?:甲方|对方).*(?:有权|可以|应当).*(?:乙方|我方).*(?:不得|无权|不应).*",
                Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = unfairPattern.matcher(text);
        if (matcher.find() && !isMutualObligation(text)) {
            RiskItem risk = new RiskItem();
            risk.setClause(clause);
            risk.setRuleName("权利义务不对等（NLP识别）");
            risk.setRiskLevel(RiskLevel.LOW);
            risk.setRiskDescription("条款表述显示双方权利义务可能不对等，建议仔细核对。");
            risk.setMatchedText(matcher.group());
            risk.setOriginalText(matcher.group());
            risk.setSuggestion("建议检查双方权利义务是否对等，如有不对等应调整。");
            risk.setAlternativePhrases(
                    "1. 双方均应按照本合同的约定履行各自的义务。\n" +
                            "2. 双方均享有本合同约定的权利，并承担相应的义务。\n" +
                            "3. 任何一方不履行合同义务的，均应承担违约责任。"
            );
            risk.setFromNlp(true);
            risks.add(risk);
        }

        Pattern absolutePattern = Pattern.compile(
                "(?:绝对|完全|彻底|无条件|任何情况下).*(?:保证|承诺|不承担|不负责)",
                Pattern.CASE_INSENSITIVE
        );
        matcher = absolutePattern.matcher(text);
        if (matcher.find()) {
            RiskItem risk = new RiskItem();
            risk.setClause(clause);
            risk.setRuleName("绝对化表述风险（NLP识别）");
            risk.setRiskLevel(RiskLevel.LOW);
            risk.setRiskDescription("条款使用了绝对化表述，可能存在法律风险或履行困难。");
            risk.setMatchedText(matcher.group());
            risk.setOriginalText(matcher.group());
            risk.setSuggestion("建议将绝对化表述修改为相对、合理的表述。");
            risk.setAlternativePhrases(
                    "1. 方应尽合理努力履行本合同项下的义务。\n" +
                            "2. 除本合同另有约定外，双方的责任以本合同约定为限。\n" +
                            "3. 因不可抗力导致的履行迟延或不能，相关方不承担责任。"
            );
            risk.setFromNlp(true);
            risks.add(risk);
        }

        return risks;
    }

    private boolean isMutualObligation(String text) {
        return text.contains("双方") || text.contains("甲乙双方") ||
                text.contains("各方") || (text.contains("甲方") && text.contains("乙方") &&
                text.indexOf("甲方") < text.indexOf("不得") && text.indexOf("乙方") < text.indexOf("不得"));
    }

    private RiskItem createNlpRiskItem(ContractClause clause, NLPRiskPattern pattern, String text) {
        RiskItem risk = new RiskItem();
        risk.setClause(clause);
        risk.setRuleName(pattern.getName() + "（NLP识别）");
        risk.setRiskLevel(pattern.getLevel());
        risk.setRiskDescription(pattern.getDescription());
        risk.setSuggestion(pattern.getSuggestion());
        risk.setAlternativePhrases(pattern.getAlternatives());
        risk.setFromNlp(true);

        try {
            Pattern p = Pattern.compile(pattern.getPattern(),
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            Matcher m = p.matcher(text);
            if (m.find()) {
                risk.setMatchedText(m.group());
                risk.setMatchPosition(m.start());
                risk.setMatchLength(m.group().length());
            }
        } catch (Exception ignored) {
        }

        risk.setOriginalText(text.length() > 300 ? text.substring(0, 300) + "..." : text);
        return risk;
    }

    private static class NLPRiskPattern {
        private final String name;
        private final String pattern;
        private final String description;
        private final RiskLevel level;
        private final String suggestion;
        private final String alternatives;

        public NLPRiskPattern(String name, String pattern, String description,
                               RiskLevel level, String suggestion, String alternatives) {
            this.name = name;
            this.pattern = pattern;
            this.description = description;
            this.level = level;
            this.suggestion = suggestion;
            this.alternatives = alternatives;
        }

        public String getName() { return name; }
        public String getPattern() { return pattern; }
        public String getDescription() { return description; }
        public RiskLevel getLevel() { return level; }
        public String getSuggestion() { return suggestion; }
        public String getAlternatives() { return alternatives; }
    }
}
