# JasperReports templates

Drop your `*.jrxml` report designs here. `JasperReportService` (in
`ma.zakaria.tadbirbudget.files.jasper`) loads them from the classpath by path
(e.g. `reports/sample-users.jrxml`), compiles each once (cached), fills it and
exports a PDF.

## Usage

```java
@RequiredArgsConstructor
class UserReportController {
    private final JasperReportService jasper;

    @GetMapping(value = "/api/v1/user/report", produces = MediaType.APPLICATION_PDF_VALUE)
    ResponseEntity<byte[]> report(/* ... */) {
        byte[] pdf = jasper.toPdf(
                "reports/sample-users.jrxml",
                Map.of("REPORT_TITLE", "Users"),
                users);                 // any beans whose getters match the template <field> names
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=users.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
```

- **parameters** → `$P{...}` (title, filters, logos). May be empty.
- **rows** → the `$F{...}` detail data: any `Collection` of beans whose getters match the
  template's `<field>` names. Pass `null`/empty for a parameters-only report.
- Templates are compiled at runtime and cached; authoring is easiest in **Jaspersoft Studio**.

`sample-users.jrxml` is a working example (uid / full name / email / roles) — replace it with
your own designs.
