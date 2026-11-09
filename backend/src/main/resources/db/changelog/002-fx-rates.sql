--liquibase formatted sql

--changeset salary-management:002-fx-rates
-- Fixed, HR-maintained FX table used ONLY for reporting (salaries stay in their local currency).
-- rate_micro        = units of the currency per 1 USD, multiplied by 1,000,000 (integer, no float rounding).
-- fraction_digits   = ISO 4217 minor-unit digits (USD 2, JPY 0); minor_unit_scale = 10^fraction_digits.
-- Both are stored so reports can convert entirely in SQL without a power function.
CREATE TABLE fx_rates (
    currency         TEXT      PRIMARY KEY,
    rate_micro       BIGINT    NOT NULL CHECK (rate_micro > 0),
    fraction_digits  INTEGER   NOT NULL,
    minor_unit_scale BIGINT    NOT NULL,
    updated_at       TIMESTAMP NOT NULL
);

INSERT INTO fx_rates (currency, rate_micro, fraction_digits, minor_unit_scale, updated_at) VALUES
    ('USD',       1000000, 2, 100, 0),
    ('INR',      83000000, 2, 100, 0),
    ('GBP',        790000, 2, 100, 0),
    ('EUR',        920000, 2, 100, 0),
    ('SGD',       1350000, 2, 100, 0),
    ('AUD',       1500000, 2, 100, 0),
    ('CAD',       1360000, 2, 100, 0),
    ('JPY',     150000000, 0,   1, 0),
    ('BRL',       5000000, 2, 100, 0);

--rollback DROP TABLE fx_rates;
