package com.contractrisk.service;

import com.contractrisk.dto.ClauseDiffResponse;
import com.contractrisk.entity.Contract;
import com.contractrisk.entity.ContractClause;
import com.contractrisk.entity.RiskItem;
import com.contractrisk.entity.RiskReport;
import com.contractrisk.entity.enums.ContractType;
import com.contractrisk.entity.enums.OperationType;
import com.contractrisk.entity.enums.RiskLevel;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfExportService {

    private final ComplianceService complianceService;
    private final RiskAnalysisService riskAnalysisService;
    private final ContractService contractService;
    private final ChangeTrackingService changeTrackingService;
    private final AuditLogService auditLogService;

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public byte[] exportComplianceReport(Long contractId) {
        try {
            Contract contract = contractService.getContractById(contractId)
                    .orElseThrow(() -> new IllegalArgumentException("合同不存在: " + contractId));

            Map<String, Object> complianceResult = complianceService.checkCompliance(contractId);
            RiskReport riskReport = riskAnalysisService.getReportByContractId(contractId).orElse(null);
            List<RiskItem> riskItems = riskAnalysisService.getRiskItemsByContractId(contractId);
            List<ContractClause> clauses = contractService.getContractClauses(contractId);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4, 50, 50, 50, 50);
            PdfWriter.getInstance(document, baos);

            document.open();

            Font titleFont = createChineseFont(18, Font.BOLD);
            Font headerFont = createChineseFont(14, Font.BOLD);
            Font normalFont = createChineseFont(10, Font.NORMAL);
            Font boldFont = createChineseFont(10, Font.BOLD);
            Font smallFont = createChineseFont(8, Font.NORMAL);

            document.add(new Paragraph("合规检查报告", titleFont));
            document.add(Chunk.NEWLINE);

            document.add(new Paragraph("一、合同基本信息", headerFont));
            document.add(Chunk.NEWLINE);

            PdfPTable basicTable = new PdfPTable(2);
            basicTable.setWidthPercentage(100);
            addTableRow(basicTable, "合同标题", contract.getTitle(), boldFont, normalFont);
            addTableRow(basicTable, "合同类型", translateContractType(contract.getContractType()), boldFont, normalFont);
            addTableRow(basicTable, "甲方", contract.getPartyA() != null ? contract.getPartyA() : "未识别", boldFont, normalFont);
            addTableRow(basicTable, "乙方", contract.getPartyB() != null ? contract.getPartyB() : "未识别", boldFont, normalFont);
            if (contract.getTotalAmount() != null) {
                addTableRow(basicTable, "合同金额", contract.getTotalAmount().toString(), boldFont, normalFont);
            }
            addTableRow(basicTable, "合同编号", contract.getContractNumber() != null ? contract.getContractNumber() : "未识别", boldFont, normalFont);
            document.add(basicTable);
            document.add(Chunk.NEWLINE);

            document.add(new Paragraph("二、使用的合规模板", headerFont));
            document.add(Chunk.NEWLINE);
            String templateName = (String) complianceResult.getOrDefault("templateName", "无");
            document.add(new Paragraph("模板名称: " + templateName, normalFont));
            document.add(Chunk.NEWLINE);

            document.add(new Paragraph("三、缺失必备条款", headerFont));
            document.add(Chunk.NEWLINE);
            @SuppressWarnings("unchecked")
            List<String> missingRequired = (List<String>) complianceResult.getOrDefault("missingRequired", List.of());
            if (missingRequired.isEmpty()) {
                document.add(new Paragraph("无缺失必备条款", normalFont));
            } else {
                for (int i = 0; i < missingRequired.size(); i++) {
                    document.add(new Paragraph((i + 1) + ". " + missingRequired.get(i), normalFont));
                }
            }
            document.add(Chunk.NEWLINE);

            document.add(new Paragraph("四、存在的禁止条款", headerFont));
            document.add(Chunk.NEWLINE);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> forbiddenClauses = (List<Map<String, Object>>) complianceResult.getOrDefault("forbiddenClauses", List.of());
            if (forbiddenClauses.isEmpty()) {
                document.add(new Paragraph("无禁止条款", normalFont));
            } else {
                for (int i = 0; i < forbiddenClauses.size(); i++) {
                    Map<String, Object> item = forbiddenClauses.get(i);
                    document.add(new Paragraph((i + 1) + ". 禁止条款: " + item.getOrDefault("forbiddenTerm", ""), boldFont));
                    document.add(new Paragraph("   条款编号: " + item.getOrDefault("clauseNumber", "N/A"), normalFont));
                    document.add(new Paragraph("   条款标题: " + item.getOrDefault("clauseTitle", "N/A"), normalFont));
                    document.add(new Paragraph("   原文位置: 条款" + item.getOrDefault("clauseNumber", "N/A"), normalFont));
                    String content = (String) item.getOrDefault("content", "");
                    if (content.length() > 200) {
                        content = content.substring(0, 200) + "...";
                    }
                    document.add(new Paragraph("   内容: " + content, normalFont));
                }
            }
            document.add(Chunk.NEWLINE);

            document.add(new Paragraph("五、金额违规项", headerFont));
            document.add(Chunk.NEWLINE);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> amountViolations = (List<Map<String, Object>>) complianceResult.getOrDefault("amountViolations", List.of());
            if (amountViolations.isEmpty()) {
                document.add(new Paragraph("无金额违规", normalFont));
            } else {
                for (int i = 0; i < amountViolations.size(); i++) {
                    Map<String, Object> v = amountViolations.get(i);
                    document.add(new Paragraph((i + 1) + ". " + v.getOrDefault("message", ""), normalFont));
                    document.add(new Paragraph("   合同金额: " + v.getOrDefault("amount", ""), normalFont));
                }
            }
            document.add(Chunk.NEWLINE);

            document.add(new Paragraph("六、合规结论", headerFont));
            document.add(Chunk.NEWLINE);
            String complianceStatus = (String) complianceResult.getOrDefault("complianceStatus", "COMPLIANT");
            String statusText = translateComplianceStatus(complianceStatus);
            document.add(new Paragraph("合规结论: " + statusText, boldFont));
            int penaltyScore = (int) complianceResult.getOrDefault("penaltyScore", 0);
            document.add(new Paragraph("合规扣分: " + penaltyScore + "分", normalFont));
            document.add(Chunk.NEWLINE);

            document.add(new Paragraph("七、扣分明细", headerFont));
            document.add(Chunk.NEWLINE);
            if (riskReport != null) {
                document.add(new Paragraph("风险评分: " + riskReport.getRiskScore() + "/100", normalFont));
                document.add(new Paragraph("高风险项: " + riskReport.getHighRiskCount() + "个", normalFont));
                document.add(new Paragraph("中风险项: " + riskReport.getMediumRiskCount() + "个", normalFont));
                document.add(new Paragraph("低风险项: " + riskReport.getLowRiskCount() + "个", normalFont));
                document.add(Chunk.NEWLINE);

                if (!riskItems.isEmpty()) {
                    PdfPTable detailTable = new PdfPTable(4);
                    detailTable.setWidthPercentage(100);
                    detailTable.setWidths(new float[]{3, 2, 2, 2});
                    addTableHeader(detailTable, new String[]{"规则名称", "风险等级", "扣分", "匹配内容"}, boldFont);
                    for (RiskItem item : riskItems) {
                        String levelText = translateRiskLevel(item.getRiskLevel());
                        String matchedText = item.getMatchedText() != null ? item.getMatchedText() : "";
                        if (matchedText.length() > 30) {
                            matchedText = matchedText.substring(0, 30) + "...";
                        }
                        addTableData(detailTable, new String[]{
                                item.getRuleName(),
                                levelText,
                                item.getPenaltyScore() != null ? item.getPenaltyScore() + "分" : "-",
                                matchedText
                        }, normalFont);
                    }
                    document.add(detailTable);
                }
            }
            document.add(Chunk.NEWLINE);

            document.add(new Paragraph("八、缺失必备条款扣分", headerFont));
            document.add(Chunk.NEWLINE);
            if (!missingRequired.isEmpty()) {
                int missingPenalty = penaltyScore > 0 && !forbiddenClauses.isEmpty()
                        ? penaltyScore - forbiddenClauses.size() * 50
                        : (penaltyScore > 0 ? penaltyScore : 0);
                document.add(new Paragraph("缺失" + missingRequired.size() + "条必备条款，每条扣30分，共扣" + Math.max(0, missingPenalty) + "分", normalFont));
            } else {
                document.add(new Paragraph("无扣分", normalFont));
            }
            document.add(Chunk.NEWLINE);

            document.add(new Paragraph("九、禁止条款扣分", headerFont));
            document.add(Chunk.NEWLINE);
            if (!forbiddenClauses.isEmpty()) {
                document.add(new Paragraph("存在" + forbiddenClauses.size() + "条禁止条款，每条扣50分，共扣" + (forbiddenClauses.size() * 50) + "分", normalFont));
            } else {
                document.add(new Paragraph("无扣分", normalFont));
            }
            document.add(Chunk.NEWLINE);
            document.add(Chunk.NEWLINE);

            Paragraph footer = new Paragraph();
            footer.add(new Chunk("报告生成时间: " + LocalDateTime.now().format(DTF), smallFont));
            footer.add(Chunk.NEWLINE);
            footer.add(new Chunk("合同条款风险识别与合规审查系统 - 机密文件", smallFont));
            document.add(footer);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("生成PDF报告失败", e);
            throw new RuntimeException("生成PDF报告失败: " + e.getMessage(), e);
        }
    }

    private Font createChineseFont(int size, int style) {
        try {
            BaseFont bf = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
            return new Font(bf, size, style);
        } catch (Exception e) {
            return new Font(Font.HELVETICA, size, style);
        }
    }

    private void addTableRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBackgroundColor(new Color(240, 240, 240));
        labelCell.setPadding(5);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value != null ? value : "", valueFont));
        valueCell.setPadding(5);
        table.addCell(valueCell);
    }

    private void addTableHeader(PdfPTable table, String[] headers, Font font) {
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, font));
            cell.setBackgroundColor(new Color(70, 130, 180));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(5);
            table.addCell(cell);
        }
    }

    private void addTableData(PdfPTable table, String[] data, Font font) {
        for (String datum : data) {
            PdfPCell cell = new PdfPCell(new Phrase(datum != null ? datum : "", font));
            cell.setPadding(4);
            table.addCell(cell);
        }
    }

    private String translateContractType(ContractType type) {
        if (type == null) return "未知";
        return switch (type) {
            case PURCHASE -> "采购合同";
            case LEASE -> "租赁合同";
            case LABOR -> "劳动合同";
            case SALES -> "销售合同";
            case SERVICE -> "服务合同";
            case COOPERATION -> "合作合同";
            case CONFIDENTIALITY -> "保密协议";
            case OTHER -> "其他合同";
        };
    }

    private String translateComplianceStatus(String status) {
        return switch (status) {
            case "COMPLIANT" -> "合规";
            case "NON_COMPLIANT" -> "不合规";
            case "SERIOUS_NON_COMPLIANT" -> "严重不合规";
            default -> status;
        };
    }

    private String translateRiskLevel(RiskLevel level) {
        return switch (level) {
            case HIGH -> "高风险";
            case MEDIUM -> "中风险";
            case LOW -> "低风险";
        };
    }

    public byte[] exportComparisonPdf(Long contractId, Integer fromVersion, Integer toVersion) {
        try {
            ClauseDiffResponse diffResponse = changeTrackingService.getClauseDiff(contractId, fromVersion, toVersion);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4, 50, 50, 50, 50);
            PdfWriter.getInstance(document, baos);

            document.open();

            Font titleFont = createChineseFont(18, Font.BOLD);
            Font headerFont = createChineseFont(14, Font.BOLD);
            Font normalFont = createChineseFont(10, Font.NORMAL);
            Font boldFont = createChineseFont(10, Font.BOLD);
            Font smallFont = createChineseFont(8, Font.NORMAL);

            document.add(new Paragraph("版本对比报告", titleFont));
            document.add(Chunk.NEWLINE);

            document.add(new Paragraph("一、对比摘要", headerFont));
            document.add(Chunk.NEWLINE);

            PdfPTable summaryTable = new PdfPTable(2);
            summaryTable.setWidthPercentage(100);
            addTableRow(summaryTable, "源版本", diffResponse.getFromVersionLabel(), boldFont, normalFont);
            addTableRow(summaryTable, "目标版本", diffResponse.getToVersionLabel(), boldFont, normalFont);
            addTableRow(summaryTable, "新增条款数", String.valueOf(diffResponse.getAddedClausesCount()), boldFont, normalFont);
            addTableRow(summaryTable, "删除条款数", String.valueOf(diffResponse.getRemovedClausesCount()), boldFont, normalFont);
            addTableRow(summaryTable, "修改条款数", String.valueOf(diffResponse.getModifiedClausesCount()), boldFont, normalFont);
            document.add(summaryTable);
            document.add(Chunk.NEWLINE);

            document.add(new Paragraph("二、逐条差异列表", headerFont));
            document.add(Chunk.NEWLINE);

            int idx = 1;
            for (ClauseDiffResponse.ClauseDiffItem diff : diffResponse.getDiffs()) {
                String changeType = diff.getChangeType();
                String typeLabel = switch (changeType) {
                    case "ADDED" -> "[新增]";
                    case "REMOVED" -> "[删除]";
                    case "MODIFIED" -> "[修改]";
                    default -> "[其他]";
                };

                Color bgColor = switch (changeType) {
                    case "ADDED" -> new Color(212, 237, 218);
                    case "REMOVED" -> new Color(248, 215, 218);
                    case "MODIFIED" -> new Color(255, 243, 205);
                    default -> Color.WHITE;
                };

                PdfPCell typeCell = new PdfPCell(new Phrase(idx + ". " + typeLabel + " " + diff.getClauseNumber() + " " + diff.getClauseTitle(), boldFont));
                typeCell.setBackgroundColor(bgColor);
                typeCell.setPadding(6);
                typeCell.setColspan(2);
                PdfPTable diffTable = new PdfPTable(2);
                diffTable.setWidthPercentage(100);
                diffTable.addCell(typeCell);

                if ("ADDED".equals(changeType)) {
                    addTableRow(diffTable, "新增内容", truncate(diff.getNewContent(), 500), boldFont, normalFont);
                } else if ("REMOVED".equals(changeType)) {
                    addTableRow(diffTable, "删除内容", truncate(diff.getOldContent(), 500), boldFont, normalFont);
                } else {
                    addTableRow(diffTable, "原内容", truncate(diff.getOldContent(), 500), boldFont, normalFont);
                    addTableRow(diffTable, "新内容", truncate(diff.getNewContent(), 500), boldFont, normalFont);
                    if (diff.getSimilarity() != null) {
                        addTableRow(diffTable, "相似度", String.format("%.2f%%", diff.getSimilarity() * 100), boldFont, normalFont);
                    }
                }

                if (diff.getIntroducesNewRisk() != null && diff.getIntroducesNewRisk()) {
                    PdfPCell riskCell = new PdfPCell(new Phrase("⚠️ 引入新风险: " + (diff.getNewRiskDescription() != null ? diff.getNewRiskDescription() : ""), normalFont));
                    riskCell.setBackgroundColor(new Color(248, 215, 218));
                    riskCell.setPadding(4);
                    riskCell.setColspan(2);
                    diffTable.addCell(riskCell);
                }

                if (diff.getTextDiffs() != null && !diff.getTextDiffs().isEmpty()) {
                    StringBuilder textDiffStr = new StringBuilder();
                    for (ClauseDiffResponse.TextDiffSegment seg : diff.getTextDiffs()) {
                        String prefix = switch (seg.getType()) {
                            case "added" -> "+ ";
                            case "removed" -> "- ";
                            default -> "  ";
                        };
                        textDiffStr.append(prefix).append(seg.getText() != null ? seg.getText() : "").append("\n");
                    }
                    addTableRow(diffTable, "文字Diff", textDiffStr.toString(), boldFont, normalFont);
                }

                document.add(diffTable);
                document.add(Chunk.NEWLINE);
                idx++;
            }

            document.add(new Paragraph("三、风险变化说明", headerFont));
            document.add(Chunk.NEWLINE);

            int addedRiskCount = 0;
            int reducedRiskCount = 0;
            for (ClauseDiffResponse.ClauseDiffItem diff : diffResponse.getDiffs()) {
                if (diff.getIntroducesNewRisk() != null && diff.getIntroducesNewRisk()) {
                    addedRiskCount++;
                }
                if ("MODIFIED".equals(diff.getChangeType())
                        && diff.getOldRiskCount() != null && diff.getNewRiskCount() != null
                        && diff.getNewRiskCount() < diff.getOldRiskCount()) {
                    reducedRiskCount++;
                }
            }

            document.add(new Paragraph("对比期间新增风险的条款: " + addedRiskCount + "条", normalFont));
            document.add(new Paragraph("对比期间风险降低的条款: " + reducedRiskCount + "条", normalFont));
            document.add(new Paragraph("新增条款: " + diffResponse.getAddedClausesCount() + "条", normalFont));
            document.add(new Paragraph("删除条款: " + diffResponse.getRemovedClausesCount() + "条", normalFont));
            document.add(new Paragraph("修改条款: " + diffResponse.getModifiedClausesCount() + "条", normalFont));
            document.add(Chunk.NEWLINE);

            Paragraph footer = new Paragraph();
            footer.add(new Chunk("报告生成时间: " + LocalDateTime.now().format(DTF), smallFont));
            footer.add(Chunk.NEWLINE);
            footer.add(new Chunk("合同条款风险识别与合规审查系统 - 版本对比报告 - 机密文件", smallFont));
            document.add(footer);

            document.close();

            auditLogService.logSuccess("system", OperationType.EXPORT_COMPARISON_PDF.name(),
                    contractId, diffResponse.getToVersionId(),
                    "导出版本对比PDF " + diffResponse.getFromVersionLabel() + " -> " + diffResponse.getToVersionLabel());

            return baos.toByteArray();
        } catch (Exception e) {
            log.error("生成对比PDF报告失败", e);
            throw new RuntimeException("生成对比PDF报告失败: " + e.getMessage(), e);
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
