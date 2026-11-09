package com.acme.salary.employee;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateEmployeeRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Email @Size(max = 200) String email,
        @NotBlank @Pattern(regexp = "[A-Z]{2}", message = "must be an ISO 3166-1 alpha-2 code, e.g. IN") String country,
        @NotBlank @Size(max = 100) String department,
        @NotBlank @Size(max = 100) String jobTitle,
        @NotBlank @Size(max = 10) String jobLevel,
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be an ISO 4217 code, e.g. INR") String currency,
        @Positive(message = "must be greater than zero") long salaryMinor,
        @NotNull @PastOrPresent LocalDate hireDate) {
}
