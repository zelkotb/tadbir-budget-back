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

import java.util.function.Function;

/**
 * One column of a generic Excel export: a header label and a function that extracts the cell value
 * from a row object. The value may be any of {@code String}, a {@code Number}, {@code Boolean}, a
 * temporal ({@code Instant}/{@code LocalDate}/{@code LocalDateTime}/{@code Date}), an {@code Enum},
 * or {@code null} — {@link ExcelExportService} formats each by its runtime type.
 *
 * @param header the column title shown in the (styled) header row
 * @param value  extracts the raw cell value from a row; may return {@code null}
 * @param <T>    the row type
 */
public record ExcelColumn<T>(String header, Function<T, Object> value) {
}
