package com.acme.salary.insights;

import java.util.List;

/**
 * @param employeesEvaluated        employees in a peer group large enough to judge
 * @param employeesNotEvaluated     employees whose peer group is below the minimum size (not judged either way)
 * @param groupsEvaluated           number of peer groups that were large enough
 * @param totalFlagged              all matches (the {@code outliers} list may be truncated by a limit)
 */
public record OutlierReport(int employeesEvaluated, int employeesNotEvaluated, int groupsEvaluated, int totalFlagged,
        OutlierParams params, List<Outlier> outliers) {
}
