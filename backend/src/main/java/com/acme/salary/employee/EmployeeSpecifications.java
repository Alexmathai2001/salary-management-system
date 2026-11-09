package com.acme.salary.employee;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** Builds the dynamic WHERE clause for the employee directory. */
final class EmployeeSpecifications {

    private EmployeeSpecifications() {
    }

    static Specification<Employee> from(EmployeeFilter f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (hasText(f.q())) {
                String like = "%" + escapeLike(f.q().trim().toLowerCase()) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("firstName")), like, '\\'),
                        cb.like(cb.lower(root.get("lastName")), like, '\\'),
                        cb.like(cb.lower(root.get("email")), like, '\\'),
                        cb.like(cb.lower(root.get("employeeCode")), like, '\\'),
                        cb.like(cb.lower(cb.concat(cb.concat(root.get("firstName"), " "), root.get("lastName"))),
                                like, '\\')));
            }
            if (hasText(f.country())) {
                predicates.add(cb.equal(root.get("country"), f.country().trim().toUpperCase()));
            }
            if (hasText(f.department())) {
                predicates.add(cb.equal(root.get("department"), f.department().trim()));
            }
            if (hasText(f.jobTitle())) {
                predicates.add(cb.equal(root.get("jobTitle"), f.jobTitle().trim()));
            }
            if (hasText(f.jobLevel())) {
                predicates.add(cb.equal(root.get("jobLevel"), f.jobLevel().trim()));
            }
            if (f.status() != null) {
                predicates.add(cb.equal(root.get("status"), f.status()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    /** Escapes LIKE wildcards so user input like "50%" is matched literally. */
    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
