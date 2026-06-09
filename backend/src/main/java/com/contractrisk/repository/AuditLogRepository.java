package com.contractrisk.repository;

import com.contractrisk.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findAll(Pageable pageable);

    Page<AuditLog> findByOperator(String operator, Pageable pageable);

    Page<AuditLog> findByOperationType(String operationType, Pageable pageable);

    @Query("SELECT al FROM AuditLog al WHERE " +
           "(:operator IS NULL OR al.operator = :operator) AND " +
           "(:operationType IS NULL OR al.operationType = :operationType) AND " +
           "(:startTime IS NULL OR al.operationTime >= :startTime) AND " +
           "(:endTime IS NULL OR al.operationTime <= :endTime) AND " +
           "(:targetContractId IS NULL OR al.targetContractId = :targetContractId)")
    Page<AuditLog> findByFilters(
            @Param("operator") String operator,
            @Param("operationType") String operationType,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("targetContractId") Long targetContractId,
            Pageable pageable);
}
