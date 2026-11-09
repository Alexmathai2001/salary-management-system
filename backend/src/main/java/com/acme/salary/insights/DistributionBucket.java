package com.acme.salary.insights;

/** One histogram bar: salaries from {@code fromUsd} (inclusive) up to {@code toUsd} (exclusive). */
public record DistributionBucket(long fromUsd, long toUsd, long count) {
}
