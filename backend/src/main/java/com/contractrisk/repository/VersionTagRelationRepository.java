package com.contractrisk.repository;

import com.contractrisk.entity.VersionTagRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VersionTagRelationRepository extends JpaRepository<VersionTagRelation, Long> {

    List<VersionTagRelation> findByVersionId(Long versionId);

    @Query("SELECT vtr FROM VersionTagRelation vtr JOIN FETCH vtr.tag WHERE vtr.version.id = :versionId AND vtr.tag.deleted = false")
    List<VersionTagRelation> findActiveByVersionId(@Param("versionId") Long versionId);

    @Modifying
    @Query("DELETE FROM VersionTagRelation vtr WHERE vtr.version.id = :versionId")
    void deleteByVersionId(@Param("versionId") Long versionId);

    @Modifying
    @Query("DELETE FROM VersionTagRelation vtr WHERE vtr.version.id IN :versionIds")
    void deleteByVersionIds(@Param("versionIds") List<Long> versionIds);

    @Modifying
    @Query("DELETE FROM VersionTagRelation vtr WHERE vtr.version.id = :versionId AND vtr.tag.id = :tagId")
    void deleteByVersionIdAndTagId(@Param("versionId") Long versionId, @Param("tagId") Long tagId);

    boolean existsByVersionIdAndTagId(Long versionId, Long tagId);
}
