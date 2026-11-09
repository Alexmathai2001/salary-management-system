# Salary Management: frontend

Angular 21 (standalone components, signals, zoneless) with Angular Material. See the [root README](../README.md) for how to
run it.

```
src/app/
  core/       API services, models, money and chart helpers, shared view-state logic
  features/   employees/ (list, detail, form, salary dialog), insights/ (page + charts), outliers/, fx/
  shared/     small reusable pieces (confirm dialog)
```

`npm start` (dev server + API proxy), `npm test` (Vitest), `npm run build`.
