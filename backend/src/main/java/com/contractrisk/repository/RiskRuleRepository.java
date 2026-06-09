package com.contractrisk.repository;

import com.contractrisk.entity.RiskRule;
import com.contractrisk.entity.enums.RiskLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RiskRuleRepository extends JpaRepository<RiskRule, Long> {

    List<RiskRule> findByEnabledTrueOrderByRiskLevelAsc();

    List<RiskRule> findByRiskLevelAndEnabledTrue(RiskLevel riskLevel);

    List<RiskRule> findByRuleCategoryAndEnabledTrue(String ruleCategory);

    @Query("SELECT r.ruleCategory FROM RiskRule r WHERE r.enabled = true GROUP BY r.ruleCategory")
    List<String> findAllCategories();

    long countByEnabledTrue();
}
