package com.contractrisk.service;

import com.contractrisk.dto.VersionTagDTO;
import com.contractrisk.dto.VersionTimelineResponse;
import com.contractrisk.entity.*;
import com.contractrisk.entity.enums.OperationType;
import com.contractrisk.repository.*;
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
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContractVersionService {

    private final ContractVersionRepository versionRepository;
    private final ContractRepository contractRepository;
    private final ContractClauseRepository clauseRepository;
    private final VersionChangeSummaryRepository changeSummaryRepository;
    private final VersionTagRepository tagRepository;
    private final VersionTagRelationRepository tagRelationRepository;
    private final RiskItemRepository riskItemRepository;
    private final RiskReportRepository riskReportRepository;
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

            List<VersionTagDTO> tagDTOs = getVersionTags(v.getId());
            node.setTags(tagDTOs);

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

    @Transactional
    public void batchDeleteVersions(Long contractId, List<Long> versionIds, String operatedBy) {
        Contract contract = contractRepository.findByIdAndDeletedFalse(contractId)
                .orElseThrow(() -> new IllegalArgumentException("合同不存在: " + contractId));

        Long currentVersionId = contract.getCurrentVersionId();
        for (Long vid : versionIds) {
            if (currentVersionId != null && currentVersionId.equals(vid)) {
                throw new IllegalArgumentException("当前版本(ID:" + vid + ")不允许删除");
            }
        }

        List<ContractVersion> versionsToDelete = versionRepository.findAllById(versionIds);
        for (ContractVersion v : versionsToDelete) {
            if (!v.getContract().getId().equals(contractId)) {
                throw new IllegalArgumentException("版本(ID:" + v.getId() + ")不属于该合同");
            }
        }

        tagRelationRepository.deleteByVersionIds(versionIds);

        for (Long vid : versionIds) {
            List<ContractClause> clauses = clauseRepository.findByVersionId(vid);
            List<Long> clauseIds = clauses.stream().map(ContractClause::getId).collect(Collectors.toList());
            for (Long clauseId : clauseIds) {
                riskItemRepository.deleteByClauseId(clauseId);
            }
            clauseRepository.deleteAll(clauses);

            riskReportRepository.findByVersionId(vid)
                    .ifPresent(riskReportRepository::delete);
        }

        changeSummaryRepository.findByContractIdOrderByVersionAsc(contractId).stream()
                .filter(s -> versionIds.contains(s.getFromVersion() != null ? s.getFromVersion().getId() : -1L)
                        || versionIds.contains(s.getToVersion() != null ? s.getToVersion().getId() : -1L))
                .forEach(changeSummaryRepository::delete);

        versionRepository.deleteAllById(versionIds);

        auditLogService.logSuccess(operatedBy, OperationType.BATCH_DELETE_VERSION.name(),
                contractId, null,
                "批量删除版本IDs: " + versionIds);

        log.info("批量删除版本成功，合同ID: {}, 删除版本IDs: {}", contractId, versionIds);
    }

    @Transactional
    public ContractVersion updateVersionNote(Long versionId, String versionNote, String operatedBy) {
        ContractVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("版本不存在: " + versionId));

        String oldNote = version.getVersionNote();
        version.setVersionNote(versionNote);
        version = versionRepository.save(version);

        auditLogService.logSuccess(operatedBy, OperationType.UPDATE_VERSION_NOTE.name(),
                version.getContract().getId(), versionId,
                "修改版本备注 " + version.getVersionLabel() + "，原备注: " + (oldNote != null ? oldNote.substring(0, Math.min(oldNote.length(), 100)) : "空"));

        return version;
    }

    public List<VersionTagDTO> getAllTags() {
        List<VersionTag> tags = tagRepository.findByDeletedFalse();
        return tags.stream()
                .map(t -> new VersionTagDTO(t.getId(), t.getName(), t.getColor(), t.isPredefined()))
                .collect(Collectors.toList());
    }

    @Transactional
    public VersionTagDTO createTag(String name, String color, Boolean predefined) {
        if (tagRepository.existsByNameAndDeletedFalse(name)) {
            throw new IllegalArgumentException("标签名称已存在: " + name);
        }
        VersionTag tag = new VersionTag();
        tag.setName(name);
        tag.setColor(color != null ? color : "#3498db");
        tag.setPredefined(predefined != null && predefined);
        tag = tagRepository.save(tag);
        return new VersionTagDTO(tag.getId(), tag.getName(), tag.getColor(), tag.isPredefined());
    }

    @Transactional
    public void deleteTag(Long tagId) {
        VersionTag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new IllegalArgumentException("标签不存在: " + tagId));
        if (tag.isPredefined()) {
            throw new IllegalArgumentException("预定义标签不可删除");
        }
        tag.setDeleted(true);
        tagRepository.save(tag);
    }

    @Transactional
    public List<VersionTagDTO> setVersionTags(Long versionId, List<Long> tagIds) {
        ContractVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("版本不存在: " + versionId));

        tagRelationRepository.deleteByVersionId(versionId);

        List<VersionTagDTO> result = new ArrayList<>();
        if (tagIds != null) {
            for (Long tagId : tagIds) {
                VersionTag tag = tagRepository.findById(tagId)
                        .filter(t -> !t.isDeleted())
                        .orElseThrow(() -> new IllegalArgumentException("标签不存在或已删除: " + tagId));

                VersionTagRelation relation = new VersionTagRelation();
                relation.setVersion(version);
                relation.setTag(tag);
                tagRelationRepository.save(relation);

                result.add(new VersionTagDTO(tag.getId(), tag.getName(), tag.getColor(), tag.isPredefined()));
            }
        }

        auditLogService.logSuccess("system", OperationType.ADD_VERSION_TAG.name(),
                version.getContract().getId(), versionId,
                "设置版本 " + version.getVersionLabel() + " 标签: " + tagIds);

        return result;
    }

    public List<VersionTagDTO> getVersionTags(Long versionId) {
        List<VersionTagRelation> relations = tagRelationRepository.findActiveByVersionId(versionId);
        return relations.stream()
                .map(r -> {
                    VersionTag tag = r.getTag();
                    return new VersionTagDTO(tag.getId(), tag.getName(), tag.getColor(), tag.isPredefined());
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void initPredefinedTags() {
        if (!tagRepository.findByPredefinedTrueAndDeletedFalse().isEmpty()) {
            return;
        }
        String[][] predefinedTags = {
                {"已审核", "#27ae60"},
                {"待修订", "#f39c12"},
                {"最终版", "#2e86c1"},
                {"草稿", "#95a5a6"},
                {"已签署", "#1abc9c"}
        };
        for (String[] pt : predefinedTags) {
            VersionTag tag = new VersionTag();
            tag.setName(pt[0]);
            tag.setColor(pt[1]);
            tag.setPredefined(true);
            tagRepository.save(tag);
        }
    }
}
