package com.acme.salary.employee;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record SalaryHistoryResponse(
        Long id,
        Long oldSalaryMinor,
        long newSalaryMinor,
        String currency,
        LocalDate effectiveDate,
        String reason,
        LocalDateTime changedAt) {

    public static SalaryHistoryResponse from(SalaryHistory h) {
        return new SalaryHistoryResponse(
                h.getId(), h.getOldSalaryMinor(), h.getNewSalaryMinor(), h.getCurrency(),
                h.getEffectiveDate(), h.getReason(), h.getChangedAt());
    }
}
