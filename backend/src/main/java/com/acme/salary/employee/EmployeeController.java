package com.acme.salary.employee;

import com.acme.salary.common.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeService service;

    public EmployeeController(EmployeeService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<EmployeeResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String jobTitle,
            @RequestParam(required = false) String jobLevel,
            @RequestParam(required = false) EmployeeStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + EmployeeService.DEFAULT_PAGE_SIZE) int size,
            @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "asc") String direction) {
        var filter = new EmployeeFilter(q, country, department, jobTitle, jobLevel, status);
        boolean descending = "desc".equalsIgnoreCase(direction);
        return PageResponse.of(service.search(filter, page, size, sort, descending), EmployeeResponse::from);
    }

    /** CSV of every employee matching the same filters as the list (not just one page). Opens cleanly in Excel. */
    @GetMapping(value = "/export.csv", produces = "text/csv")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String jobTitle,
            @RequestParam(required = false) String jobLevel,
            @RequestParam(required = false) EmployeeStatus status,
            @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "asc") String direction) {
        var filter = new EmployeeFilter(q, country, department, jobTitle, jobLevel, status);
        byte[] csv = EmployeeCsvWriter.write(service.export(filter, sort, "desc".equalsIgnoreCase(direction)));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"employees-" + LocalDate.now(ZoneOffset.UTC) + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv);
    }

    @GetMapping("/{id}")
    public EmployeeResponse get(@PathVariable Long id) {
        return EmployeeResponse.from(service.get(id));
    }

    @GetMapping("/{id}/salary-history")
    public List<SalaryHistoryResponse> salaryHistory(@PathVariable Long id) {
        return service.salaryHistory(id).stream().map(SalaryHistoryResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<EmployeeResponse> create(@Valid @RequestBody CreateEmployeeRequest request) {
        Employee created = service.create(request);
        return ResponseEntity.created(URI.create("/api/employees/" + created.getId()))
                .body(EmployeeResponse.from(created));
    }

    @PutMapping("/{id}")
    public EmployeeResponse update(@PathVariable Long id, @Valid @RequestBody UpdateEmployeeRequest request) {
        return EmployeeResponse.from(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public EmployeeResponse deactivate(@PathVariable Long id) {
        return EmployeeResponse.from(service.deactivate(id));
    }

    @PostMapping("/{id}/reactivate")
    public EmployeeResponse reactivate(@PathVariable Long id) {
        return EmployeeResponse.from(service.reactivate(id));
    }

    @PostMapping("/{id}/salary")
    public EmployeeResponse changeSalary(@PathVariable Long id, @Valid @RequestBody ChangeSalaryRequest request) {
        return EmployeeResponse.from(service.changeSalary(id, request));
    }
}
