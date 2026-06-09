package com.contractrisk.repository;

import com.contractrisk.entity.RiskItem;
import com.contractrisk.entity.enums.RiskLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RiskItemRepository extends JpaRepository<RiskItem, Long> {

    List<RiskItem> findByClauseId(Long clauseId);

    @Query("SELECT ri FROM RiskItem ri WHERE ri.clause.contract.id = :contractId")
    List<RiskItem> findByContractId(@Param("contractId") Long contractId);

    @Query("SELECT ri FROM RiskItem ri WHERE ri.clause.contract.id = :contractId AND ri.riskLevel = :level")
    List<RiskItem> findByContractIdAndRiskLevel(@Param("contractId") Long contractId, @Param("level") RiskLevel level);

    @Query("SELECT COUNT(ri) FROM RiskItem ri WHERE ri.clause.contract.id = :contractId AND ri.riskLevel = :level")
    long countByContractIdAndRiskLevel(@Param("contractId") Long contractId, @Param("level") RiskLevel level);

    @Query("SELECT COUNT(ri) FROM RiskItem ri WHERE ri.clause.contract.id = :contractId")
    long countByContractId(@Param("contractId") Long contractId);

    void deleteByClauseContractId(Long contractId);

    void deleteByClauseId(Long clauseId);
}
