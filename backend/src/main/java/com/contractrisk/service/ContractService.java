package com.contractrisk.service;

import com.contractrisk.entity.Contract;
import com.contractrisk.entity.ContractClause;
import com.contractrisk.entity.enums.ContractType;
import com.contractrisk.repository.ContractClauseRepository;
import com.contractrisk.repository.ContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContractService {

    private final ContractRepository contractRepository;
    private final ContractClauseRepository clauseRepository;
    private final DocumentParserService documentParserService;
    private final ContractStructureService structureService;

    @Transactional
    public Contract uploadContract(MultipartFile file, ContractType contractType, String createdBy) throws IOException {
        String originalFileName = file.getOriginalFilename();
        String fileType = documentParserService.getFileType(originalFileName);
        Path filePath = documentParserService.saveUploadedFile(file);

        Contract contract = new Contract();
        contract.setOriginalFileName(originalFileName);
        contract.setFileType(fileType);
        contract.setFilePath(filePath.toString());
        contract.setContractType(contractType != null ? contractType : ContractType.OTHER);
        contract.setCreatedBy(createdBy);
        contract.setTitle(originalFileName);

        String text = documentParserService.parseDocument(file);
        contract.setFullText(text);

        contract = contractRepository.save(contract);

        List<ContractClause> clauses = structureService.parseStructure(text, contract);
        for (ContractClause clause : clauses) {
            clause.setContract(contract);
        }
        clauseRepository.saveAll(clauses);
        contract.setClauses(clauses);

        log.info("合同上传成功，ID: {}, 文件名: {}, 条款数: {}",
                contract.getId(), originalFileName, clauses.size());

        return contract;
    }

    public Optional<Contract> getContractById(Long id) {
        return contractRepository.findByIdAndDeletedFalse(id);
    }

    public List<Contract> getAllContracts() {
        return contractRepository.findByDeletedFalseOrderByCreatedAtDesc();
    }

    public List<Contract> getContractsByType(ContractType type) {
        return contractRepository.findByContractTypeAndDeletedFalse(type);
    }

    public List<Contract> searchContracts(String keyword) {
        return contractRepository.searchContracts(keyword);
    }

    @Transactional
    public boolean deleteContract(Long id) {
        return contractRepository.findByIdAndDeletedFalse(id)
                .map(contract -> {
                    contract.setDeleted(true);
                    contractRepository.save(contract);
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public Contract updateContract(Contract contract) {
        return contractRepository.save(contract);
    }

    public List<ContractClause> getContractClauses(Long contractId) {
        return clauseRepository.findByContractIdOrderBySortOrderAsc(contractId);
    }
}
