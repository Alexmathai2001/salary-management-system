package com.acme.salary.employee;

import com.acme.salary.common.PageResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @GetMapping("/{id}")
    public EmployeeResponse get(@PathVariable Long id) {
        return EmployeeResponse.from(service.get(id));
    }

    @GetMapping("/{id}/salary-history")
    public List<SalaryHistoryResponse> salaryHistory(@PathVariable Long id) {
        return service.salaryHistory(id).stream().map(SalaryHistoryResponse::from).toList();
    }
}
