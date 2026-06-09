package com.contractrisk.repository;

import com.contractrisk.entity.ContractClause;
import com.contractrisk.entity.enums.ClauseSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContractClauseRepository extends JpaRepository<ContractClause, Long> {

    List<ContractClause> findByContractIdOrderBySortOrderAsc(Long contractId);

    List<ContractClause> findByContractIdAndSectionType(Long contractId, ClauseSection sectionType);

    @Query("SELECT cc FROM ContractClause cc WHERE cc.contract.id = :contractId AND cc.highRisk = true")
    List<ContractClause> findHighRiskClausesByContractId(@Param("contractId") Long contractId);

    @Query("SELECT COUNT(cc) FROM ContractClause cc WHERE cc.contract.id = :contractId AND cc.highRisk = true")
    long countHighRiskClausesByContractId(@Param("contractId") Long contractId);

    @Query("SELECT cc.sectionType, COUNT(cc) FROM ContractClause cc WHERE cc.contract.id = :contractId " +
           "AND cc.riskCount > 0 GROUP BY cc.sectionType")
    List<Object[]> countRisksBySection(@Param("contractId") Long contractId);

    void deleteByContractId(Long contractId);
}
