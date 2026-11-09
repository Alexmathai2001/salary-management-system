package com.acme.salary.employee;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalaryHistoryRepository extends JpaRepository<SalaryHistory, Long> {

    List<SalaryHistory> findByEmployeeIdOrderByEffectiveDateDescIdDesc(Long employeeId);
}
