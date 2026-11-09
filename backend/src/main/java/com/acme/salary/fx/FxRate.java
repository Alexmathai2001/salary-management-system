package com.acme.salary.fx;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Units of {@link #currency} per 1 USD, as an integer in micro-units, plus the currency's minor-unit scale. */
@Entity
@Table(name = "fx_rates")
@Getter
@Setter
@NoArgsConstructor
public class FxRate {

    @Id
    private String currency;

    @Column(name = "rate_micro", nullable = false)
    private long rateMicro;

    @Column(name = "fraction_digits", nullable = false)
    private int fractionDigits;

    @Column(name = "minor_unit_scale", nullable = false)
    private long minorUnitScale;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
