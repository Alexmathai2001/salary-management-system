package com.acme.salary.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.salary.common.ConflictException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Pure unit tests of the write rules: no Spring, no database, fixed clock. */
class EmployeeServiceWriteTest {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2025-06-15T10:00:00Z"), ZoneOffset.UTC);

    EmployeeRepository employees = mock(EmployeeRepository.class);
    SalaryHistoryRepository history = mock(SalaryHistoryRepository.class);
    com.acme.salary.fx.FxRateRepository fxRates = mock(com.acme.salary.fx.FxRateRepository.class);
    EmployeeService service = new EmployeeService(employees, history, fxRates, CLOCK);

    @BeforeEach
    void saveAssignsAnId() {
        when(fxRates.existsById("INR")).thenReturn(true);
        when(employees.save(any(Employee.class))).thenAnswer(inv -> {
            Employee e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(42L);
            }
            return e;
        });
    }

    private static CreateEmployeeRequest createRequest(String email) {
        return new CreateEmployeeRequest("  Asha ", "Rao", email, "IN", "Engineering", "Backend Engineer", "L4",
                "INR", 2_000_000L, LocalDate.of(2023, 2, 1));
    }

    private static Employee existing(long salary) {
        Employee e = new Employee();
        e.setId(7L);
        e.setCurrency("INR");
        e.setSalaryMinor(salary);
        e.setHireDate(LocalDate.of(2023, 2, 1));
        e.setStatus(EmployeeStatus.ACTIVE);
        return e;
    }

    // ---- create ----

    @Test
    void createNormalisesFieldsAssignsCodeAndRecordsInitialSalary() {
        Employee created = service.create(createRequest("Asha@ACME.test"));

        assertThat(created.getEmployeeCode()).isEqualTo("EMP-000042");
        assertThat(created.getFirstName()).isEqualTo("Asha");
        assertThat(created.getEmail()).isEqualTo("asha@acme.test");
        assertThat(created.getStatus()).isEqualTo(EmployeeStatus.ACTIVE);

        ArgumentCaptor<SalaryHistory> captor = ArgumentCaptor.forClass(SalaryHistory.class);
        verify(history).save(captor.capture());
        SalaryHistory initial = captor.getValue();
        assertThat(initial.getOldSalaryMinor()).isNull();
        assertThat(initial.getNewSalaryMinor()).isEqualTo(2_000_000L);
        assertThat(initial.getEffectiveDate()).isEqualTo(LocalDate.of(2023, 2, 1));
        assertThat(initial.getReason()).isEqualTo("Initial salary");
    }

    @Test
    void createRejectsDuplicateEmail() {
        when(employees.existsByEmailIgnoreCase("asha@acme.test")).thenReturn(true);

        assertThatThrownBy(() -> service.create(createRequest("asha@acme.test")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists");
        verify(employees, never()).save(any());
    }

    @Test
    void createRejectsCurrencyWithoutAnFxRate() {
        var chf = new CreateEmployeeRequest("Max", "Frei", "max@acme.test", "CH", "Sales", "Account Executive", "L3",
                "CHF", 9_000_000L, LocalDate.of(2023, 2, 1));

        assertThatThrownBy(() -> service.create(chf))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported currency CHF");
        verify(employees, never()).save(any());
    }

    // ---- salary change ----

    @Test
    void changeSalaryUpdatesCurrentValueAndAppendsHistory() {
        Employee e = existing(2_000_000L);
        when(employees.findById(7L)).thenReturn(Optional.of(e));

        service.changeSalary(7L, new ChangeSalaryRequest(2_400_000L, LocalDate.of(2025, 4, 1), " Annual review "));

        assertThat(e.getSalaryMinor()).isEqualTo(2_400_000L);
        ArgumentCaptor<SalaryHistory> captor = ArgumentCaptor.forClass(SalaryHistory.class);
        verify(history).save(captor.capture());
        assertThat(captor.getValue().getOldSalaryMinor()).isEqualTo(2_000_000L);
        assertThat(captor.getValue().getNewSalaryMinor()).isEqualTo(2_400_000L);
        assertThat(captor.getValue().getReason()).isEqualTo("Annual review");
    }

    @Test
    void changeSalaryRejectsNoOpChange() {
        when(employees.findById(7L)).thenReturn(Optional.of(existing(2_000_000L)));

        assertThatThrownBy(() -> service.changeSalary(7L,
                new ChangeSalaryRequest(2_000_000L, LocalDate.of(2025, 4, 1), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same as the current");
        verify(history, never()).save(any());
    }

    @Test
    void changeSalaryRejectsFutureEffectiveDate() {
        when(employees.findById(7L)).thenReturn(Optional.of(existing(2_000_000L)));

        assertThatThrownBy(() -> service.changeSalary(7L,
                new ChangeSalaryRequest(2_100_000L, LocalDate.of(2025, 6, 16), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("future");
    }

    @Test
    void changeSalaryAllowsEffectiveDateOfToday() {
        Employee e = existing(2_000_000L);
        when(employees.findById(7L)).thenReturn(Optional.of(e));

        service.changeSalary(7L, new ChangeSalaryRequest(2_100_000L, LocalDate.of(2025, 6, 15), null));

        assertThat(e.getSalaryMinor()).isEqualTo(2_100_000L);
    }

    @Test
    void changeSalaryRejectsDateBeforeHire() {
        when(employees.findById(7L)).thenReturn(Optional.of(existing(2_000_000L)));

        assertThatThrownBy(() -> service.changeSalary(7L,
                new ChangeSalaryRequest(2_100_000L, LocalDate.of(2023, 1, 31), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("before the hire date");
    }

    @Test
    void changeSalaryRejectsInactiveEmployee() {
        Employee e = existing(2_000_000L);
        e.setStatus(EmployeeStatus.INACTIVE);
        when(employees.findById(7L)).thenReturn(Optional.of(e));

        assertThatThrownBy(() -> service.changeSalary(7L,
                new ChangeSalaryRequest(2_100_000L, LocalDate.of(2025, 4, 1), null)))
                .isInstanceOf(ConflictException.class);
    }

    // ---- update / deactivate ----

    @Test
    void updateAllowsKeepingOwnEmailButRejectsAnotherEmployeesEmail() {
        Employee e = existing(2_000_000L);
        when(employees.findById(7L)).thenReturn(Optional.of(e));
        when(employees.existsByEmailIgnoreCaseAndIdNot("taken@acme.test", 7L)).thenReturn(true);

        var ok = new UpdateEmployeeRequest("A", "B", "mine@acme.test", "IN", "Eng", "Dev", "L2",
                LocalDate.of(2023, 2, 1));
        var clash = new UpdateEmployeeRequest("A", "B", "taken@acme.test", "IN", "Eng", "Dev", "L2",
                LocalDate.of(2023, 2, 1));

        assertThat(service.update(7L, ok).getEmail()).isEqualTo("mine@acme.test");
        assertThatThrownBy(() -> service.update(7L, clash)).isInstanceOf(ConflictException.class);
    }

    @Test
    void deactivateIsIdempotentAndKeepsTheRow() {
        Employee e = existing(2_000_000L);
        when(employees.findById(7L)).thenReturn(Optional.of(e));

        service.deactivate(7L);
        service.deactivate(7L);

        assertThat(e.getStatus()).isEqualTo(EmployeeStatus.INACTIVE);
        verify(employees, never()).delete(any(Employee.class));
        verify(employees, never()).deleteById(any());
    }
}
