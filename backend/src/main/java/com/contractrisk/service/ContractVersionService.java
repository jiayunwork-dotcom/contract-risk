package com.contractrisk.service;

import com.contractrisk.dto.VersionTimelineResponse;
import com.contractrisk.entity.Contract;
import com.contractrisk.entity.ContractClause;
import com.contractrisk.entity.ContractVersion;
import com.contractrisk.entity.VersionChangeSummary;
import com.contractrisk.entity.enums.OperationType;
import com.contractrisk.repository.ContractClauseRepository;
import com.contractrisk.repository.ContractRepository;
import com.contractrisk.repository.ContractVersionRepository;
import com.contractrisk.repository.VersionChangeSummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContractVersionService {

    private final ContractVersionRepository versionRepository;
    private final ContractRepository contractRepository;
    private final ContractClauseRepository clauseRepository;
    private final VersionChangeSummaryRepository changeSummaryRepository;
    private final ContractService contractService;
    private final DocumentParserService documentParserService;
    private final ContractStructureService structureService;
    private final ChangeTrackingService changeTrackingService;
    private final RiskAnalysisService riskAnalysisService;
    private final AuditLogService auditLogService;

    @Transactional
    public ContractVersion uploadNewVersion(Long contractId, MultipartFile file,
                                             String versionNote, String uploadedBy) throws IOException {
        Contract contract = contractRepository.findByIdAndDeletedFalse(contractId)
                .orElseThrow(() -> new IllegalArgumentException("合同不存在: " + contractId));

        Integer maxVersion = versionRepository.findMaxVersionNumber(contractId);
        int newVersionNumber = (maxVersion == null ? 0 : maxVersion) + 1;

        String originalFileName = file.getOriginalFilename();
        String fileType = documentParserService.getFileType(originalFileName);
        Path filePath = documentParserService.saveUploadedFile(file);
        String text = documentParserService.parseDocument(file);

        List<ContractVersion> currentVersions = versionRepository.findByContractIdAndCurrentTrue(contractId);
        for (ContractVersion cv : currentVersions) {
            cv.setCurrent(false);
            versionRepository.save(cv);
        }

        ContractVersion version = new ContractVersion();
        version.setContract(contract);
        version.setVersionNumber(newVersionNumber);
        version.setVersionLabel("v" + newVersionNumber);
        version.setUploadedBy(uploadedBy);
        version.setVersionNote(versionNote);
        version.setFullText(text);
        version.setOriginalFileName(originalFileName);
        version.setFileType(fileType);
        version.setFilePath(filePath.toString());
        version.setCurrent(true);
        version = versionRepository.save(version);

        List<ContractClause> clauses = structureService.parseStructure(text, contract);
        for (ContractClause clause : clauses) {
            clause.setVersion(version);
        }
        clauseRepository.saveAll(clauses);

        contract.setFullText(text);
        contract.setOriginalFileName(originalFileName);
        contract.setFileType(fileType);
        contract.setFilePath(filePath.toString());
        contract.setCurrentVersionId(version.getId());
        contractRepository.save(contract);

        if (newVersionNumber > 1) {
            try {
                changeTrackingService.generateChangeSummary(contractId, newVersionNumber - 1, newVersionNumber);
            } catch (Exception e) {
                log.warn("生成变更摘要失败: {}", e.getMessage());
            }
        }

        try {
            riskAnalysisService.analyzeByVersion(contractId, version.getId());
        } catch (Exception e) {
            log.warn("版本风险分析失败: {}", e.getMessage());
        }

        auditLogService.logSuccess(uploadedBy, OperationType.UPLOAD_VERSION.name(),
                contractId, version.getId(),
                "上传新版本 " + version.getVersionLabel());

        log.info("合同新版本上传成功，合同ID: {}, 版本: {}", contractId, version.getVersionLabel());
        return version;
    }

    public VersionTimelineResponse getVersionTimeline(Long contractId) {
        Contract contract = contractRepository.findByIdAndDeletedFalse(contractId)
                .orElseThrow(() -> new IllegalArgumentException("合同不存在: " + contractId));

        List<ContractVersion> versions = versionRepository.findByContractIdOrderByVersionNumberDesc(contractId);
        List<VersionChangeSummary> summaries = changeSummaryRepository.findByContractIdOrderByVersionAsc(contractId);

        VersionTimelineResponse response = new VersionTimelineResponse();
        response.setContractId(contractId);
        response.setContractTitle(contract.getTitle());
        response.setCurrentVersionNumber(versions.stream()
                .filter(ContractVersion::isCurrent)
                .map(ContractVersion::getVersionNumber)
                .findFirst()
                .orElse(1));

        List<VersionTimelineResponse.VersionNode> nodes = new ArrayList<>();
        for (ContractVersion v : versions) {
            VersionTimelineResponse.VersionNode node = new VersionTimelineResponse.VersionNode();
            node.setVersionId(v.getId());
            node.setVersionNumber(v.getVersionNumber());
            node.setVersionLabel(v.getVersionLabel());
            node.setUploadedBy(v.getUploadedBy());
            node.setUploadTime(v.getCreatedAt());
            node.setVersionNote(v.getVersionNote());
            node.setCurrent(v.isCurrent());

            summaries.stream()
                    .filter(s -> s.getToVersion() != null && s.getToVersion().getId().equals(v.getId()))
                    .findFirst()
                    .ifPresent(s -> {
                        VersionTimelineResponse.ChangeSummaryInfo info = new VersionTimelineResponse.ChangeSummaryInfo();
                        info.setAddedClausesCount(s.getAddedClausesCount());
                        info.setRemovedClausesCount(s.getRemovedClausesCount());
                        info.setModifiedClausesCount(s.getModifiedClausesCount());
                        info.setRiskScoreChange(s.getRiskScoreChange());
                        node.setChangeSummary(info);
                    });

            nodes.add(node);
        }

        response.setVersions(nodes);
        return response;
    }

    public Optional<ContractVersion> getVersionById(Long versionId) {
        return versionRepository.findById(versionId);
    }

    public List<ContractVersion> getVersionsByContractId(Long contractId) {
        return versionRepository.findByContractIdOrderByVersionNumberDesc(contractId);
    }

    public Optional<ContractVersion> getCurrentVersion(Long contractId) {
        List<ContractVersion> currentVersions = versionRepository.findByContractIdAndCurrentTrue(contractId);
        return currentVersions.stream().findFirst();
    }

    public List<ContractClause> getVersionClauses(Long versionId) {
        return clauseRepository.findByVersionId(versionId);
    }

    @Transactional
    public ContractVersion rollbackToVersion(Long contractId, Integer targetVersionNumber, String operatedBy) {
        Contract contract = contractRepository.findByIdAndDeletedFalse(contractId)
                .orElseThrow(() -> new IllegalArgumentException("合同不存在: " + contractId));

        ContractVersion targetVersion = versionRepository
                .findByContractIdAndVersionNumber(contractId, targetVersionNumber)
                .orElseThrow(() -> new IllegalArgumentException("目标版本不存在: v" + targetVersionNumber));

        Integer maxVersion = versionRepository.findMaxVersionNumber(contractId);
        int newVersionNumber = (maxVersion == null ? 0 : maxVersion) + 1;

        List<ContractVersion> currentVersions = versionRepository.findByContractIdAndCurrentTrue(contractId);
        for (ContractVersion cv : currentVersions) {
            cv.setCurrent(false);
            versionRepository.save(cv);
        }

        ContractVersion newVersion = new ContractVersion();
        newVersion.setContract(contract);
        newVersion.setVersionNumber(newVersionNumber);
        newVersion.setVersionLabel("v" + newVersionNumber);
        newVersion.setUploadedBy(operatedBy);
        newVersion.setVersionNote("回滚至 " + targetVersion.getVersionLabel() + " 的内容");
        newVersion.setFullText(targetVersion.getFullText());
        newVersion.setOriginalFileName(targetVersion.getOriginalFileName());
        newVersion.setFileType(targetVersion.getFileType());
        newVersion.setFilePath(targetVersion.getFilePath());
        newVersion.setCurrent(true);
        newVersion = versionRepository.save(newVersion);

        List<ContractClause> sourceClauses = clauseRepository.findByVersionId(targetVersion.getId());
        List<ContractClause> newClauses = new ArrayList<>();
        for (ContractClause source : sourceClauses) {
            ContractClause clause = new ContractClause();
            clause.setContract(contract);
            clause.setVersion(newVersion);
            clause.setClauseNumber(source.getClauseNumber());
            clause.setTitle(source.getTitle());
            clause.setContent(source.getContent());
            clause.setSectionType(source.getSectionType());
            clause.setStartPosition(source.getStartPosition());
            clause.setEndPosition(source.getEndPosition());
            clause.setSortOrder(source.getSortOrder());
            clause.setParentClauseId(source.getParentClauseId());
            clause.setHighRisk(source.isHighRisk());
            clause.setRiskCount(source.getRiskCount());
            newClauses.add(clause);
        }
        clauseRepository.saveAll(newClauses);

        contract.setFullText(targetVersion.getFullText());
        contract.setCurrentVersionId(newVersion.getId());
        contractRepository.save(contract);

        try {
            Integer previousVersionNumber = maxVersion;
            if (previousVersionNumber != null) {
                changeTrackingService.generateChangeSummary(contractId, previousVersionNumber, newVersionNumber);
            }
        } catch (Exception e) {
            log.warn("回滚后生成变更摘要失败: {}", e.getMessage());
        }

        try {
            riskAnalysisService.analyzeByVersion(contractId, newVersion.getId());
        } catch (Exception e) {
            log.warn("回滚后版本风险分析失败: {}", e.getMessage());
        }

        auditLogService.logSuccess(operatedBy, OperationType.ROLLBACK_VERSION.name(),
                contractId, newVersion.getId(),
                "回滚至 " + targetVersion.getVersionLabel() + "，新版本为 " + newVersion.getVersionLabel());

        log.info("合同版本回滚成功，合同ID: {}, 回滚至: {}, 新版本: {}",
                contractId, targetVersion.getVersionLabel(), newVersion.getVersionLabel());
        return newVersion;
    }

    @Transactional
    public void switchCurrentVersion(Long contractId, Long versionId) {
        Contract contract = contractRepository.findByIdAndDeletedFalse(contractId)
                .orElseThrow(() -> new IllegalArgumentException("合同不存在: " + contractId));

        ContractVersion targetVersion = versionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("版本不存在: " + versionId));

        if (!targetVersion.getContract().getId().equals(contractId)) {
            throw new IllegalArgumentException("版本不属于该合同");
        }

        List<ContractVersion> currentVersions = versionRepository.findByContractIdAndCurrentTrue(contractId);
        for (ContractVersion cv : currentVersions) {
            cv.setCurrent(false);
            versionRepository.save(cv);
        }

        targetVersion.setCurrent(true);
        versionRepository.save(targetVersion);

        contract.setCurrentVersionId(versionId);
        contract.setFullText(targetVersion.getFullText());
        contractRepository.save(contract);
    }
}
