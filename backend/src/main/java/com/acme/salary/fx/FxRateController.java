package com.acme.salary.fx;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fx-rates")
public class FxRateController {

    private final FxRateService service;

    public FxRateController(FxRateService service) {
        this.service = service;
    }

    @GetMapping
    public List<FxRateResponse> list() {
        return service.list().stream().map(FxRateResponse::from).toList();
    }

    @PutMapping("/{currency}")
    public FxRateResponse upsert(@PathVariable String currency, @Valid @RequestBody UpdateFxRateRequest request) {
        return FxRateResponse.from(service.upsert(currency.toUpperCase(), request.ratePerUsd()));
    }
}
