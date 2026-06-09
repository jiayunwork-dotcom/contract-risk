package com.contractrisk.repository;

import com.contractrisk.entity.ContractVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContractVersionRepository extends JpaRepository<ContractVersion, Long> {

    List<ContractVersion> findByContractIdOrderByVersionNumberDesc(Long contractId);

    List<ContractVersion> findByContractIdOrderByVersionNumberAsc(Long contractId);

    List<ContractVersion> findByContractIdAndCurrentTrue(Long contractId);

    @Query("SELECT MAX(v.versionNumber) FROM ContractVersion v WHERE v.contract.id = :contractId")
    Integer findMaxVersionNumber(@Param("contractId") Long contractId);

    long countByContractId(Long contractId);

    Optional<ContractVersion> findByContractIdAndVersionNumber(Long contractId, Integer versionNumber);

    Optional<ContractVersion> findFirstByContractIdAndVersionNumberLessThanOrderByVersionNumberDesc(
            @Param("contractId") Long contractId, @Param("versionNumber") Integer versionNumber);
}
