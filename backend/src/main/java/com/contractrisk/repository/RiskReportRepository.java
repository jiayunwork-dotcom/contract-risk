package com.contractrisk.repository;

import com.contractrisk.entity.RiskReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RiskReportRepository extends JpaRepository<RiskReport, Long> {

    Optional<RiskReport> findTopByContractIdOrderByIdDesc(Long contractId);

    List<RiskReport> findAllByContractIdOrderByIdDesc(Long contractId);

    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM RiskReport r WHERE r.contract.id = :contractId")
    boolean existsByContractId(@Param("contractId") Long contractId);

    void deleteByContractId(Long contractId);

    Optional<RiskReport> findByVersionId(Long versionId);
}
