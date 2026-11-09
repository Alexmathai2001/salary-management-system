package com.acme.salary.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

/** Pure unit tests (no Spring, no database) for paging and sort validation. */
class EmployeeServicePagingTest {

    @Test
    void mapsPublicSortKeyToEntityPropertyWithStableTieBreaker() {
        var pageable = EmployeeService.pageable(2, 50, "salary", true);

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(50);
        assertThat(pageable.getSort().getOrderFor("salaryMinor").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(pageable.getSort().getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void defaultsToSortingByLastName() {
        var pageable = EmployeeService.pageable(0, 25, null, false);

        assertThat(pageable.getSort().getOrderFor("lastName")).isNotNull();
    }

    @Test
    void rejectsUnknownSortKey() {
        assertThatThrownBy(() -> EmployeeService.pageable(0, 25, "password", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot sort by 'password'");
    }

    @Test
    void rejectsOversizedOrInvalidPages() {
        assertThatThrownBy(() -> EmployeeService.pageable(0, 101, "name", false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EmployeeService.pageable(0, 0, "name", false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EmployeeService.pageable(-1, 25, "name", false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
