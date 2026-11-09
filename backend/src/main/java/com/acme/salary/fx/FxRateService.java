package com.acme.salary.fx;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FxRateService {

    private final FxRateRepository rates;
    private final Clock clock;

    public FxRateService(FxRateRepository rates, Clock clock) {
        this.rates = rates;
        this.clock = clock;
    }

    public List<FxRate> list() {
        return rates.findAllByOrderByCurrencyAsc();
    }

    /** Creates or updates a rate. Adding a currency here is what makes it usable for new employees. */
    @Transactional
    public FxRate upsert(String currencyCode, BigDecimal ratePerUsd) {
        Currency currency = parse(currencyCode);
        long micro = toMicro(ratePerUsd);
        if ("USD".equals(currency.getCurrencyCode()) && micro != FxConverter.RATE_SCALE) {
            throw new IllegalArgumentException("The USD rate is the reporting base and must be 1");
        }
        FxRate rate = rates.findById(currency.getCurrencyCode()).orElseGet(FxRate::new);
        int digits = Math.max(currency.getDefaultFractionDigits(), 0);
        rate.setCurrency(currency.getCurrencyCode());
        rate.setRateMicro(micro);
        rate.setFractionDigits(digits);
        rate.setMinorUnitScale((long) Math.pow(10, digits));
        rate.setUpdatedAt(LocalDateTime.now(clock));
        return rates.save(rate);
    }

    private static Currency parse(String code) {
        try {
            return Currency.getInstance(code);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("'" + code + "' is not a valid ISO 4217 currency code");
        }
    }

    static long toMicro(BigDecimal rate) {
        try {
            return rate.movePointRight(6).longValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Rate must have at most 6 decimal places and be a sensible size");
        }
    }
}
