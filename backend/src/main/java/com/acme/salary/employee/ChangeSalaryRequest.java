package com.acme.salary.employee;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ChangeSalaryRequest(
        @Positive(message = "must be greater than zero") long newSalaryMinor,
        @NotNull LocalDate effectiveDate,
        @Size(max = 500) String reason) {
}
