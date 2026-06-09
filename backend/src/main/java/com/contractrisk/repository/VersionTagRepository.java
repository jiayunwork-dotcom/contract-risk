package com.contractrisk.repository;

import com.contractrisk.entity.VersionTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VersionTagRepository extends JpaRepository<VersionTag, Long> {

    List<VersionTag> findByDeletedFalse();

    Optional<VersionTag> findByNameAndDeletedFalse(String name);

    List<VersionTag> findByPredefinedTrueAndDeletedFalse();

    boolean existsByNameAndDeletedFalse(String name);
}
