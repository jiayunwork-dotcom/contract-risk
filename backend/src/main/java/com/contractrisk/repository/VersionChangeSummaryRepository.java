package com.contractrisk.repository;

import com.contractrisk.entity.VersionChangeSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VersionChangeSummaryRepository extends JpaRepository<VersionChangeSummary, Long> {

    List<VersionChangeSummary> findByContractIdOrderByCreatedAtDesc(Long contractId);

    Optional<VersionChangeSummary> findByFromVersionIdAndToVersionId(Long fromVersionId, Long toVersionId);

    @Query("SELECT vcs FROM VersionChangeSummary vcs WHERE vcs.contractId = :contractId " +
           "AND vcs.toVersion.id = :versionId")
    Optional<VersionChangeSummary> findByContractIdAndToVersionId(
            @Param("contractId") Long contractId,
            @Param("versionId") Long versionId);

    @Query("SELECT vcs FROM VersionChangeSummary vcs WHERE vcs.contractId = :contractId " +
           "ORDER BY vcs.toVersion.versionNumber ASC")
    List<VersionChangeSummary> findByContractIdOrderByVersionAsc(@Param("contractId") Long contractId);
}
