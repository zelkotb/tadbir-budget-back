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

import lombok.extern.slf4j.Slf4j;
import ma.zakaria.tadbirbudget.exception.CustomException;
import ma.zakaria.tadbirbudget.exception.ErrorCode;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generic, reusable {@code .xlsx} writer. Feature modules describe <b>what</b> to export as one or
 * more {@link ExcelSheet}s (columns + rows) and this service renders a nicely-styled workbook:
 * a colored/bold header row, an optional title banner, frozen header, an auto-filter over the
 * columns, banded (zebra) rows, type-aware cell formatting (numbers, dates, booleans, enums) and
 * auto-sized columns. Streams with SXSSF so large exports stay low-memory.
 */
@Slf4j
@Service
public class ExcelExportService {

    /** MIME type + suggested extension for callers building the HTTP response. */
    public static final String CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private static final byte[] HEADER_RGB = {0x1F, 0x4E, 0x79};                 // dark blue
    private static final byte[] BAND_RGB   = {(byte) 0xF2, (byte) 0xF6, (byte) 0xFC}; // very light blue
    private static final int MAX_COL_WIDTH = 60 * 256;   // cap auto-size so one long cell can't blow up a column
    private static final int WINDOW        = 1_000;      // rows kept in memory before flushing to disk

    /** Write one or more sheets to {@code out}. Does not close the stream (the caller owns it). */
    public void write(OutputStream out, ExcelSheet<?>... sheets) {
        SXSSFWorkbook wb = new SXSSFWorkbook(WINDOW);
        try {
            Styles styles = new Styles(wb);
            for (ExcelSheet<?> sheet : sheets) {
                writeSheet(wb, styles, sheet);
            }
            wb.write(out);
        } catch (IOException ex) {
            log.error("Excel export failed", ex);
            throw new CustomException(ErrorCode.FILE_STORAGE_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        } finally {
            wb.dispose();   // remove SXSSF temp files
        }
    }

    private <T> void writeSheet(SXSSFWorkbook wb, Styles styles, ExcelSheet<T> sheet) {
        SXSSFSheet ss = wb.createSheet(WorkbookUtil.createSafeSheetName(sheet.name()));
        ss.trackAllColumnsForAutoSizing();
        List<ExcelColumn<T>> cols = sheet.columns();
        int lastCol = Math.max(cols.size() - 1, 0);
        int r = 0;

        // Optional title banner merged across the columns.
        if (sheet.titleText() != null && !sheet.titleText().isBlank()) {
            Row titleRow = ss.createRow(r);
            Cell c = titleRow.createCell(0);
            c.setCellValue(sheet.titleText());
            c.setCellStyle(styles.title);
            if (lastCol > 0) {
                ss.addMergedRegion(new CellRangeAddress(r, r, 0, lastCol));
            }
            titleRow.setHeightInPoints(22);
            r++;
        }

        // Header row.
        int headerRowIdx = r;
        Row header = ss.createRow(r++);
        header.setHeightInPoints(18);
        for (int i = 0; i < cols.size(); i++) {
            Cell c = header.createCell(i);
            c.setCellValue(cols.get(i).header());
            c.setCellStyle(styles.header);
        }

        // Data rows (banded).
        int idx = 0;
        for (T row : sheet.rows()) {
            Row dr = ss.createRow(r++);
            boolean band = (idx++ & 1) == 1;
            for (int i = 0; i < cols.size(); i++) {
                Cell c = dr.createCell(i);
                styles.apply(c, cols.get(i).value().apply(row), band);
            }
        }

        // Freeze the header, add a filter, and size the columns.
        ss.createFreezePane(0, headerRowIdx + 1);
        if (!cols.isEmpty()) {
            ss.setAutoFilter(new CellRangeAddress(headerRowIdx, headerRowIdx, 0, lastCol));
        }
        for (int i = 0; i < cols.size(); i++) {
            ss.autoSizeColumn(i);
            ss.setColumnWidth(i, Math.min(ss.getColumnWidth(i) + 512, MAX_COL_WIDTH));
        }
    }

    /** Per-workbook style palette + a small cache of body styles keyed by (banded, kind). */
    private static final class Styles {

        private enum Kind { TEXT, NUMBER, DATE, DATETIME }

        private final SXSSFWorkbook wb;
        private final CellStyle header;
        private final CellStyle title;
        private final Map<String, CellStyle> body = new HashMap<>();

        Styles(SXSSFWorkbook wb) {
            this.wb = wb;
            this.header = buildHeader();
            this.title = buildTitle();
        }

        void apply(Cell c, Object v, boolean band) {
            if (v == null) {
                c.setCellStyle(body(band, Kind.TEXT));
            } else if (v instanceof Number n) {
                c.setCellValue(n.doubleValue());
                c.setCellStyle(body(band, Kind.NUMBER));
            } else if (v instanceof Boolean b) {
                c.setCellValue(b);
                c.setCellStyle(body(band, Kind.TEXT));
            } else if (v instanceof Instant t) {
                c.setCellValue(Date.from(t));
                c.setCellStyle(body(band, Kind.DATETIME));
            } else if (v instanceof LocalDateTime t) {
                c.setCellValue(t);
                c.setCellStyle(body(band, Kind.DATETIME));
            } else if (v instanceof LocalDate d) {
                c.setCellValue(d);
                c.setCellStyle(body(band, Kind.DATE));
            } else if (v instanceof Date d) {
                c.setCellValue(d);
                c.setCellStyle(body(band, Kind.DATETIME));
            } else if (v instanceof Enum<?> e) {
                c.setCellValue(e.name());
                c.setCellStyle(body(band, Kind.TEXT));
            } else {
                c.setCellValue(v.toString());
                c.setCellStyle(body(band, Kind.TEXT));
            }
        }

        private CellStyle body(boolean band, Kind kind) {
            return body.computeIfAbsent(band + ":" + kind, k -> buildBody(band, kind));
        }

        private CellStyle buildBody(boolean band, Kind kind) {
            XSSFCellStyle st = (XSSFCellStyle) wb.createCellStyle();
            thinBorders(st);
            st.setVerticalAlignment(VerticalAlignment.CENTER);
            if (band) {
                st.setFillForegroundColor(new XSSFColor(BAND_RGB, null));
                st.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            String fmt = switch (kind) {
                case DATE -> "yyyy-mm-dd";
                case DATETIME -> "yyyy-mm-dd hh:mm";
                default -> null;
            };
            if (fmt != null) {
                st.setDataFormat(wb.createDataFormat().getFormat(fmt));
            }
            return st;
        }

        private CellStyle buildHeader() {
            XSSFCellStyle st = (XSSFCellStyle) wb.createCellStyle();
            thinBorders(st);
            st.setFillForegroundColor(new XSSFColor(HEADER_RGB, null));
            st.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            st.setAlignment(HorizontalAlignment.CENTER);
            st.setVerticalAlignment(VerticalAlignment.CENTER);
            XSSFFont f = (XSSFFont) wb.createFont();
            f.setBold(true);
            f.setColor(IndexedColors.WHITE.getIndex());
            st.setFont(f);
            return st;
        }

        private CellStyle buildTitle() {
            CellStyle st = wb.createCellStyle();
            st.setVerticalAlignment(VerticalAlignment.CENTER);
            XSSFFont f = (XSSFFont) wb.createFont();
            f.setBold(true);
            f.setFontHeightInPoints((short) 14);
            f.setColor(new XSSFColor(HEADER_RGB, null));
            ((XSSFCellStyle) st).setFont(f);
            return st;
        }

        private void thinBorders(CellStyle st) {
            st.setBorderTop(BorderStyle.THIN);
            st.setBorderBottom(BorderStyle.THIN);
            st.setBorderLeft(BorderStyle.THIN);
            st.setBorderRight(BorderStyle.THIN);
            short grey = IndexedColors.GREY_25_PERCENT.getIndex();
            st.setTopBorderColor(grey);
            st.setBottomBorderColor(grey);
            st.setLeftBorderColor(grey);
            st.setRightBorderColor(grey);
        }
    }
}
