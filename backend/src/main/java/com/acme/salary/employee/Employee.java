package com.acme.salary.employee;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/** An employee and their CURRENT salary. Past salaries live in {@link SalaryHistory}. */
@Entity
@Table(name = "employees")
@Getter
@Setter
@NoArgsConstructor
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JdbcTypeCode(SqlTypes.INTEGER) // SQLite rowid alias must be declared INTEGER, not BIGINT
    private Long id;

    @Column(name = "employee_code", nullable = false, unique = true)
    private String employeeCode;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    /** ISO 3166-1 alpha-2, e.g. "IN". */
    @Column(nullable = false)
    private String country;

    @Column(nullable = false)
    private String department;

    @Column(name = "job_title", nullable = false)
    private String jobTitle;

    @Column(name = "job_level", nullable = false)
    private String jobLevel;

    /** ISO 4217, e.g. "INR". The currency {@link #salaryMinor} is expressed in. */
    @Column(nullable = false)
    private String currency;

    /** Annual salary in minor units (e.g. cents), avoiding floating-point rounding. */
    @Column(name = "salary_minor", nullable = false)
    private long salaryMinor;

    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmployeeStatus status = EmployeeStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
