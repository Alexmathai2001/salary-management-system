package com.acme.salary.employee;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/** End-to-end HTTP -> service -> SQLite tests for the write endpoints. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EmployeeControllerWriteTest {

    static final String VALID = """
            {"firstName":"Asha","lastName":"Rao","email":"asha@acme.test","country":"IN",
             "department":"Engineering","jobTitle":"Backend Engineer","jobLevel":"L4",
             "currency":"INR","salaryMinor":2000000,"hireDate":"2023-02-01"}""";

    @Autowired
    MockMvc mvc;

    @Autowired
    EmployeeRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    private long createAsha() throws Exception {
        MvcResult result = mvc.perform(post("/api/employees").contentType(APPLICATION_JSON).content(VALID))
                .andExpect(status().isCreated())
                .andReturn();
        String location = result.getResponse().getHeader("Location");
        return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
    }

    @Test
    void createReturns201WithLocationAndGeneratedCode() throws Exception {
        mvc.perform(post("/api/employees").contentType(APPLICATION_JSON).content(VALID))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/employees/")))
                .andExpect(jsonPath("$.employeeCode").value(org.hamcrest.Matchers.startsWith("EMP-")))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void createWritesInitialSalaryHistory() throws Exception {
        long id = createAsha();

        mvc.perform(get("/api/employees/{id}/salary-history", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].oldSalaryMinor").doesNotExist())
                .andExpect(jsonPath("$[0].newSalaryMinor").value(2_000_000))
                .andExpect(jsonPath("$[0].reason").value("Initial salary"));
    }

    @Test
    void createRejectsInvalidInputWithProblemResponse() throws Exception {
        String invalid = VALID.replace("\"salaryMinor\":2000000", "\"salaryMinor\":-5")
                .replace("\"country\":\"IN\"", "\"country\":\"india\"")
                .replace("asha@acme.test", "not-an-email");

        mvc.perform(post("/api/employees").contentType(APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors.salaryMinor").value("must be greater than zero"))
                .andExpect(jsonPath("$.errors.country").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.firstName").doesNotExist());
    }

    @Test
    void duplicateEmailIs409() throws Exception {
        createAsha();

        mvc.perform(post("/api/employees").contentType(APPLICATION_JSON)
                        .content(VALID.replace("asha@acme.test", "ASHA@acme.test")))
                .andExpect(status().isConflict());
    }

    @Test
    void salaryChangeUpdatesEmployeeAndHistory() throws Exception {
        long id = createAsha();

        mvc.perform(post("/api/employees/{id}/salary", id).contentType(APPLICATION_JSON)
                        .content("{\"newSalaryMinor\":2400000,\"effectiveDate\":\"2024-04-01\",\"reason\":\"Promotion\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salaryMinor").value(2_400_000));

        mvc.perform(get("/api/employees/{id}/salary-history", id))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].newSalaryMinor").value(2_400_000))
                .andExpect(jsonPath("$[0].oldSalaryMinor").value(2_000_000))
                .andExpect(jsonPath("$[0].reason").value("Promotion"));
    }

    @Test
    void invalidSalaryChangeIs400AndLeavesSalaryUntouched() throws Exception {
        long id = createAsha();

        mvc.perform(post("/api/employees/{id}/salary", id).contentType(APPLICATION_JSON)
                        .content("{\"newSalaryMinor\":2000000,\"effectiveDate\":\"2024-04-01\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/employees/{id}", id)).andExpect(jsonPath("$.salaryMinor").value(2_000_000));
    }

    @Test
    void updateChangesProfileButNeverTheSalary() throws Exception {
        long id = createAsha();

        mvc.perform(put("/api/employees/{id}", id).contentType(APPLICATION_JSON)
                        .content("""
                                {"firstName":"Asha","lastName":"Rao-Iyer","email":"asha@acme.test","country":"DE",
                                 "department":"Platform","jobTitle":"Staff Engineer","jobLevel":"L5",
                                 "hireDate":"2023-02-01","salaryMinor":999}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Rao-Iyer"))
                .andExpect(jsonPath("$.country").value("DE"))
                .andExpect(jsonPath("$.salaryMinor").value(2_000_000));
    }

    @Test
    void deleteDeactivatesAndKeepsTheEmployeeAndHistory() throws Exception {
        long id = createAsha();

        mvc.perform(delete("/api/employees/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        mvc.perform(get("/api/employees/{id}", id)).andExpect(status().isOk());
        mvc.perform(get("/api/employees/{id}/salary-history", id)).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void inactiveEmployeeSalaryChangeIs409UntilReactivated() throws Exception {
        long id = createAsha();
        mvc.perform(delete("/api/employees/{id}", id));
        String change = "{\"newSalaryMinor\":2400000,\"effectiveDate\":\"2024-04-01\"}";

        mvc.perform(post("/api/employees/{id}/salary", id).contentType(APPLICATION_JSON).content(change))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/employees/{id}/reactivate", id)).andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(post("/api/employees/{id}/salary", id).contentType(APPLICATION_JSON).content(change))
                .andExpect(status().isOk());
    }

    @Test
    void unknownEmployeeIs404OnWrites() throws Exception {
        mvc.perform(delete("/api/employees/{id}", 999_999)).andExpect(status().isNotFound());
        mvc.perform(post("/api/employees/{id}/salary", 999_999).contentType(APPLICATION_JSON)
                        .content("{\"newSalaryMinor\":1,\"effectiveDate\":\"2024-04-01\"}"))
                .andExpect(status().isNotFound());
    }
}
