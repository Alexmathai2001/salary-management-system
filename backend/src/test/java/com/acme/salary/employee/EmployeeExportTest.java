package com.acme.salary.employee;

import static com.acme.salary.employee.TestEmployees.anEmployee;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
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
class EmployeeExportTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    EmployeeRepository repository;

    @BeforeEach
    void seed() {
        repository.deleteAll();
        repository.saveAndFlush(anEmployee().name("Asha", "Rao").country("IN").currency("INR").salary(830_000_000).build());
        repository.saveAndFlush(anEmployee().name("Bruno", "Keller").country("DE").currency("EUR").salary(6_000_000).build());
        repository.saveAndFlush(anEmployee().name("Chen", "Wei").country("IN").currency("INR").salary(500_000_000)
                .status(EmployeeStatus.INACTIVE).build());
    }

    private String export(String... params) throws Exception {
        var request = get("/api/employees/export.csv");
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        byte[] body = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        return new String(body, StandardCharsets.UTF_8);
    }

    @Test
    void downloadsACsvAttachment() throws Exception {
        mvc.perform(get("/api/employees/export.csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.matchesPattern(
                        "attachment; filename=\"employees-\\d{4}-\\d{2}-\\d{2}\\.csv\"")));
    }

    @Test
    void includesEveryMatchingEmployeeNotJustOnePage() throws Exception {
        String csv = export();

        assertThat(csv.split("\r\n")).hasSize(1 + 3); // header + all three
    }

    @Test
    void appliesTheSameFiltersAsTheDirectory() throws Exception {
        String csv = export("country", "IN", "status", "ACTIVE");

        assertThat(csv).contains("Asha").doesNotContain("Bruno").doesNotContain("Chen");
    }

    @Test
    void honoursTheRequestedSortOrder() throws Exception {
        String ascending = export("sort", "name", "direction", "asc");
        String descending = export("sort", "name", "direction", "desc");

        assertThat(ascending.indexOf("Keller")).isLessThan(ascending.indexOf("Rao"));
        assertThat(descending.indexOf("Rao")).isLessThan(descending.indexOf("Keller"));
    }

    @Test
    void writesSalaryInMajorUnitsOfEachCurrency() throws Exception {
        String csv = export("country", "DE");

        assertThat(csv).contains(",EUR,60000.00,");
    }

    @Test
    void rejectsAnInvalidSortKeyLikeTheListDoes() throws Exception {
        mvc.perform(get("/api/employees/export.csv").param("sort", "ssn")).andExpect(status().isBadRequest());
    }
}
