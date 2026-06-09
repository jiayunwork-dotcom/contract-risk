package com.contractrisk.controller;

import com.contractrisk.dto.*;
import com.contractrisk.entity.ContractVersion;
import com.contractrisk.entity.enums.OperationType;
import com.contractrisk.service.AuditLogService;
import com.contractrisk.service.ChangeTrackingService;
import com.contractrisk.service.ContractVersionService;
import com.contractrisk.service.PdfExportService;
import com.contractrisk.service.RiskAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/versions")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class ContractVersionController {

    private final ContractVersionService versionService;
    private final ChangeTrackingService changeTrackingService;
    private final RiskAnalysisService riskAnalysisService;
    private final AuditLogService auditLogService;
    private final PdfExportService pdfExportService;

    @PostMapping("/{contractId}/upload")
    public ApiResponse<ContractVersion> uploadNewVersion(
            @PathVariable Long contractId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "versionNote", required = false) String versionNote,
            @RequestParam(value = "uploadedBy", defaultValue = "system") String uploadedBy) {
        try {
            if (file.isEmpty()) {
                return ApiResponse.error("文件不能为空");
            }
            String originalFileName = file.getOriginalFilename();
            if (originalFileName == null ||
                    (!originalFileName.toLowerCase().endsWith(".pdf") &&
                            !originalFileName.toLowerCase().endsWith(".doc") &&
                            !originalFileName.toLowerCase().endsWith(".docx"))) {
                return ApiResponse.error("仅支持PDF、DOC、DOCX格式的文件");
            }
            ContractVersion version = versionService.uploadNewVersion(contractId, file, versionNote, uploadedBy);
            return ApiResponse.success("新版本上传成功", version);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        } catch (IOException e) {
            log.error("文件上传失败", e);
            auditLogService.logFailure(uploadedBy, OperationType.UPLOAD_VERSION.name(),
                    contractId, null, "上传新版本失败: " + e.getMessage());
            return ApiResponse.error("文件上传失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("版本上传处理失败", e);
            return ApiResponse.error("版本上传处理失败: " + e.getMessage());
        }
    }

    @GetMapping("/{contractId}/timeline")
    public ApiResponse<VersionTimelineResponse> getVersionTimeline(@PathVariable Long contractId) {
        try {
            VersionTimelineResponse timeline = versionService.getVersionTimeline(contractId);
            return ApiResponse.success(timeline);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/{contractId}/versions")
    public ApiResponse<List<ContractVersion>> getVersionsByContract(@PathVariable Long contractId) {
        List<ContractVersion> versions = versionService.getVersionsByContractId(contractId);
        return ApiResponse.success(versions);
    }

    @GetMapping("/{contractId}/current")
    public ApiResponse<ContractVersion> getCurrentVersion(@PathVariable Long contractId) {
        Optional<ContractVersion> version = versionService.getCurrentVersion(contractId);
        return version
                .map(v -> ApiResponse.success(v))
                .orElseGet(() -> ApiResponse.error("当前版本不存在"));
    }

    @GetMapping("/detail/{versionId}")
    public ApiResponse<ContractVersion> getVersionDetail(@PathVariable Long versionId) {
        Optional<ContractVersion> version = versionService.getVersionById(versionId);
        return version
                .map(v -> {
                    auditLogService.logSuccess("system", OperationType.VIEW_VERSION.name(),
                            v.getContract().getId(), v.getId(),
                            "查看版本 " + v.getVersionLabel());
                    return ApiResponse.success(v);
                })
                .orElseGet(() -> ApiResponse.error("版本不存在"));
    }

    @GetMapping("/{versionId}/clauses")
    public ApiResponse<List<com.contractrisk.entity.ContractClause>> getVersionClauses(@PathVariable Long versionId) {
        List<com.contractrisk.entity.ContractClause> clauses = versionService.getVersionClauses(versionId);
        return ApiResponse.success(clauses);
    }

    @GetMapping("/{versionId}/risk-report")
    public ApiResponse<com.contractrisk.entity.RiskReport> getVersionRiskReport(@PathVariable Long versionId) {
        Optional<ContractVersion> versionOpt = versionService.getVersionById(versionId);
        if (versionOpt.isEmpty()) {
            return ApiResponse.error("版本不存在");
        }
        ContractVersion version = versionOpt.get();
        Optional<com.contractrisk.entity.RiskReport> report = riskAnalysisService.getReportByVersionId(versionId);
        return report
                .map(r -> ApiResponse.success(r))
                .orElseGet(() -> ApiResponse.error("该版本暂无风险报告，请先执行风险分析"));
    }

    @PostMapping("/{contractId}/rollback/{targetVersionNumber}")
    public ApiResponse<ContractVersion> rollbackVersion(
            @PathVariable Long contractId,
            @PathVariable Integer targetVersionNumber,
            @RequestParam(value = "operatedBy", defaultValue = "system") String operatedBy) {
        try {
            ContractVersion newVersion = versionService.rollbackToVersion(
                    contractId, targetVersionNumber, operatedBy);
            return ApiResponse.success("回滚成功，新版本为 " + newVersion.getVersionLabel(), newVersion);
        } catch (IllegalArgumentException e) {
            auditLogService.logFailure(operatedBy, OperationType.ROLLBACK_VERSION.name(),
                    contractId, null, "回滚失败: " + e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/{contractId}/diff")
    public ApiResponse<ClauseDiffResponse> getClauseDiff(
            @PathVariable Long contractId,
            @RequestParam Integer fromVersion,
            @RequestParam Integer toVersion) {
        try {
            ClauseDiffResponse diff = changeTrackingService.getClauseDiff(
                    contractId, fromVersion, toVersion);
            return ApiResponse.success("变更对比完成", diff);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/{contractId}/impact")
    public ApiResponse<ChangeImpactReport> getImpactAssessment(
            @PathVariable Long contractId,
            @RequestParam Integer fromVersion,
            @RequestParam Integer toVersion) {
        try {
            ChangeImpactReport report = changeTrackingService.getImpactAssessment(
                    contractId, fromVersion, toVersion);
            return ApiResponse.success("影响评估完成", report);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PostMapping("/{contractId}/batch-delete")
    public ResponseEntity<ApiResponse<Void>> batchDeleteVersions(
            @PathVariable Long contractId,
            @RequestBody BatchDeleteRequest request) {
        try {
            versionService.batchDeleteVersions(contractId, request.getVersionIds(), request.getOperatedBy());
            return ResponseEntity.ok(ApiResponse.success("批量删除成功", null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/{versionId}/note")
    public ApiResponse<ContractVersion> updateVersionNote(
            @PathVariable Long versionId,
            @RequestBody VersionNoteRequest request) {
        try {
            ContractVersion updated = versionService.updateVersionNote(
                    versionId, request.getVersionNote(), request.getOperatedBy());
            return ApiResponse.success("备注更新成功", updated);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/tags")
    public ApiResponse<List<VersionTagDTO>> getAllTags() {
        List<VersionTagDTO> tags = versionService.getAllTags();
        return ApiResponse.success(tags);
    }

    @PostMapping("/tags")
    public ApiResponse<VersionTagDTO> createTag(@RequestBody CreateTagRequest request) {
        try {
            VersionTagDTO tag = versionService.createTag(request.getName(), request.getColor(), request.getPredefined());
            return ApiResponse.success("标签创建成功", tag);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @DeleteMapping("/tags/{tagId}")
    public ApiResponse<Void> deleteTag(@PathVariable Long tagId) {
        try {
            versionService.deleteTag(tagId);
            return ApiResponse.success("标签删除成功", null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PutMapping("/{versionId}/tags")
    public ApiResponse<List<VersionTagDTO>> setVersionTags(
            @PathVariable Long versionId,
            @RequestBody VersionTagsRequest request) {
        try {
            List<VersionTagDTO> tags = versionService.setVersionTags(versionId, request.getTagIds());
            return ApiResponse.success("标签设置成功", tags);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/{versionId}/tags")
    public ApiResponse<List<VersionTagDTO>> getVersionTags(@PathVariable Long versionId) {
        List<VersionTagDTO> tags = versionService.getVersionTags(versionId);
        return ApiResponse.success(tags);
    }

    @GetMapping("/{contractId}/comparison-export-pdf")
    public ResponseEntity<byte[]> exportComparisonPdf(
            @PathVariable Long contractId,
            @RequestParam Integer fromVersion,
            @RequestParam Integer toVersion) {
        try {
            byte[] pdfBytes = pdfExportService.exportComparisonPdf(contractId, fromVersion, toVersion);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment",
                    "comparison_" + fromVersion + "_to_" + toVersion + ".pdf");
            headers.setContentLength(pdfBytes.length);
            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            log.error("导出对比PDF失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/init-tags")
    public ApiResponse<Void> initPredefinedTags() {
        versionService.initPredefinedTags();
        return ApiResponse.success("预定义标签初始化完成", null);
    }
}
