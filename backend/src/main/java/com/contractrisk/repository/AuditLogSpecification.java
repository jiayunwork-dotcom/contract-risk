package com.contractrisk.repository;

import com.contractrisk.entity.AuditLog;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AuditLogSpecification {

    public static Specification<AuditLog> withFilters(String operator, String operationType,
                                                       LocalDateTime startTime, LocalDateTime endTime,
                                                       Long contractId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (operator != null && !operator.isEmpty()) {
                predicates.add(cb.equal(root.get("operator"), operator));
            }
            if (operationType != null && !operationType.isEmpty()) {
                predicates.add(cb.equal(root.get("operationType"), operationType));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("operationTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("operationTime"), endTime));
            }
            if (contractId != null) {
                predicates.add(cb.equal(root.get("targetContractId"), contractId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
