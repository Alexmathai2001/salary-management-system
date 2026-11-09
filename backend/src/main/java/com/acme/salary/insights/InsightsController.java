package com.acme.salary.insights;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only pay analytics. All monetary values are USD cents (converted with the FX table). */
@RestController
@RequestMapping("/api/insights")
public class InsightsController {

    private final InsightsService service;

    public InsightsController(InsightsService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public PayStats summary(
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String jobLevel,
            @RequestParam(required = false) String jobTitle,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return service.summary(new InsightsFilter(country, department, jobLevel, jobTitle, includeInactive));
    }

    @GetMapping("/by/{dimension}")
    public List<PayStats> by(
            @org.springframework.web.bind.annotation.PathVariable String dimension,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String jobLevel,
            @RequestParam(required = false) String jobTitle,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return service.by(Dimension.parse(dimension),
                new InsightsFilter(country, department, jobLevel, jobTitle, includeInactive));
    }

    @GetMapping("/distribution")
    public List<DistributionBucket> distribution(
            @RequestParam(defaultValue = "20000") long bucketUsd,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String jobLevel,
            @RequestParam(required = false) String jobTitle,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return service.distribution(new InsightsFilter(country, department, jobLevel, jobTitle, includeInactive),
                bucketUsd);
    }

    @GetMapping("/earners")
    public List<EarnerResponse> earners(
            @RequestParam(defaultValue = "top") String order,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String jobLevel,
            @RequestParam(required = false) String jobTitle,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        if (!order.equalsIgnoreCase("top") && !order.equalsIgnoreCase("bottom")) {
            throw new IllegalArgumentException("order must be 'top' or 'bottom'");
        }
        return service.earners(new InsightsFilter(country, department, jobLevel, jobTitle, includeInactive),
                order.equalsIgnoreCase("top"), limit);
    }

    /**
     * Employees paid far from their peers (same job title, level and country). Uses a robust median/MAD rule, and
     * reports how many employees were not judged because their peer group was too small.
     */
    @GetMapping("/outliers")
    public OutlierReport outliers(
            @RequestParam(defaultValue = "" + OutlierParams.DEFAULT_MIN_GROUP_SIZE) int minGroupSize,
            @RequestParam(defaultValue = "" + OutlierParams.DEFAULT_Z_THRESHOLD) double zThreshold,
            @RequestParam(defaultValue = "" + OutlierParams.DEFAULT_MIN_DEVIATION_PCT) double minDeviationPct,
            @RequestParam(defaultValue = "any") String direction,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String jobLevel,
            @RequestParam(required = false) String jobTitle,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        Outlier.Direction dir = switch (direction.toLowerCase()) {
            case "any" -> null;
            case "high" -> Outlier.Direction.HIGH;
            case "low" -> Outlier.Direction.LOW;
            default -> throw new IllegalArgumentException("direction must be 'any', 'high' or 'low'");
        };
        return service.outliers(new InsightsFilter(country, department, jobLevel, jobTitle, includeInactive),
                new OutlierParams(minGroupSize, zThreshold, minDeviationPct), dir, limit);
    }
}
