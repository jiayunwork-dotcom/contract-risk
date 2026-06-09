package com.contractrisk.controller;

import com.contractrisk.dto.ApiResponse;
import com.contractrisk.dto.ContractUploadResponse;
import com.contractrisk.entity.Contract;
import com.contractrisk.entity.ContractClause;
import com.contractrisk.entity.enums.ContractType;
import com.contractrisk.service.ContractService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/contracts")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class ContractController {

    private final ContractService contractService;

    @PostMapping("/upload")
    public ApiResponse<ContractUploadResponse> uploadContract(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "contractType", required = false) ContractType contractType,
            @RequestParam(value = "createdBy", defaultValue = "system") String createdBy) {

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

            Contract contract = contractService.uploadContract(file, contractType, createdBy);
            return ApiResponse.success("文件上传成功", toUploadResponse(contract));

        } catch (IOException e) {
            log.error("文件上传失败", e);
            return ApiResponse.error("文件上传失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("合同处理失败", e);
            return ApiResponse.error("合同处理失败: " + e.getMessage());
        }
    }

    @GetMapping
    public ApiResponse<List<ContractUploadResponse>> getAllContracts() {
        List<Contract> contracts = contractService.getAllContracts();
        List<ContractUploadResponse> responses = contracts.stream()
                .map(this::toUploadResponse)
                .collect(Collectors.toList());
        return ApiResponse.success(responses);
    }

    @GetMapping("/{id}")
    public ApiResponse<ContractUploadResponse> getContractById(@PathVariable Long id) {
        Optional<Contract> contractOpt = contractService.getContractById(id);
        return contractOpt
                .map(contract -> ApiResponse.success(toUploadResponse(contract)))
                .orElseGet(() -> ApiResponse.error("合同不存在"));
    }

    @GetMapping("/{id}/clauses")
    public ApiResponse<List<ContractClause>> getContractClauses(@PathVariable Long id) {
        if (contractService.getContractById(id).isEmpty()) {
            return ApiResponse.error("合同不存在");
        }
        List<ContractClause> clauses = contractService.getContractClauses(id);
        return ApiResponse.success(clauses);
    }

    @GetMapping("/type/{type}")
    public ApiResponse<List<ContractUploadResponse>> getContractsByType(@PathVariable ContractType type) {
        List<Contract> contracts = contractService.getContractsByType(type);
        List<ContractUploadResponse> responses = contracts.stream()
                .map(this::toUploadResponse)
                .collect(Collectors.toList());
        return ApiResponse.success(responses);
    }

    @GetMapping("/search")
    public ApiResponse<List<ContractUploadResponse>> searchContracts(@RequestParam String keyword) {
        List<Contract> contracts = contractService.searchContracts(keyword);
        List<ContractUploadResponse> responses = contracts.stream()
                .map(this::toUploadResponse)
                .collect(Collectors.toList());
        return ApiResponse.success(responses);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteContract(@PathVariable Long id) {
        boolean deleted = contractService.deleteContract(id);
        if (deleted) {
            return ApiResponse.success("删除成功", null);
        } else {
            return ApiResponse.error("合同不存在或删除失败");
        }
    }

    private ContractUploadResponse toUploadResponse(Contract contract) {
        return new ContractUploadResponse(
                contract.getId(),
                contract.getTitle(),
                contract.getContractNumber(),
                contract.getContractType() != null ? contract.getContractType().name() : null,
                contract.getPartyA(),
                contract.getPartyB(),
                contract.getTotalAmount(),
                contract.getAmountText(),
                contract.getOriginalFileName(),
                contract.getFileType(),
                contract.getEffectiveDate(),
                contract.getExpirationDate(),
                contract.getSigningDate(),
                contract.getClauses() != null ? contract.getClauses().size() : 0,
                contract.getCreatedAt(),
                contract.getCurrentVersionId()
        );
    }
}
