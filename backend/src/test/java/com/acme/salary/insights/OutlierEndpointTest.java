package com.acme.salary.insights;

import static com.acme.salary.employee.TestEmployees.anEmployee;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.salary.employee.EmployeeRepository;
import com.acme.salary.employee.EmployeeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** End to end: SQLite -> USD conversion -> detector -> JSON. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OutlierEndpointTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    EmployeeRepository employees;

    private void save(String country, String currency, long salaryMinor, EmployeeStatus status) {
        employees.saveAndFlush(anEmployee().country(country).jobTitle("Engineer").level("L3").currency(currency)
                .salary(salaryMinor).status(status).build());
    }

    @BeforeEach
    void seed() {
        employees.deleteAll();
        // Seven US peers (USD cents): median 100k, one at 200k.
        for (long dollars : new long[] {98_000, 100_000, 101_000, 99_000, 102_000, 100_000, 200_000}) {
            save("US", "USD", dollars * 100, EmployeeStatus.ACTIVE);
        }
        // An inactive extreme must not count as a peer or be reported by default.
        save("US", "USD", 5_000_000, EmployeeStatus.INACTIVE);
        // Two Indian employees: a group far too small to judge.
        save("IN", "INR", 100_000_000L, EmployeeStatus.ACTIVE);
        save("IN", "INR", 900_000_000L, EmployeeStatus.ACTIVE);
    }

    @Test
    void reportsTheOutlierAndHowManyPeopleCouldNotBeJudged() throws Exception {
        mvc.perform(get("/api/insights/outliers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFlagged").value(1))
                .andExpect(jsonPath("$.employeesEvaluated").value(7))
                .andExpect(jsonPath("$.employeesNotEvaluated").value(2))
                .andExpect(jsonPath("$.groupsEvaluated").value(1))
                .andExpect(jsonPath("$.outliers[0].direction").value("HIGH"))
                .andExpect(jsonPath("$.outliers[0].deviationPct").value(100.0))
                .andExpect(jsonPath("$.outliers[0].usdCents").value(20_000_000))
                .andExpect(jsonPath("$.outliers[0].peerMedianCents").value(10_000_000))
                .andExpect(jsonPath("$.params.minGroupSize").value(5));
    }

    @Test
    void includingInactiveEmployeesChangesThePeerGroup() throws Exception {
        mvc.perform(get("/api/insights/outliers").param("includeInactive", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeesEvaluated").value(8));
    }

    @Test
    void directionFiltersTheResults() throws Exception {
        mvc.perform(get("/api/insights/outliers").param("direction", "low"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFlagged").value(0))
                .andExpect(jsonPath("$.outliers.length()").value(0));
        mvc.perform(get("/api/insights/outliers").param("direction", "HIGH"))
                .andExpect(jsonPath("$.totalFlagged").value(1));
    }

    @Test
    void tighteningTheParametersChangesTheOutcome() throws Exception {
        mvc.perform(get("/api/insights/outliers").param("minDeviationPct", "150"))
                .andExpect(jsonPath("$.totalFlagged").value(0));
        mvc.perform(get("/api/insights/outliers").param("minGroupSize", "10"))
                .andExpect(jsonPath("$.employeesEvaluated").value(0))
                .andExpect(jsonPath("$.employeesNotEvaluated").value(9));
    }

    @Test
    void drillDownFiltersApply() throws Exception {
        mvc.perform(get("/api/insights/outliers").param("country", "IN"))
                .andExpect(jsonPath("$.totalFlagged").value(0))
                .andExpect(jsonPath("$.employeesNotEvaluated").value(2));
    }

    @Test
    void rejectsInvalidParameters() throws Exception {
        mvc.perform(get("/api/insights/outliers").param("direction", "sideways")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/insights/outliers").param("minGroupSize", "1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/insights/outliers").param("zThreshold", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/insights/outliers").param("limit", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/insights/outliers").param("limit", "201")).andExpect(status().isBadRequest());
    }
}
