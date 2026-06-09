package com.contractrisk.controller;

import com.contractrisk.dto.ApiResponse;
import com.contractrisk.entity.ComplianceTemplate;
import com.contractrisk.entity.enums.ContractType;
import com.contractrisk.service.ComplianceService;
import com.contractrisk.service.PdfExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/compliance")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class ComplianceController {

    private final ComplianceService complianceService;
    private final PdfExportService pdfExportService;

    @PostMapping("/check/{contractId}")
    public ApiResponse<Map<String, Object>> checkCompliance(@PathVariable Long contractId) {
        try {
            Map<String, Object> result = complianceService.checkCompliance(contractId);
            return ApiResponse.success("合规检查完成", result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("合规检查失败", e);
            return ApiResponse.error("合规检查失败: " + e.getMessage());
        }
    }

    @GetMapping("/templates")
    public ApiResponse<List<ComplianceTemplate>> getTemplates() {
        List<ComplianceTemplate> templates = complianceService.getAllTemplates();
        return ApiResponse.success(templates);
    }

    @GetMapping("/templates/type/{type}")
    public ApiResponse<List<ComplianceTemplate>> getTemplatesByType(@PathVariable ContractType type) {
        List<ComplianceTemplate> templates = complianceService.getTemplatesByType(type);
        return ApiResponse.success(templates);
    }

    @GetMapping("/templates/{id}")
    public ApiResponse<ComplianceTemplate> getTemplateById(@PathVariable Long id) {
        Optional<ComplianceTemplate> template = complianceService.getTemplateById(id);
        return template
                .map(t -> ApiResponse.success(t))
                .orElseGet(() -> ApiResponse.error("模板不存在"));
    }

    @PostMapping("/templates")
    public ApiResponse<ComplianceTemplate> createTemplate(@RequestBody ComplianceTemplate template) {
        ComplianceTemplate saved = complianceService.createTemplate(template);
        return ApiResponse.success("模板创建成功", saved);
    }

    @DeleteMapping("/templates/{id}")
    public ApiResponse<Void> deleteTemplate(@PathVariable Long id) {
        boolean deleted = complianceService.deleteTemplate(id);
        if (deleted) {
            return ApiResponse.success("模板已删除", null);
        } else {
            return ApiResponse.error("模板不存在");
        }
    }

    @GetMapping("/export-pdf/{contractId}")
    public ResponseEntity<byte[]> exportCompliancePdf(@PathVariable Long contractId) {
        try {
            byte[] pdfBytes = pdfExportService.exportComplianceReport(contractId);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment",
                    "compliance_report_" + contractId + ".pdf");
            headers.setContentLength(pdfBytes.length);
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(pdfBytes);
        } catch (Exception e) {
            log.error("PDF导出失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
