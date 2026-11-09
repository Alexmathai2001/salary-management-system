package com.acme.salary.fx;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FxRateResponse(String currency, BigDecimal ratePerUsd, int fractionDigits, LocalDateTime updatedAt) {

    static FxRateResponse from(FxRate r) {
        BigDecimal rate = BigDecimal.valueOf(r.getRateMicro(), 6).stripTrailingZeros();
        if (rate.scale() < 0) {
            rate = rate.setScale(0); // avoid scientific notation such as 8.3E+1 in JSON
        }
        return new FxRateResponse(r.getCurrency(), rate, r.getFractionDigits(), r.getUpdatedAt());
    }
}
