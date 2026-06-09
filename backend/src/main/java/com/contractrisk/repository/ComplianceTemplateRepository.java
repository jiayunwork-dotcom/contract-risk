package com.contractrisk.repository;

import com.contractrisk.entity.ComplianceTemplate;
import com.contractrisk.entity.enums.ContractType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ComplianceTemplateRepository extends JpaRepository<ComplianceTemplate, Long> {

    List<ComplianceTemplate> findByActiveTrue();

    List<ComplianceTemplate> findByContractTypeAndActiveTrue(ContractType contractType);

    Optional<ComplianceTemplate> findByContractTypeAndActiveTrueOrderByCreatedAtDesc(ContractType contractType);

    Optional<ComplianceTemplate> findByNameAndActiveTrue(String name);
}
