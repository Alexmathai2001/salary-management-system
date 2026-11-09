package com.acme.salary.employee;

import com.acme.salary.common.ConflictException;
import com.acme.salary.common.NotFoundException;
import com.acme.salary.fx.FxRateRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private final FxRateRepository fxRates;
    private final Clock clock;

    public EmployeeService(EmployeeRepository employees, SalaryHistoryRepository history,
            FxRateRepository fxRates, Clock clock) {
        this.employees = employees;
        this.history = history;
        this.fxRates = fxRates;
        this.clock = clock;
    }

    public Page<Employee> search(EmployeeFilter filter, int page, int size, String sortKey, boolean descending) {
        return employees.findAll(EmployeeSpecifications.from(filter), pageable(page, size, sortKey, descending));
    }

    /** Every employee matching the filter (no paging), in the same order the directory would show them. */
    public List<Employee> export(EmployeeFilter filter, String sortKey, boolean descending) {
        return employees.findAll(EmployeeSpecifications.from(filter), pageable(0, 1, sortKey, descending).getSort());
    }

    public Employee get(Long id) {
        return employees.findById(id).orElseThrow(() -> new NotFoundException("Employee " + id + " not found"));
    }

    public List<SalaryHistory> salaryHistory(Long id) {
        get(id); // 404 if the employee does not exist
        return history.findByEmployeeIdOrderByEffectiveDateDescIdDesc(id);
    }

    @Transactional
    public Employee create(CreateEmployeeRequest r) {
        requireUniqueEmail(r.email(), null);
        // Reports convert via the FX table, so an employee in an unknown currency would vanish from every report.
        if (!fxRates.existsById(r.currency())) {
            throw new IllegalArgumentException(
                    "Unsupported currency " + r.currency() + ": add an FX rate for it first");
        }
        LocalDateTime now = LocalDateTime.now(clock);

        Employee e = new Employee();
        // Placeholder satisfies NOT NULL/UNIQUE until the generated id is known (same transaction).
        e.setEmployeeCode("PENDING-" + java.util.UUID.randomUUID());
        e.setFirstName(r.firstName().trim());
        e.setLastName(r.lastName().trim());
        e.setEmail(r.email().trim().toLowerCase());
        e.setCountry(r.country());
        e.setDepartment(r.department().trim());
        e.setJobTitle(r.jobTitle().trim());
        e.setJobLevel(r.jobLevel().trim());
        e.setCurrency(r.currency());
        e.setSalaryMinor(r.salaryMinor());
        e.setHireDate(r.hireDate());
        e.setStatus(EmployeeStatus.ACTIVE);
        e.setCreatedAt(now);
        e.setUpdatedAt(now);

        e = employees.save(e);
        e.setEmployeeCode(String.format("EMP-%06d", e.getId()));

        recordSalary(e, null, r.salaryMinor(), r.hireDate(), "Initial salary", now);
        return e;
    }

    @Transactional
    public Employee update(Long id, UpdateEmployeeRequest r) {
        Employee e = get(id);
        requireUniqueEmail(r.email(), id);

        e.setFirstName(r.firstName().trim());
        e.setLastName(r.lastName().trim());
        e.setEmail(r.email().trim().toLowerCase());
        e.setCountry(r.country());
        e.setDepartment(r.department().trim());
        e.setJobTitle(r.jobTitle().trim());
        e.setJobLevel(r.jobLevel().trim());
        e.setHireDate(r.hireDate());
        e.setUpdatedAt(LocalDateTime.now(clock));
        return e;
    }

    /** Soft delete: keeps the row so salary history and reports stay consistent. Idempotent. */
    @Transactional
    public Employee deactivate(Long id) {
        Employee e = get(id);
        if (e.getStatus() != EmployeeStatus.INACTIVE) {
            e.setStatus(EmployeeStatus.INACTIVE);
            e.setUpdatedAt(LocalDateTime.now(clock));
        }
        return e;
    }

    @Transactional
    public Employee reactivate(Long id) {
        Employee e = get(id);
        if (e.getStatus() != EmployeeStatus.ACTIVE) {
            e.setStatus(EmployeeStatus.ACTIVE);
            e.setUpdatedAt(LocalDateTime.now(clock));
        }
        return e;
    }

    /** Changes the current salary and appends the audit record in one transaction. */
    @Transactional
    public Employee changeSalary(Long id, ChangeSalaryRequest r) {
        Employee e = get(id);
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();

        if (e.getStatus() != EmployeeStatus.ACTIVE) {
            throw new ConflictException("Cannot change the salary of an inactive employee");
        }
        if (r.newSalaryMinor() == e.getSalaryMinor()) {
            throw new IllegalArgumentException("New salary is the same as the current salary");
        }
        if (r.effectiveDate().isAfter(today)) {
            throw new IllegalArgumentException("Effective date cannot be in the future");
        }
        if (r.effectiveDate().isBefore(e.getHireDate())) {
            throw new IllegalArgumentException("Effective date cannot be before the hire date");
        }

        long old = e.getSalaryMinor();
        e.setSalaryMinor(r.newSalaryMinor());
        e.setUpdatedAt(now);
        recordSalary(e, old, r.newSalaryMinor(), r.effectiveDate(), r.reason(), now);
        return e;
    }

    private void recordSalary(Employee e, Long oldMinor, long newMinor, LocalDate effective, String reason,
            LocalDateTime now) {
        SalaryHistory h = new SalaryHistory();
        h.setEmployeeId(e.getId());
        h.setOldSalaryMinor(oldMinor);
        h.setNewSalaryMinor(newMinor);
        h.setCurrency(e.getCurrency());
        h.setEffectiveDate(effective);
        h.setReason(reason == null || reason.isBlank() ? null : reason.trim());
        h.setChangedAt(now);
        history.save(h);
    }

    private void requireUniqueEmail(String email, Long excludingId) {
        boolean taken = excludingId == null
                ? employees.existsByEmailIgnoreCase(email.trim())
                : employees.existsByEmailIgnoreCaseAndIdNot(email.trim(), excludingId);
        if (taken) {
            throw new ConflictException("An employee with email " + email.trim() + " already exists");
        }
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
