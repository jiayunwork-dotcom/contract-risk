package com.contractrisk.service;

import com.contractrisk.entity.Contract;
import com.contractrisk.entity.ContractClause;
import com.contractrisk.entity.enums.ClauseSection;
import com.contractrisk.util.AmountUtil;
import com.contractrisk.util.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ContractStructureService {

    private static final Pattern CLAUSE_NUMBER_PATTERN = Pattern.compile(
            "^(第[一二三四五六七八九十百千]+[条款节]|[1-9]\\d*(?:\\.[1-9]\\d*)*[、.．]?\\s+)"
    );

    private static final Pattern TITLE_PATTERN = Pattern.compile(
            "^(第[一二三四五六七八九十百千]+[条款节][^。：；]{0,50}|[1-9]\\d*(?:\\.[1-9]\\d*)*[、.．]?\\s+[^。：；\\n]{1,100})"
    );

    private static final Map<String, ClauseSection> SECTION_KEYWORDS = new LinkedHashMap<>();

    static {
        SECTION_KEYWORDS.put("当事人|甲方|乙方|双方|各方|鉴于", ClauseSection.PARTIES_INFO);
        SECTION_KEYWORDS.put("定义|释义|术语", ClauseSection.DEFINITIONS);
        SECTION_KEYWORDS.put("权利|义务|责任|职责", ClauseSection.RIGHTS_AND_OBLIGATIONS);
        SECTION_KEYWORDS.put("违约|赔偿|违约金|滞纳金", ClauseSection.BREACH_LIABILITY);
        SECTION_KEYWORDS.put("争议|纠纷|管辖|诉讼|仲裁", ClauseSection.DISPUTE_RESOLUTION);
        SECTION_KEYWORDS.put("保密|涉密|不披露", ClauseSection.CONFIDENTIALITY);
        SECTION_KEYWORDS.put("知识产权|专利|商标|著作权|版权", ClauseSection.INTELLECTUAL_PROPERTY);
        SECTION_KEYWORDS.put("付款|支付|价款|报酬|费用", ClauseSection.PAYMENT_TERMS);
        SECTION_KEYWORDS.put("期限|终止|解除|续约|有效期", ClauseSection.TERM_AND_TERMINATION);
        SECTION_KEYWORDS.put("不可抗力|免责|意外事件", ClauseSection.FORCE_MAJEURE);
        SECTION_KEYWORDS.put("其他|未尽事宜|通知|联系", ClauseSection.MISCELLANEOUS);
        SECTION_KEYWORDS.put("附则|附件|补充|附录", ClauseSection.SUPPLEMENTARY);
    }

    public List<ContractClause> parseStructure(String text, Contract contract) {
        List<ContractClause> clauses = new ArrayList<>();

        extractBasicInfo(text, contract);

        String[] lines = text.split("\n");
        StringBuilder currentContent = new StringBuilder();
        String currentNumber = "";
        String currentTitle = "";
        int sortOrder = 0;
        int startPosition = 0;
        boolean isFirstClause = true;

        for (String line : lines) {
            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty()) {
                continue;
            }

            Matcher numberMatcher = CLAUSE_NUMBER_PATTERN.matcher(trimmedLine);
            if (numberMatcher.find()) {
                if (!isFirstClause && currentContent.length() > 0) {
                    ContractClause clause = createClause(
                            contract, currentNumber, currentTitle,
                            currentContent.toString().trim(), startPosition, sortOrder++
                    );
                    clauses.add(clause);
                    startPosition += currentContent.length();
                }

                currentNumber = extractClauseNumber(trimmedLine);
                currentTitle = extractTitle(trimmedLine);
                currentContent = new StringBuilder(trimmedLine);
                isFirstClause = false;
            } else {
                if (currentContent.length() > 0) {
                    currentContent.append("\n").append(trimmedLine);
                } else {
                    if (isFirstClause && trimmedLine.length() > 20) {
                        currentContent.append(trimmedLine);
                    }
                }
            }
        }

        if (currentContent.length() > 0) {
            ContractClause clause = createClause(
                    contract, currentNumber, currentTitle,
                    currentContent.toString().trim(), startPosition, sortOrder++
            );
            clauses.add(clause);
        }

        assignSectionTypes(clauses);
        updateClauseRiskStats(clauses);

        return clauses;
    }

    private void extractBasicInfo(String text, Contract contract) {
        if (contract.getPartyA() == null || contract.getPartyA().isEmpty()) {
            String partyA = extractParty(text, "甲方|甲方：|甲方（盖章）|需方|买方");
            if (partyA != null) {
                contract.setPartyA(partyA);
            }
        }

        if (contract.getPartyB() == null || contract.getPartyB().isEmpty()) {
            String partyB = extractParty(text, "乙方|乙方：|乙方（盖章）|供方|卖方");
            if (partyB != null) {
                contract.setPartyB(partyB);
            }
        }

        if (contract.getTotalAmount() == null) {
            BigDecimal amount = extractTotalAmount(text);
            if (amount != null) {
                contract.setTotalAmount(amount);
                contract.setAmountText(AmountUtil.extractAmountText(text));
            }
        }

        if (contract.getEffectiveDate() == null) {
            LocalDateTime effectiveDate = DateUtil.extractDate(text, "生效日期|自.*起生效|合同生效");
            if (effectiveDate != null) {
                contract.setEffectiveDate(effectiveDate);
            }
        }

        if (contract.getExpirationDate() == null) {
            LocalDateTime expirationDate = DateUtil.extractDate(text, "有效期至|到期日期|终止日期");
            if (expirationDate != null) {
                contract.setExpirationDate(expirationDate);
            }
        }

        if (contract.getSigningDate() == null) {
            LocalDateTime signingDate = DateUtil.extractDate(text, "签订日期|签署日期|本合同于.*签订");
            if (signingDate != null) {
                contract.setSigningDate(signingDate);
            }
        }

        if (contract.getTitle() == null || contract.getTitle().isEmpty()) {
            String title = extractTitleFromText(text);
            if (title != null) {
                contract.setTitle(title);
            } else {
                contract.setTitle("未命名合同");
            }
        }
    }

    private String extractParty(String text, String patternStr) {
        Pattern pattern = Pattern.compile(patternStr + "[:：]?\\s*([^\\n，。；]{2,50})");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            String party = matcher.group(1).trim();
            if (party.length() > 1 && party.length() < 100) {
                return party;
            }
        }
        return null;
    }

    private BigDecimal extractTotalAmount(String text) {
        Pattern pattern = Pattern.compile(
                "(?:合同总金额|总价款|总价|合同金额|合同价款)[:：]?\\s*([^\\n；。，]+)"
        );
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return AmountUtil.parseAmount(matcher.group(1));
        }
        return null;
    }

    private String extractTitleFromText(String text) {
        String[] lines = text.split("\n");
        for (int i = 0; i < Math.min(10, lines.length); i++) {
            String line = lines[i].trim();
            if (line.contains("合同") || line.contains("协议")) {
                if (line.length() > 3 && line.length() < 100) {
                    return line.replaceAll("[\\s\\u00A0]+", " ").trim();
                }
            }
        }
        return null;
    }

    private String extractClauseNumber(String line) {
        Matcher matcher = CLAUSE_NUMBER_PATTERN.matcher(line);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }

    private String extractTitle(String line) {
        Matcher matcher = TITLE_PATTERN.matcher(line);
        if (matcher.find()) {
            String fullMatch = matcher.group(1).trim();
            String number = extractClauseNumber(line);
            String title = fullMatch.substring(number.length()).trim();
            title = title.replaceAll("[：:。；].*", "").trim();
            return title;
        }
        return "";
    }

    private ContractClause createClause(Contract contract, String number, String title,
                                         String content, int startPos, int sortOrder) {
        ContractClause clause = new ContractClause();
        clause.setContract(contract);
        clause.setClauseNumber(number);
        clause.setTitle(title);
        clause.setContent(content);
        clause.setStartPosition(startPos);
        clause.setEndPosition(startPos + content.length());
        clause.setSortOrder(sortOrder);
        return clause;
    }

    private void assignSectionTypes(List<ContractClause> clauses) {
        for (ContractClause clause : clauses) {
            String text = (clause.getTitle() + " " + clause.getContent()).toLowerCase();
            ClauseSection section = ClauseSection.OTHER;

            for (Map.Entry<String, ClauseSection> entry : SECTION_KEYWORDS.entrySet()) {
                Pattern pattern = Pattern.compile(entry.getKey(), Pattern.CASE_INSENSITIVE);
                if (pattern.matcher(text).find()) {
                    section = entry.getValue();
                    break;
                }
            }

            clause.setSectionType(section);
        }
    }

    private void updateClauseRiskStats(List<ContractClause> clauses) {
        for (ContractClause clause : clauses) {
            clause.setRiskCount(0);
            clause.setHighRisk(false);
        }
    }

    public ClauseSection detectSection(String text) {
        if (text == null) {
            return ClauseSection.OTHER;
        }
        String lowerText = text.toLowerCase();
        for (Map.Entry<String, ClauseSection> entry : SECTION_KEYWORDS.entrySet()) {
            Pattern pattern = Pattern.compile(entry.getKey(), Pattern.CASE_INSENSITIVE);
            if (pattern.matcher(lowerText).find()) {
                return entry.getValue();
            }
        }
        return ClauseSection.OTHER;
    }
}
