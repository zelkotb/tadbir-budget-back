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
package ma.zakaria.tadbirbudget.files.pdf;

import lombok.extern.slf4j.Slf4j;
import ma.zakaria.tadbirbudget.exception.CustomException;
import ma.zakaria.tadbirbudget.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.OutputStream;

/**
 * Generic HTML/CSS → PDF renderer (Flying Saucer). Feature modules build a page as an
 * <b>XHTML string</b> (typically from a Thymeleaf template) and this service turns it into a PDF.
 * Reusable for any document (the requête PDF today, decisions/convocations later).
 *
 * <p>Input must be well-formed XHTML (close every tag, quote attributes). Images can be embedded
 * inline as {@code data:} URIs so no external fetch happens at render time. The built-in fonts cover
 * Latin-1 (French accents); embed a font via CSS {@code @font-face} only for glyphs outside it.
 */
@Slf4j
@Service
public class HtmlPdfService {

    public static final String CONTENT_TYPE = "application/pdf";

    /** Render a well-formed XHTML document to {@code out} as PDF. Does not close the stream. */
    public void render(String xhtml, OutputStream out) {
        try {
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(xhtml);
            renderer.layout();
            renderer.createPDF(out);
        } catch (Exception ex) {
            log.error("HTML->PDF rendering failed", ex);
            throw new CustomException(ErrorCode.FILE_STORAGE_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
