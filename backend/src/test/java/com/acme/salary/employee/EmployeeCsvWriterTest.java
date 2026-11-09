package com.acme.salary.employee;

import static com.acme.salary.employee.TestEmployees.anEmployee;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pure unit tests: no Spring, no database. */
class EmployeeCsvWriterTest {

    private static String csv(Employee... employees) {
        return new String(EmployeeCsvWriter.write(List.of(employees)), StandardCharsets.UTF_8);
    }

    private static List<String> lines(String csv) {
        return List.of(csv.substring(1).split("\r\n")); // drop the BOM
    }

    @Test
    void startsWithAByteOrderMarkSoExcelReadsUtf8() {
        byte[] bytes = EmployeeCsvWriter.write(List.of());

        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        assertThat(bytes[1]).isEqualTo((byte) 0xBB);
        assertThat(bytes[2]).isEqualTo((byte) 0xBF);
    }

    @Test
    void writesAHeaderEvenWhenThereAreNoEmployees() {
        assertThat(lines(csv())).containsExactly(EmployeeCsvWriter.HEADER);
    }

    @Test
    void writesOneRowPerEmployeeWithSalaryInMajorUnits() {
        Employee e = anEmployee().name("Asha", "Rao").email("asha@acme.test").country("IN").currency("INR")
                .salary(830_000_050).build();
        e.setEmployeeCode("EMP-000001");

        assertThat(lines(csv(e)).get(1)).isEqualTo(
                "EMP-000001,Asha,Rao,asha@acme.test,IN,Engineering,Software Engineer,L3,INR,8300000.50,2022-01-01,ACTIVE");
    }

    @Test
    void formatsMoneyExactlyForEveryCurrencyShape() {
        assertThat(EmployeeCsvWriter.formatMajor(123_456, "USD")).isEqualTo("1234.56");
        assertThat(EmployeeCsvWriter.formatMajor(7, "USD")).isEqualTo("0.07");
        assertThat(EmployeeCsvWriter.formatMajor(100, "USD")).isEqualTo("1.00");
        assertThat(EmployeeCsvWriter.formatMajor(5_000_000, "JPY")).isEqualTo("5000000"); // no decimals
    }

    @Test
    void quotesFieldsWithCommasQuotesAndLineBreaks() {
        assertThat(EmployeeCsvWriter.cell("Rao, Asha")).isEqualTo("\"Rao, Asha\"");
        assertThat(EmployeeCsvWriter.cell("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(EmployeeCsvWriter.cell("two\nlines")).isEqualTo("\"two\nlines\"");
        assertThat(EmployeeCsvWriter.cell("plain")).isEqualTo("plain");
        assertThat(EmployeeCsvWriter.cell(null)).isEmpty();
    }

    @Test
    void defusesSpreadsheetFormulas() {
        // Names and emails are typed by users; Excel would execute these as formulas.
        assertThat(EmployeeCsvWriter.cell("=HYPERLINK(\"http://evil\",\"x\")")).startsWith("\"'=HYPERLINK");
        assertThat(EmployeeCsvWriter.cell("+1+1")).isEqualTo("'+1+1");
        assertThat(EmployeeCsvWriter.cell("-2+3")).isEqualTo("'-2+3");
        assertThat(EmployeeCsvWriter.cell("@SUM(A1)")).isEqualTo("'@SUM(A1)");
        assertThat(EmployeeCsvWriter.cell("\tcmd")).isEqualTo("'\tcmd");
    }

    @Test
    void doesNotMangleHarmlessValuesThatContainThoseCharactersLater() {
        assertThat(EmployeeCsvWriter.cell("Anne-Marie")).isEqualTo("Anne-Marie");
        assertThat(EmployeeCsvWriter.cell("a=b")).isEqualTo("a=b");
        assertThat(EmployeeCsvWriter.cell("o'neil@acme.test")).isEqualTo("o'neil@acme.test");
    }

    @Test
    void appliesFormulaDefenceToRealRows() {
        Employee e = anEmployee().name("=cmd|' /C calc'!A0", "Rao").email("x@acme.test").build();

        String row = lines(csv(e)).get(1);

        assertThat(row).contains(",'=cmd|' /C calc'!A0,");
        assertThat(row).doesNotContain(",=cmd");
    }

    @Test
    void keepsNonAsciiNamesIntact() {
        Employee e = anEmployee().name("Zoë", "Müller").build();

        assertThat(csv(e)).contains("Zoë,Müller");
    }
}
