package com.contractrisk.repository;

import com.contractrisk.entity.Contract;
import com.contractrisk.entity.enums.ContractType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContractRepository extends JpaRepository<Contract, Long> {

    List<Contract> findByContractTypeAndDeletedFalse(ContractType contractType);

    List<Contract> findByCreatedByAndDeletedFalse(String createdBy);

    Optional<Contract> findByIdAndDeletedFalse(Long id);

    @Query("SELECT c FROM Contract c WHERE c.deleted = false AND " +
           "(LOWER(c.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(c.contractNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(c.partyA) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(c.partyB) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Contract> searchContracts(@Param("keyword") String keyword);

    List<Contract> findByDeletedFalseOrderByCreatedAtDesc();
}
