package com.acme.salary.insights;

import static com.acme.salary.employee.TestEmployees.anEmployee;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.salary.employee.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InsightsControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    EmployeeRepository employees;

    @BeforeEach
    void seed() {
        employees.deleteAll();
        employees.saveAndFlush(anEmployee().country("US").department("Engineering").currency("USD")
                .salary(10_000_000).build());
        employees.saveAndFlush(anEmployee().country("US").department("Sales").currency("USD")
                .salary(6_000_000).build());
        employees.saveAndFlush(anEmployee().country("IN").department("Engineering").currency("INR")
                .salary(830_000_000L).build());
    }

    @Test
    void summaryReturnsUsdCentStats() throws Exception {
        mvc.perform(get("/api/insights/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headcount").value(3))
                .andExpect(jsonPath("$.totalCents").value(26_000_000))
                .andExpect(jsonPath("$.medianCents").value(10_000_000));
    }

    @Test
    void summaryOfNothingIsZerosNotAnError() throws Exception {
        mvc.perform(get("/api/insights/summary").param("country", "ZZ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headcount").value(0))
                .andExpect(jsonPath("$.avgCents").value(0));
    }

    @Test
    void groupsByDimensionAcceptingCamelAndKebabCase() throws Exception {
        mvc.perform(get("/api/insights/by/country"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].key").value("IN"))
                .andExpect(jsonPath("$[1].key").value("US"));

        mvc.perform(get("/api/insights/by/jobLevel")).andExpect(status().isOk());
        mvc.perform(get("/api/insights/by/job-title")).andExpect(status().isOk());
    }

    @Test
    void unknownDimensionIs400() throws Exception {
        mvc.perform(get("/api/insights/by/salary")).andExpect(status().isBadRequest());
    }

    @Test
    void distributionRejectsAbsurdBucketSizes() throws Exception {
        mvc.perform(get("/api/insights/distribution").param("bucketUsd", "5")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/insights/distribution").param("bucketUsd", "99999999"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/insights/distribution").param("bucketUsd", "50000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fromUsd").value(50_000));
    }

    @Test
    void earnersValidatesOrderAndLimit() throws Exception {
        mvc.perform(get("/api/insights/earners").param("order", "middle")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/insights/earners").param("limit", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/insights/earners").param("limit", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/insights/earners").param("limit", "2").param("order", "bottom"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].usdCents").value(6_000_000));
    }
}
