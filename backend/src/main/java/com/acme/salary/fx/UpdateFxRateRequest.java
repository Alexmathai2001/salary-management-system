package com.acme.salary.fx;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** Units of the currency per 1 USD, e.g. 83.25 for INR. At most 6 decimal places. */
public record UpdateFxRateRequest(@NotNull @Positive BigDecimal ratePerUsd) {
}
