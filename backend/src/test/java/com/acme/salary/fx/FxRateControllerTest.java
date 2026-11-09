package com.acme.salary.fx;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class FxRateControllerTest {

    @Autowired
    MockMvc mvc;

    private static String rate(String value) {
        return "{\"ratePerUsd\":" + value + "}";
    }

    @Test
    void listsSeededRatesIncludingMinorUnitDigits() throws Exception {
        mvc.perform(get("/api/fx-rates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(9))
                .andExpect(jsonPath("$[?(@.currency=='INR')].ratePerUsd").value(83))
                .andExpect(jsonPath("$[?(@.currency=='JPY')].fractionDigits").value(0))
                .andExpect(jsonPath("$[?(@.currency=='USD')].ratePerUsd").value(1));
    }

    @Test
    void updatesAnExistingRate() throws Exception {
        mvc.perform(put("/api/fx-rates/INR").contentType(APPLICATION_JSON).content(rate("84.25")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ratePerUsd").value(84.25));

        mvc.perform(get("/api/fx-rates")).andExpect(jsonPath("$[?(@.currency=='INR')].ratePerUsd").value(84.25));
    }

    @Test
    void addingACurrencyMakesItUsableForNewEmployees() throws Exception {
        String chfEmployee = """
                {"firstName":"Max","lastName":"Frei","email":"max@acme.test","country":"CH",
                 "department":"Sales","jobTitle":"Account Executive","jobLevel":"L3",
                 "currency":"CHF","salaryMinor":9000000,"hireDate":"2023-02-01"}""";

        mvc.perform(post("/api/employees").contentType(APPLICATION_JSON).content(chfEmployee))
                .andExpect(status().isBadRequest());

        mvc.perform(put("/api/fx-rates/chf").contentType(APPLICATION_JSON).content(rate("0.88")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("CHF"))
                .andExpect(jsonPath("$.fractionDigits").value(2));

        mvc.perform(post("/api/employees").contentType(APPLICATION_JSON).content(chfEmployee))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsUnknownCurrencyCode() throws Exception {
        mvc.perform(put("/api/fx-rates/ZZZ").contentType(APPLICATION_JSON).content(rate("2")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void usdMustStayAtOne() throws Exception {
        mvc.perform(put("/api/fx-rates/USD").contentType(APPLICATION_JSON).content(rate("2")))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/fx-rates/USD").contentType(APPLICATION_JSON).content(rate("1.0")))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsNonPositiveOrOverPreciseRates() throws Exception {
        mvc.perform(put("/api/fx-rates/INR").contentType(APPLICATION_JSON).content(rate("0")))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/fx-rates/INR").contentType(APPLICATION_JSON).content(rate("-3")))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/fx-rates/INR").contentType(APPLICATION_JSON).content(rate("83.1234567")))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/fx-rates/INR").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }
}
