# Salary Management: Requirements (v1)

**Persona:** HR Manager at ACME (10,000 employees, multiple countries)
**Status:** Draft, written before implementation

## 1. Goal
Replace the Excel-based salary process with a web app where the HR Manager can **maintain salary data** for all employees and **answer questions about how the org pays people**, without building pivot tables by hand.

Success looks like:
- Finding or updating any employee's salary takes seconds, not a spreadsheet hunt.
- Common questions ("What's the median engineer salary in India vs. Germany?", "Who is paid below their band?") are answered in 2-3 clicks.
- The app stays fast at 10,000 employees (list and insight pages load in under 1 second).

## 2. Users and key questions
Single persona: HR Manager, with full access. Questions the product must answer:
1. How much do we spend on salaries, in total and per country, department and job level?
2. How is pay distributed (min / median / avg / max, percentiles) for a role, level or country?
3. Who are the outliers: very high, very low, or inconsistent with peers in the same role and level?
4. How has an individual's salary changed over time?

## 3. Scope and features (in)

| # | Feature | Notes |
|---|---------|-------|
| F1 | **Employee directory**: search, filter (country, department, job title, level, status), sort, server-side pagination | Core daily workflow; must handle 10k rows |
| F2 | **Employee CRUD**: add, edit, deactivate (soft delete) | Validation on required fields and salary > 0 |
| F3 | **Salary update with history**: every change records old and new value, effective date and reason | Answers "how has pay changed?" and gives an audit trail |
| F4 | **Multi-country, multi-currency**: each salary is stored in its local currency, and reports convert to a single reporting currency (USD) using a fixed rate table | Needed because employees span countries |
| F5 | **Insights dashboard**: headline totals, salary stats by country / department / level, distribution histogram, top and bottom earners | The "how do we pay people" answer |
| F6 | **Outlier / pay-equity view**: employees far from the median of their role + level + country group | Robust median/MAD rule with a minimum percentage gap. Originally a ±2 standard deviation rule; changed after finding it cannot flag anyone in small groups |
| F7 | **CSV export** of the filtered employee list | Lets HR keep using Excel for ad-hoc sharing; cheap and safe (read-only) |
| F8 | **Seed data**: 10,000 realistic, deterministic employees in plain CSV files, loaded by a Liquibase changeset into an empty database | Generated from a fixed random seed; the same files serve local runs and the demo |

## 4. Deliberately left out (and why)

| Out of scope | Reasoning |
|--------------|-----------|
| Authentication, roles, permissions | Single persona. A real deployment needs SSO and RBAC, but it adds no insight into the core problem. It is listed as the first follow-up. |
| Payroll processing (tax, deductions, payslips, payments) | Different product with heavy legal and country-specific complexity. We manage salary *data*, not pay runs. |
| Bonuses, equity, benefits (total compensation) | Doubles the data model. The base-salary model is built so components can be added later. |
| Live FX rates | A fixed, editable rate table keeps reports deterministic and testable. Live rates add an external dependency for little benefit here. |
| CSV import | A one-off migration, not a daily workflow, and the most validation-heavy feature (parsing, per-row errors, duplicates, currency checks). The seed script covers the initial load and the validated API/UI covers data entry. First candidate to add if real Excel data must be migrated. |
| Approval workflows for raises | Valuable at scale, but the HR Manager is the sole user and approver. |
| Performance reviews / compensation planning cycles | Adjacent product area. |
| Mobile-first UI and i18n | The HR Manager works on a desktop. The UI is English-only. |
| Org hierarchy / manager reporting lines | Not needed to answer the pay questions above. |

## 5. Non-functional requirements
- **Performance:** list, filter and aggregate queries must be fast at 10k rows. Use DB indexes on filter columns, do aggregation in SQL (not in the client), and paginate server-side.
- **Correctness:** salaries are stored as integers in minor units (no float rounding). Salary changes are atomic with their history record.
- **Testability:** deterministic seed and fast unit tests covering validation, history, FX conversion, aggregation and outlier logic.
- **Maintainability:** layered backend (API, service, repository) with the business rules isolated from the HTTP layer.
- **Deployability:** one-command local setup, publicly deployed, with a short video demo.

## 6. Key assumptions
- One current salary per employee, with full history kept in a separate table.
- About 10 countries and 5-6 currencies in the seed data.
- Reporting currency is USD, and the FX table is maintained by HR.
- Salaries are annual.

## 7. Risks and open questions
- **Real data quality** (messy Excel exports): CSV import is out of scope (see section 4), so the initial load is the seed script and data entry goes through validated forms and APIs.
- **Outlier rule is simplistic:** may flag legitimate pay differences, so it is presented as "worth reviewing", not "wrong".
- Should the pay bands per level be defined by HR (a later feature), or derived from the data (v1)? **v1 assumes derived.**
