package com.contractrisk.controller;

import com.contractrisk.dto.ApiResponse;
import com.contractrisk.service.ContractComparisonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/comparison")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class ComparisonController {

    private final ContractComparisonService comparisonService;

    @PostMapping("/{contractId1}/{contractId2}")
    public ApiResponse<Map<String, Object>> compareContracts(
            @PathVariable Long contractId1,
            @PathVariable Long contractId2) {
        try {
            Map<String, Object> result = comparisonService.compareContracts(contractId1, contractId2);
            return ApiResponse.success("合同对比完成", result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("合同对比失败", e);
            return ApiResponse.error("合同对比失败: " + e.getMessage());
        }
    }
}
