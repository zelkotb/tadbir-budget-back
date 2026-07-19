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

import lombok.extern.slf4j.Slf4j;
import ma.zakaria.tadbirbudget.exception.CustomException;
import ma.zakaria.tadbirbudget.exception.ErrorCode;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.export.JRPdfExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Generic, reusable JasperReports renderer. Feature modules point this at a {@code .jrxml} template
 * (on the classpath, under {@code reports/}), pass the report parameters and the detail rows, and
 * get a PDF — complementing {@link ma.zakaria.tadbirbudget.files.pdf.HtmlPdfService} (free-form
 * HTML→PDF) with pixel-perfect, band-based report layouts.
 *
 * <p>How it maps to Jasper concepts:
 * <ul>
 *   <li><b>template</b> — a classpath {@code .jrxml} path, e.g. {@code "reports/sample-users.jrxml"}.
 *       Compiled once and cached (compilation is the expensive step).</li>
 *   <li><b>parameters</b> — {@code $P{...}} values (title, filters, logos…); may be empty.</li>
 *   <li><b>rows</b> — the {@code $F{...}} detail data: any collection of beans whose getters match
 *       the template's {@code <field>} names. Pass {@code null}/empty for a parameters-only report
 *       (an empty data source with a single record).</li>
 * </ul>
 *
 * <p>Stateless and thread-safe; register once and inject. Like the other exporters in this module it
 * writes to a caller-owned {@link OutputStream} and never closes it.
 */
@Slf4j
@Service
public class JasperReportService {

    /** MIME type for callers building the HTTP response. */
    public static final String CONTENT_TYPE = "application/pdf";

    /** Compiled templates, keyed by classpath path — compile once, fill many times. */
    private final ConcurrentMap<String, JasperReport> compiledCache = new ConcurrentHashMap<>();

    /**
     * Fill {@code templatePath} with {@code parameters} + {@code rows} and write the PDF to
     * {@code out}. Does not close the stream (the caller owns it).
     */
    public void writePdf(String templatePath, Map<String, Object> parameters,
                         Collection<?> rows, OutputStream out) {
        exportPdf(fill(templatePath, parameters, rows), out);
    }

    /** Convenience: fill {@code templatePath} and return the PDF as a byte array. */
    public byte[] toPdf(String templatePath, Map<String, Object> parameters, Collection<?> rows) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        writePdf(templatePath, parameters, rows, bos);
        return bos.toByteArray();
    }

    // ── internals ───────────────────────────────────────────────────────────────

    private JasperPrint fill(String templatePath, Map<String, Object> parameters, Collection<?> rows) {
        JasperReport report = compiledCache.computeIfAbsent(templatePath, this::compile);
        var dataSource = (rows == null || rows.isEmpty())
                ? new JREmptyDataSource()
                : new JRBeanCollectionDataSource(rows);
        Map<String, Object> params = parameters == null ? new HashMap<>() : new HashMap<>(parameters);
        try {
            return JasperFillManager.fillReport(report, params, dataSource);
        } catch (JRException ex) {
            log.error("Jasper fill failed for template [{}]", templatePath, ex);
            throw new CustomException(ErrorCode.FILE_STORAGE_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private JasperReport compile(String templatePath) {
        String path = templatePath.startsWith("/") ? templatePath.substring(1) : templatePath;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                log.error("Jasper template not found on classpath: [{}]", path);
                throw new CustomException(ErrorCode.FILE_NOT_FOUND, HttpStatus.NOT_FOUND);
            }
            return JasperCompileManager.compileReport(in);
        } catch (CustomException ce) {
            throw ce;
        } catch (Exception ex) {
            log.error("Jasper compile failed for template [{}]", path, ex);
            throw new CustomException(ErrorCode.FILE_STORAGE_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void exportPdf(JasperPrint print, OutputStream out) {
        try {
            JRPdfExporter exporter = new JRPdfExporter();
            exporter.setExporterInput(new SimpleExporterInput(print));
            exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));
            exporter.exportReport();
        } catch (JRException ex) {
            log.error("Jasper PDF export failed", ex);
            throw new CustomException(ErrorCode.FILE_STORAGE_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
