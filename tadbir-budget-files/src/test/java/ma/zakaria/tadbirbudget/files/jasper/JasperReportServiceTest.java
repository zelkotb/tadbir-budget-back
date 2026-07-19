/*
 * Copyright (c) 2026 Zakaria El Kotb. All rights reserved.
 *
 * This source code is the exclusive property of Zakaria El Kotb.
 * Unauthorized copying, modification, distribution, or use of this file,
 * via any medium, is strictly prohibited without the prior written
 * permission of the copyright owner.
 *
 * Author: Zakaria El Kotb <elkotbzakaria@gmail.com>
 */
package ma.zakaria.tadbirbudget.files.jasper;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JasperReportServiceTest {

    private final JasperReportService service = new JasperReportService();

    /** A plain bean whose getters match the template's {@code <field>} names. */
    public static class Row {
        private final String uid;
        private final String fullName;
        private final String email;
        private final List<String> roles;

        Row(String uid, String fullName, String email, List<String> roles) {
            this.uid = uid;
            this.fullName = fullName;
            this.email = email;
            this.roles = roles;
        }

        public String getUid()          { return uid; }
        public String getFullName()     { return fullName; }
        public String getEmail()        { return email; }
        public List<String> getRoles()  { return roles; }
    }

    @Test
    void rendersSampleTemplateToPdf() {
        List<Row> rows = List.of(
                new Row("pm.admin", "Platform Admin", "admin@tadbir-budget.ma", List.of("ROLE_ADMIN")),
                new Row("j.doe", "Jane Doe", "jane@example.com", List.of("ROLE_EMPLOYEE")));

        byte[] pdf = service.toPdf(
                "reports/sample-users.jrxml",
                Map.of("REPORT_TITLE", "Users"),
                rows);

        assertTrue(pdf.length > 500, "PDF should be non-trivial in size");
        assertEquals("%PDF-", new String(pdf, 0, 5, StandardCharsets.ISO_8859_1),
                "output should start with the PDF magic header");
    }

    @Test
    void rendersParametersOnlyReportWithNoRows() {
        byte[] pdf = service.toPdf(
                "reports/sample-users.jrxml",
                Map.of("REPORT_TITLE", "Empty"),
                List.of());

        assertEquals("%PDF-", new String(pdf, 0, 5, StandardCharsets.ISO_8859_1));
    }
}
