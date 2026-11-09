package com.acme.salary.employee;

import java.nio.charset.StandardCharsets;
import java.util.Currency;
import java.util.List;

/**
 * Renders employees as CSV that is safe to open in Excel.
 *
 * <ul>
 *   <li><b>Formula injection:</b> a cell starting with {@code = + - @} (or a tab/CR) would be executed as a formula by
 *       Excel, and names and emails are user-entered. Such cells get a leading apostrophe (the OWASP mitigation).</li>
 *   <li><b>Quoting:</b> RFC 4180 (fields with commas, quotes or line breaks are quoted, quotes doubled).</li>
 *   <li><b>Encoding:</b> UTF-8 with a BOM, otherwise Excel misreads accented names.</li>
 *   <li><b>Money:</b> exact decimal text in the currency's own units (12345 cents -> 123.45; yen has no decimals).</li>
 * </ul>
 */
public final class EmployeeCsvWriter {

    static final String BOM = "﻿";
    static final String HEADER = String.join(",", "Employee code", "First name", "Last name", "Email", "Country",
            "Department", "Job title", "Level", "Currency", "Annual salary", "Hire date", "Status");

    private EmployeeCsvWriter() {
    }

    public static byte[] write(List<Employee> employees) {
        StringBuilder out = new StringBuilder(BOM).append(HEADER).append("\r\n");
        for (Employee e : employees) {
            out.append(String.join(",",
                    cell(e.getEmployeeCode()), cell(e.getFirstName()), cell(e.getLastName()), cell(e.getEmail()),
                    cell(e.getCountry()), cell(e.getDepartment()), cell(e.getJobTitle()), cell(e.getJobLevel()),
                    cell(e.getCurrency()), cell(formatMajor(e.getSalaryMinor(), e.getCurrency())),
                    cell(String.valueOf(e.getHireDate())), cell(e.getStatus().name())))
                    .append("\r\n");
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    static String cell(String value) {
        if (value == null) {
            return "";
        }
        String safe = value;
        if (!safe.isEmpty() && "=+-@\t\r".indexOf(safe.charAt(0)) >= 0) {
            safe = "'" + safe;
        }
        boolean needsQuotes = safe.indexOf(',') >= 0 || safe.indexOf('"') >= 0 || safe.indexOf('\n') >= 0
                || safe.indexOf('\r') >= 0;
        return needsQuotes ? "\"" + safe.replace("\"", "\"\"") + "\"" : safe;
    }

    /** 123456 USD cents -> "1234.56"; 5000000 JPY -> "5000000". Integer arithmetic only, so it is always exact. */
    static String formatMajor(long minor, String currency) {
        int digits = Math.max(Currency.getInstance(currency).getDefaultFractionDigits(), 0);
        if (digits == 0) {
            return Long.toString(minor);
        }
        long scale = (long) Math.pow(10, digits);
        String fraction = String.format("%0" + digits + "d", Math.abs(minor % scale));
        return (minor < 0 ? "-" : "") + Math.abs(minor / scale) + "." + fraction;
    }
}
