package com.acme.salary.employee;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One append-only record of a salary change. {@code oldSalaryMinor} is null for the first salary. */
@Entity
@Table(name = "salary_history")
@Getter
@Setter
@NoArgsConstructor
public class SalaryHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JdbcTypeCode(SqlTypes.INTEGER) // SQLite rowid alias must be declared INTEGER, not BIGINT
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "old_salary_minor")
    private Long oldSalaryMinor;

    @Column(name = "new_salary_minor", nullable = false)
    private long newSalaryMinor;

    @Column(nullable = false)
    private String currency;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    private String reason;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;
}
