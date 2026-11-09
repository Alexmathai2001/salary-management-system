-- Baseline migration; domain tables are added in subsequent migrations.
CREATE TABLE app_metadata (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
);
