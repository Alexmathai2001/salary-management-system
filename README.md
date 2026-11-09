# Salary Management

Web-based salary management for an HR Manager at an organisation of about 10,000 employees across multiple countries.

- Requirements and scope: [`docs/requirements.md`](docs/requirements.md)

## Backend (Java 21, Spring Boot 4, SQLite)

```bash
cd backend

# run the tests
./mvnw test

# start the API (an empty database is seeded automatically with 10,000 sample employees)
./mvnw spring-boot:run
```

### Sample data

The sample data is two plain CSV files you can open and read:

- `backend/src/main/resources/db/seed/employees.csv` (10,000 employees)
- `backend/src/main/resources/db/seed/salary_history.csv` (each employee's salary changes)

The FX rates are in `backend/src/main/resources/db/changelog/002-fx-rates.sql`.

[Liquibase](https://www.liquibase.com) loads the CSVs through the `003-seed-sample-data` changeset (context `seed`, on by default).
It runs only on an **empty** database and is skipped, never merged, if employees already exist, so it cannot touch real data.
A fresh start takes about 7 seconds including the seed.

- **Start without sample data:** `SPRING_LIQUIBASE_CONTEXTS=no-seed ./mvnw spring-boot:run`. Use any value other than `seed`.
  Do not leave it empty: Liquibase reads an empty context list as "run everything", which includes the seed.
- **Start over:** stop the app and delete `backend/data/salary.db`.
- **Change the sample data:** the files are generated, not hand-edited. Change the deterministic generator in
  `backend/src/test/java/com/acme/salary/seed/` and re-run it, then commit the new CSVs (a test fails if they drift):

  ```bash
  ./mvnw -q test-compile exec:java -Dexec.classpathScope=test -Dexec.mainClass=com.acme.salary.seed.SeedFileWriter
  ```

The database file defaults to `backend/data/salary.db`. Override it with `SPRING_DATASOURCE_URL`.

## Frontend (Angular 21, Angular Material)

```bash
cd frontend
npm install

# dev server on http://localhost:4200, proxying /api to the backend on :8080
npm start

# if something else already uses :8080, run the backend elsewhere (e.g. --server.port=8081) and point the proxy at it
API_TARGET=http://localhost:8081 npm start

npm test          # unit and component tests (Vitest)
npm run build     # production build in frontend/dist
```

Screens: **Employees** (search, filter, sort, page, add/edit, salary change with history, deactivate, CSV export),
**Insights** (headline figures, median pay by country/department/level/title, salary distribution, highest and lowest paid),
**Pay outliers** and **FX rates**.

### API

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/employees` | Search, filter, sort, paginate (`q`, `country`, `department`, `jobTitle`, `jobLevel`, `status`, `page`, `size`, `sort`, `direction`) |
| GET | `/api/employees/export.csv` | CSV of everything matching the same filters (Excel-safe) |
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
| GET | `/api/insights/outliers` | Employees paid far from peers (same title, level, country) |
| GET | `/api/insights/earners` | Top or bottom paid (`order=top\|bottom`, `limit`) |

Insight endpoints accept the drill-down filters `country`, `department`, `jobLevel`, `jobTitle` and `includeInactive`
(inactive employees are excluded by default). All insight money values are **USD cents**, converted with the FX table.

Salaries are integers in minor units of the employee's currency (e.g. cents; JPY has none).
