package com.acme.salary.insights;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InsightsService {

    static final long MIN_BUCKET_USD = 1_000;
    static final long MAX_BUCKET_USD = 1_000_000;
    static final int MAX_EARNERS = 100;
    static final int MAX_OUTLIERS = 200;

    private static final PayStats EMPTY = new PayStats("ALL", 0, 0, 0, 0, 0, 0, 0, 0);

    private final InsightsRepository repository;

    public InsightsService(InsightsRepository repository) {
        this.repository = repository;
    }

    public PayStats summary(InsightsFilter filter) {
        List<PayStats> rows = repository.stats(null, filter);
        return rows.isEmpty() ? EMPTY : rows.get(0);
    }

    public List<PayStats> by(Dimension dimension, InsightsFilter filter) {
        return repository.stats(dimension, filter);
    }

    public List<DistributionBucket> distribution(InsightsFilter filter, long bucketWidthUsd) {
        if (bucketWidthUsd < MIN_BUCKET_USD || bucketWidthUsd > MAX_BUCKET_USD) {
            throw new IllegalArgumentException(
                    "bucketUsd must be between " + MIN_BUCKET_USD + " and " + MAX_BUCKET_USD);
        }
        return repository.distribution(filter, bucketWidthUsd);
    }

    /**
     * @param direction null for both, otherwise only HIGH or only LOW outliers
     * @param limit     caps the returned list; {@code totalFlagged} in the report still counts every match
     */
    public OutlierReport outliers(InsightsFilter filter, OutlierParams params, Outlier.Direction direction,
            int limit) {
        if (limit < 1 || limit > MAX_OUTLIERS) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_OUTLIERS);
        }
        OutlierReport full = OutlierDetector.detect(repository.allPay(filter), params);
        List<Outlier> matching = direction == null
                ? full.outliers()
                : full.outliers().stream().filter(o -> o.direction() == direction).toList();
        return new OutlierReport(full.employeesEvaluated(), full.employeesNotEvaluated(), full.groupsEvaluated(),
                matching.size(), params, matching.stream().limit(limit).toList());
    }

    public List<EarnerResponse> earners(InsightsFilter filter, boolean top, int limit) {
        if (limit < 1 || limit > MAX_EARNERS) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_EARNERS);
        }
        return repository.earners(filter, top, limit);
    }
}
