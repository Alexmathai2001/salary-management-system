# Salary Management

Web-based salary management for an HR Manager at an organisation of about 10,000 employees across multiple countries.

- Requirements and scope: [`docs/requirements.md`](docs/requirements.md)

## Backend (Java 21, Spring Boot 4, SQLite)

```bash
cd backend

# run the tests
./mvnw test

# start the API with 10,000 seeded employees (skipped automatically if data already exists)
./mvnw spring-boot:run -Dspring-boot.run.arguments="--app.seed.enabled=true"
```

Seed options (all optional): `--app.seed.count=10000`, `--app.seed.rng-seed=42`, `--app.seed.reset=true` (replace existing data).
The seed is deterministic: the same options always produce the same data.

The database file defaults to `backend/data/salary.db`. Override it with `SPRING_DATASOURCE_URL`.

### API

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/employees` | Search, filter, sort, paginate (`q`, `country`, `department`, `jobTitle`, `jobLevel`, `status`, `page`, `size`, `sort`, `direction`) |
| GET | `/api/employees/{id}` | One employee |
| GET | `/api/employees/{id}/salary-history` | Salary changes, newest first |
| POST | `/api/employees` | Create (records the initial salary) |
| PUT | `/api/employees/{id}` | Update profile fields (never salary) |
| POST | `/api/employees/{id}/salary` | Change salary (audited) |
| DELETE | `/api/employees/{id}` | Deactivate (soft delete) |
| POST | `/api/employees/{id}/reactivate` | Reactivate |

| GET | `/api/fx-rates` | FX table used for reporting (units of currency per 1 USD) |
| PUT | `/api/fx-rates/{currency}` | Create or update a rate. A currency needs a rate before employees can be created in it |
| GET | `/api/insights/summary` | Headcount, total, average, median, p25/p75, min/max pay in USD |
| GET | `/api/insights/by/{dimension}` | The same stats per `country`, `department`, `jobLevel` or `jobTitle` |
| GET | `/api/insights/distribution` | Salary histogram (`bucketUsd`, default 20000) |
| GET | `/api/insights/earners` | Top or bottom paid (`order=top\|bottom`, `limit`) |

Insight endpoints accept the drill-down filters `country`, `department`, `jobLevel`, `jobTitle` and `includeInactive`
(inactive employees are excluded by default). All insight money values are **USD cents**, converted with the FX table.

Salaries are integers in minor units of the employee's currency (e.g. cents; JPY has none).
