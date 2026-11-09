package com.acme.salary.employee;

import com.acme.salary.common.NotFoundException;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EmployeeService {

    static final int MAX_PAGE_SIZE = 100;
    static final int DEFAULT_PAGE_SIZE = 25;

    /** Public sort keys mapped to entity properties. An allowlist prevents sorting on arbitrary fields. */
    private static final Map<String, String> SORTABLE = Map.of(
            "name", "lastName",
            "code", "employeeCode",
            "email", "email",
            "country", "country",
            "department", "department",
            "jobTitle", "jobTitle",
            "jobLevel", "jobLevel",
            "salary", "salaryMinor",
            "hireDate", "hireDate");

    private final EmployeeRepository employees;
    private final SalaryHistoryRepository history;

    public EmployeeService(EmployeeRepository employees, SalaryHistoryRepository history) {
        this.employees = employees;
        this.history = history;
    }

    public Page<Employee> search(EmployeeFilter filter, int page, int size, String sortKey, boolean descending) {
        return employees.findAll(EmployeeSpecifications.from(filter), pageable(page, size, sortKey, descending));
    }

    public Employee get(Long id) {
        return employees.findById(id).orElseThrow(() -> new NotFoundException("Employee " + id + " not found"));
    }

    public List<SalaryHistory> salaryHistory(Long id) {
        get(id); // 404 if the employee does not exist
        return history.findByEmployeeIdOrderByEffectiveDateDescIdDesc(id);
    }

    static Pageable pageable(int page, int size, String sortKey, boolean descending) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        String key = (sortKey == null || sortKey.isBlank()) ? "name" : sortKey;
        String property = SORTABLE.get(key);
        if (property == null) {
            throw new IllegalArgumentException("Cannot sort by '" + key + "'. Allowed: " + SORTABLE.keySet());
        }
        Sort.Direction direction = descending ? Sort.Direction.DESC : Sort.Direction.ASC;
        // Secondary sort on id keeps paging stable when the primary key has ties.
        Sort sort = Sort.by(direction, property).and(Sort.by(Sort.Direction.ASC, "id"));
        return PageRequest.of(page, size, sort);
    }
}
