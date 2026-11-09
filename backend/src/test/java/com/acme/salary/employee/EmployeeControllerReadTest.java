package com.acme.salary.employee;

import static com.acme.salary.employee.TestEmployees.anEmployee;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class EmployeeControllerReadTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    EmployeeRepository repository;

    Long ashaId;

    @BeforeEach
    void seed() {
        repository.deleteAll();
        ashaId = repository.saveAndFlush(anEmployee().name("Asha", "Rao").country("IN").salary(2_000_000).build())
                .getId();
        repository.saveAndFlush(anEmployee().name("Bruno", "Keller").country("DE").salary(6_000_000).build());
    }

    @Test
    void listsEmployeesWithPagingMetadata() throws Exception {
        mvc.perform(get("/api/employees").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].lastName").value("Keller"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void filtersByCountryParameter() throws Exception {
        mvc.perform(get("/api/employees").param("country", "IN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].firstName").value("Asha"))
                .andExpect(jsonPath("$.content[0].salaryMinor").value(2_000_000));
    }

    @Test
    void returnsOneEmployee() throws Exception {
        mvc.perform(get("/api/employees/{id}", ashaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").exists())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void unknownEmployeeIs404ProblemResponse() throws Exception {
        mvc.perform(get("/api/employees/{id}", 999_999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Employee 999999 not found"));
    }

    @Test
    void invalidSortIs400() throws Exception {
        mvc.perform(get("/api/employees").param("sort", "ssn"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void oversizedPageIs400() throws Exception {
        mvc.perform(get("/api/employees").param("size", "5000"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidStatusValueIs400() throws Exception {
        mvc.perform(get("/api/employees").param("status", "BOGUS"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void salaryHistoryOfUnknownEmployeeIs404() throws Exception {
        mvc.perform(get("/api/employees/{id}/salary-history", 999_999))
                .andExpect(status().isNotFound());
    }
}
