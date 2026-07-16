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
package ma.zakaria.tadbirbudget.files.excel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A single Excel sheet definition for {@link ExcelExportService}: a name, an optional title banner,
 * an ordered list of {@link ExcelColumn columns}, and the rows to write.
 *
 * <p>Fluent usage:
 * <pre>{@code
 * ExcelSheet<UserResponse> sheet = ExcelSheet.of("Utilisateurs", rows)
 *     .title("Export des utilisateurs")
 *     .column("Nom", UserResponse::fullName)
 *     .column("Email", UserResponse::email)
 *     .column("Rôles", UserResponse::roles);
 * }</pre>
 *
 * @param <T> the row type
 */
public final class ExcelSheet<T> {

    private final String name;
    private final Iterable<T> rows;
    private final List<ExcelColumn<T>> columns = new ArrayList<>();
    private String title;   // optional banner row above the header; null = none

    private ExcelSheet(String name, Iterable<T> rows) {
        this.name = (name == null || name.isBlank()) ? "Sheet1" : name;
        this.rows = rows == null ? List.of() : rows;
    }

    /** Start a sheet named {@code name} over {@code rows}. */
    public static <T> ExcelSheet<T> of(String name, Iterable<T> rows) {
        return new ExcelSheet<>(name, rows);
    }

    /** Add a column with a header and a value extractor (chainable). */
    public ExcelSheet<T> column(String header, Function<T, Object> value) {
        columns.add(new ExcelColumn<>(header, value));
        return this;
    }

    /** Optional title banner merged across all columns above the header row (chainable). */
    public ExcelSheet<T> title(String title) {
        this.title = title;
        return this;
    }

    public String name() {
        return name;
    }

    public String titleText() {
        return title;
    }

    public List<ExcelColumn<T>> columns() {
        return columns;
    }

    public Iterable<T> rows() {
        return rows;
    }
}
